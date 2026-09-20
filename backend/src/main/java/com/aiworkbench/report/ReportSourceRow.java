package com.aiworkbench.report;

import java.time.Instant;
import java.util.UUID;

record ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, ReportSourceRole sourceRole, UUID entityId,
                       String content, UUID projectId, String projectName, String sourceStatus,
                       Instant sourceTime, String snapshot) {
    ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, UUID entityId,
                    String content, UUID projectId, String projectName, String sourceStatus,
                    Instant sourceTime, String snapshot) {
        this(id, reportId, sourceType,
                sourceType == ReportSourceType.RECORD ? ReportSourceRole.DAILY_RECORD : ReportSourceRole.DAILY_TASK,
                entityId, content, projectId, projectName, sourceStatus, sourceTime, snapshot);
    }
}
