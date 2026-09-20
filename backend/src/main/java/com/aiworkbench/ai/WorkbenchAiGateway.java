package com.aiworkbench.ai;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public interface WorkbenchAiGateway {
    AiCaptureResult extract(String rawContent, Instant referenceAt, ZoneId zoneId, List<String> activeProjectNames);
}
