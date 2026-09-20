package com.aiworkbench.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record ReportRow(UUID id, UUID requestId, String reportType, LocalDate periodStart, LocalDate periodEnd,
                 ReportStatus status, String content, String errorMessage, String zoneId, Long version,
                 Instant editedAt, Instant createdAt, Instant updatedAt) {}
