package com.aiworkbench.service;

import com.aiworkbench.dto.report.ReportClaim;
import com.aiworkbench.entity.report.ReportRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public interface ReportPersistenceService {
    void delete(UUID id, long version, Instant now);
    ReportClaim prepare(UUID requestId, String reportType, LocalDate periodStart, LocalDate periodEnd, ZoneId zoneId);
    void succeed(UUID id, UUID token, String content, Instant now);
    void fail(UUID id, UUID token, String message, String errorCode, String errorStage,
              int sourceCount, Instant now);
    ReportRow update(UUID id, String content, long version, Instant now);
    ReportRow updateManualAdditions(UUID id, String manualAdditions, long version, Instant now);
    ReportRow require(UUID id);
    ReportRow requireOwned(UUID userId, UUID id);
}
