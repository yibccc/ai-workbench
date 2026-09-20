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
    ReportClaim prepare(UUID requestId, LocalDate date, ZoneId zoneId) {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        boolean owner = mapper.insertReport(id, requestId, date, zoneId.getId(), token) == 1;
        ReportRow row = mapper.findByRequestId(requestId).orElseThrow();
        if (!row.periodStart().equals(date) || !row.reportType().equals("DAILY")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId 已用于其他报告请求");
        }
        if (owner) {
            Instant start = date.atStartOfDay(zoneId).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(zoneId).toInstant();
            List<ReportSourceRow> candidates = new java.util.ArrayList<>(mapper.findCandidateRecords(start, end));
            candidates.addAll(mapper.findCandidateTasks(start, end));
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

    @Transactional(readOnly = true)
    ReportRow require(UUID id) {
        return mapper.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "日报不存在"));
    }
}
