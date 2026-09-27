package com.aiworkbench.dto.report;

import com.aiworkbench.enums.ReportSourceRole;
import com.aiworkbench.enums.ReportSourceType;
import com.aiworkbench.enums.ReportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id, UUID requestId, String reportType, LocalDate date, LocalDate periodEnd, ReportStatus status,
        String content, String errorMessage, String zoneId, long version, Instant editedAt,
        UUID previousReportId, String manualAdditions, Instant manualEditedAt,
        Instant createdAt, Instant updatedAt, String errorCode, String errorStage, int sourceCount,
        List<Source> sources) {
    public record Source(UUID id, ReportSourceType type, ReportSourceRole role, UUID entityId, String content,
                         UUID projectId, String projectName, String status, Instant sourceTime,
                         UUID taskId, UUID sessionId, LocalDate businessDate, Long focusMs, Long breakMs,
                         String progress) {}
}
