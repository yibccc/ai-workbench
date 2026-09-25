package com.aiworkbench.service.impl;

import com.aiworkbench.dto.report.ReportClaim;
import com.aiworkbench.entity.report.ReportRow;
import com.aiworkbench.entity.report.ReportSourceRow;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ReportPersistenceService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportPersistenceServiceImpl implements ReportPersistenceService {
    private final ReportMapper mapper;
    private final WorkbenchEventHub events;

    public ReportPersistenceServiceImpl(ReportMapper mapper, WorkbenchEventHub events) {
        this.mapper = mapper; this.events = events;
    }

    @Transactional
    public ReportClaim prepare(UUID requestId, String reportType, LocalDate periodStart, LocalDate periodEnd, ZoneId zoneId) {
        UUID userId = CurrentUser.requireId();
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID previousReportId = null;
        if (reportType.equals("WEEKLY")) {
            mapper.lockWeeklyVersionChain(userId, periodStart);
            previousReportId = mapper.findLatest(userId, reportType, periodStart).map(ReportRow::id).orElse(null);
        }
        boolean owner = mapper.insertReport(userId, id, requestId, reportType, periodStart, periodEnd,
                zoneId.getId(), token, previousReportId) == 1;
        ReportRow row = mapper.findByRequestId(userId, requestId).orElseThrow();
        if (mapper.isDeleted(userId, row.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求对应的日报已删除，请发起新的生成请求");
        }
        if (!row.periodStart().equals(periodStart) || !row.periodEnd().equals(periodEnd)
                || !row.reportType().equals(reportType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId 已用于其他报告请求");
        }
        if (owner) {
            Instant start = periodStart.atStartOfDay(zoneId).toInstant();
            Instant end = (reportType.equals("DAILY") ? periodStart.plusDays(1) : periodEnd)
                    .atStartOfDay(zoneId).toInstant();
            List<ReportSourceRow> candidates = new java.util.ArrayList<>();
            if (reportType.equals("DAILY")) {
                candidates.addAll(mapper.findCandidateRecords(userId, start, end));
                candidates.addAll(mapper.findCandidateTasks(userId, start, end));
            } else {
                Instant nextEnd = periodEnd.plusWeeks(1).atStartOfDay(zoneId).toInstant();
                candidates.addAll(mapper.findWeeklyRecords(userId, start, end));
                candidates.addAll(mapper.findCurrentTasks(userId, start, end));
                candidates.addAll(mapper.findNextWeekTasks(userId, end, nextEnd));
            }
            candidates.forEach(source -> mapper.insertSource(userId, source.id(), id, source));
            mapper.updateSourceCount(userId, id, candidates.size());
        }
        if (owner) events.publishAfterCommit(row.userId(), "REPORT", row.id(), "PROCESSING");
        return new ReportClaim(row, owner ? token : null, owner);
    }

    @Transactional
    public void succeed(UUID id, UUID token, String content, Instant now) {
        ReportRow report = requirePersisted(id);
        if (mapper.markSucceeded(report.userId(), id, token, content, now) != 1) throw new IllegalStateException("日报处理权已失效");
        events.publishAfterCommit(report.userId(), "REPORT", id, "SUCCEEDED");
    }

    @Transactional
    public void fail(UUID id, UUID token, String message, String errorCode, String errorStage,
              int sourceCount, Instant now) {
        ReportRow report = requirePersisted(id);
        if (mapper.markFailed(report.userId(), id, token, message, errorCode, errorStage, sourceCount, now) == 1) {
            events.publishAfterCommit(report.userId(), "REPORT", id, "FAILED");
        }
    }

    @Transactional
    public ReportRow update(UUID id, String content, long version, Instant now) {
        ReportRow report = require(id);
        if (mapper.updateContent(report.userId(), id, content, version, now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "日报已被其他操作修改，请刷新后重试");
        }
        events.publishAfterCommit(report.userId(), "REPORT", id, "UPDATED");
        return require(id);
    }

    @Transactional
    public ReportRow updateManualAdditions(UUID id, String manualAdditions, long version, Instant now) {
        ReportRow report = require(id);
        if (mapper.updateManualAdditions(report.userId(), id, manualAdditions, version, now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "报告已被其他操作修改，请刷新后重试");
        }
        events.publishAfterCommit(report.userId(), "REPORT", id, "UPDATED");
        return require(id);
    }

    @Transactional
    public void delete(UUID id, long version, Instant now) {
        UUID userId = CurrentUser.requireId();
        ReportRow row = mapper.lockById(userId, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "日报不存在"));
        if (!row.reportType().equals("DAILY")) throw new ResponseStatusException(HttpStatus.CONFLICT, "暂不支持删除周报版本");
        // A repeated deletion is successful regardless of its now-stale version.
        if (mapper.isDeleted(userId, id)) return;
        if (row.status() == com.aiworkbench.enums.ReportStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "生成中的日报不能删除，请等待生成结束");
        }
        if (mapper.softDelete(userId, id, version, now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "日报已被其他操作修改，请刷新后重试");
        }
        events.publishAfterCommit(userId, "REPORT", id, "DELETED");
    }

    @Transactional(readOnly = true)
    public ReportRow require(UUID id) {
        return requireOwned(CurrentUser.requireId(), id);
    }

    @Transactional(readOnly = true)
    public ReportRow requireOwned(UUID userId, UUID id) {
        return mapper.findById(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "日报不存在"));
    }

    private ReportRow requirePersisted(UUID id) {
        return mapper.findPersistedById(id).orElseThrow(() -> new IllegalStateException("持久报告不存在"));
    }
}
