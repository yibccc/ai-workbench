package com.aiworkbench.status;

import java.time.Instant;
import java.util.Map;

public record WorkbenchStatus(
        String application,
        Instant checkedAt,
        Map<String, ProbeStatus> components,
        Map<String, String> versions) {}
