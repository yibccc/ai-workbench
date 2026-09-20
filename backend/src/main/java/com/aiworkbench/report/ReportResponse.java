package com.aiworkbench.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id, UUID requestId, String reportType, LocalDate date, ReportStatus status,
        String content, String errorMessage, String zoneId, long version, Instant editedAt,
        Instant createdAt, Instant updatedAt, List<Source> sources) {
    public record Source(UUID id, ReportSourceType type, UUID entityId, String content,
                         UUID projectId, String projectName, String status, Instant sourceTime) {}
}
