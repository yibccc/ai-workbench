package com.aiworkbench.entity.report;

import com.aiworkbench.enums.ReportSourceRole;
import com.aiworkbench.enums.ReportSourceType;
import java.time.Instant;
import java.util.UUID;

public record ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, ReportSourceRole sourceRole, UUID entityId,
                       String content, UUID projectId, String projectName, String sourceStatus,
                       Instant sourceTime, String snapshot) {
    public ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, UUID entityId,
                    String content, UUID projectId, String projectName, String sourceStatus,
                    Instant sourceTime, String snapshot) {
        this(id, reportId, sourceType,
                sourceType == ReportSourceType.RECORD ? ReportSourceRole.DAILY_RECORD : ReportSourceRole.DAILY_TASK,
                entityId, content, projectId, projectName, sourceStatus, sourceTime, snapshot);
    }
}
