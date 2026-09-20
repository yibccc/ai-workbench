package com.aiworkbench.report;

import java.time.Instant;
import java.util.UUID;

record ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, UUID entityId,
                       String content, UUID projectId, String projectName, String sourceStatus,
                       Instant sourceTime, String snapshot) {}
