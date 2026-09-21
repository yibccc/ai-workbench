package com.aiworkbench.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workbench.report.ai")
public record ReportAiProperties(Duration timeout, Integer maxTokens) {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(6);
    private static final int DEFAULT_MAX_TOKENS = 4096;

    public Duration requestTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    public int responseTokenLimit() {
        return maxTokens == null || maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens;
    }
}
