package com.aiworkbench.report;

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
class ReportPersistenceService {
    private final ReportMapper mapper;

    ReportPersistenceService(ReportMapper mapper) { this.mapper = mapper; }

    @Transactional
    ReportClaim prepare(UUID requestId, String reportType, LocalDate periodStart, LocalDate periodEnd, ZoneId zoneId) {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID previousReportId = null;
        if (reportType.equals("WEEKLY")) {
            mapper.lockWeeklyVersionChain(periodStart);
            previousReportId = mapper.findLatest(reportType, periodStart).map(ReportRow::id).orElse(null);
        }
        boolean owner = mapper.insertReport(id, requestId, reportType, periodStart, periodEnd,
                zoneId.getId(), token, previousReportId) == 1;
        ReportRow row = mapper.findByRequestId(requestId).orElseThrow();
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
                candidates.addAll(mapper.findCandidateRecords(start, end));
                candidates.addAll(mapper.findCandidateTasks(start, end));
            } else {
                Instant nextEnd = periodEnd.plusWeeks(1).atStartOfDay(zoneId).toInstant();
                candidates.addAll(mapper.findWeeklyRecords(start, end));
                candidates.addAll(mapper.findCurrentTasks(start, end));
                candidates.addAll(mapper.findNextWeekTasks(end, nextEnd));
            }
            candidates.forEach(source -> mapper.insertSource(source.id(), id, source));
        }
        return new ReportClaim(row, owner ? token : null, owner);
    }

    @Transactional
    void succeed(UUID id, UUID token, String content, Instant now) {
        if (mapper.markSucceeded(id, token, content, now) != 1) throw new IllegalStateException("日报处理权已失效");
    }

    @Transactional
    void fail(UUID id, UUID token, String message, Instant now) {
        mapper.markFailed(id, token, message, now);
    }

    @Transactional
    ReportRow update(UUID id, String content, long version, Instant now) {
        require(id);
        if (mapper.updateContent(id, content, version, now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "日报已被其他操作修改，请刷新后重试");
        }
        return require(id);
    }

    @Transactional
    ReportRow updateManualAdditions(UUID id, String manualAdditions, long version, Instant now) {
        require(id);
        if (mapper.updateManualAdditions(id, manualAdditions, version, now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "报告已被其他操作修改，请刷新后重试");
        }
        return require(id);
    }

    @Transactional(readOnly = true)
    ReportRow require(UUID id) {
        return mapper.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "日报不存在"));
    }
}
