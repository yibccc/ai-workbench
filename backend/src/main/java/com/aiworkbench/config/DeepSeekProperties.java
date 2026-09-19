package com.aiworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workbench.deepseek")
public record DeepSeekProperties(String apiKey, String baseUrl, String model) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
