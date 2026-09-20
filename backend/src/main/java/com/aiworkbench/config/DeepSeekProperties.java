package com.aiworkbench.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workbench.deepseek")
public record DeepSeekProperties(String apiKey, String baseUrl, String model, Duration timeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(4);

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Duration requestTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }
}
