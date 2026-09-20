package com.aiworkbench.report;

import java.time.Instant;
import java.util.UUID;

public record ReportSourcePrompt(UUID id, ReportSourceType type, String content, String projectName,
                                 String status, Instant sourceTime) {}
