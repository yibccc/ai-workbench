package com.aiworkbench.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPropertiesTest {

    @Test
    void detectsMissingCredential() {
        assertThat(new DeepSeekProperties(" ", "https://api.deepseek.com", "deepseek-flash", null)
                        .isConfigured())
                .isFalse();
    }

    @Test
    void detectsConfiguredCredential() {
        assertThat(new DeepSeekProperties("secret", "https://api.deepseek.com", "deepseek-flash", null)
                        .isConfigured())
                .isTrue();
    }

    @Test
    void usesBoundedDefaultAndAcceptsPositiveConfiguredTimeout() {
        assertThat(new DeepSeekProperties("secret", "https://api.deepseek.com", "deepseek-flash", null)
                .requestTimeout()).isEqualTo(Duration.ofMinutes(4));
        assertThat(new DeepSeekProperties("secret", "https://api.deepseek.com", "deepseek-flash",
                Duration.ofSeconds(30)).requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
