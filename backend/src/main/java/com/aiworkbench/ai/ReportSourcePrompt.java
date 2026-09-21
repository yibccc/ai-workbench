package com.aiworkbench.ai;

import com.aiworkbench.enums.ReportSourceRole;
import com.aiworkbench.enums.ReportSourceType;
import java.time.Instant;
import java.util.UUID;

public record ReportSourcePrompt(UUID id, ReportSourceType type, ReportSourceRole role, String content,
                                 String projectName, String status, Instant sourceTime) {
    public ReportSourcePrompt(UUID id, ReportSourceType type, String content, String projectName,
                              String status, Instant sourceTime) {
        this(id, type, type == ReportSourceType.RECORD ? ReportSourceRole.DAILY_RECORD : ReportSourceRole.DAILY_TASK,
                content, projectName, status, sourceTime);
    }
}
