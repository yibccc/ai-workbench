package com.aiworkbench.entity.report;

import com.aiworkbench.enums.ReportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportRow(UUID id, UUID userId, UUID requestId, String reportType, LocalDate periodStart, LocalDate periodEnd,
                 ReportStatus status, String content, String errorMessage, String zoneId, Long version,
                 Instant editedAt, UUID previousReportId, String manualAdditions, Instant manualEditedAt,
                 Instant createdAt, Instant updatedAt, String errorCode, String errorStage, Integer sourceCount) {}
