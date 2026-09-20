package com.aiworkbench.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiworkbench.config.DeepSeekProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentScopeReportAiGatewayTest {
    private final AgentScopeReportAiGateway gateway = new AgentScopeReportAiGateway(
            new DeepSeekProperties("", "https://api.deepseek.com", "deepseek-chat", Duration.ofSeconds(5)),
            new ObjectMapper());

    @Test void parsesStrictJsonAndMarkdownFence() {
        AiReportResult result = gateway.parseModelText("```json\n{\"sections\":[]}\n```");
        assertThat(result.sections()).isEmpty();
    }

    @Test void rejectsUnknownFieldsAndTrailingTokens() {
        assertThatThrownBy(() -> gateway.parseModelText("{\"sections\":[],\"extra\":true}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.parseModelText("{\"sections\":[]} garbage"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.parseModelText("{\"sections\":[{\"type\":1,\"bullets\":[]}]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsIncompleteOrDecoratedMarkdownFence() {
        assertThatThrownBy(() -> gateway.parseModelText("```json\n{\"sections\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.parseModelText("说明\n```json\n{\"sections\":[]}\n```"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
