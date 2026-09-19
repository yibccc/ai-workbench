package com.aiworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeepSeekPropertiesTest {

    @Test
    void detectsMissingCredential() {
        assertThat(new DeepSeekProperties(" ", "https://api.deepseek.com", "deepseek-flash")
                        .isConfigured())
                .isFalse();
    }

    @Test
    void detectsConfiguredCredential() {
        assertThat(new DeepSeekProperties("secret", "https://api.deepseek.com", "deepseek-flash")
                        .isConfigured())
                .isTrue();
    }
}
