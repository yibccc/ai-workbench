package com.aiworkbench.ai;

import com.aiworkbench.config.DeepSeekProperties;
import com.aiworkbench.exception.DeepSeekNotConfiguredException;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!e2e & !test")
public class AgentScopeDeepSeekGateway implements DeepSeekGateway {

    private final DeepSeekProperties properties;

    public AgentScopeDeepSeekGateway(DeepSeekProperties properties) {
        this.properties = properties;
    }

    @Override
    public String probe() {
        if (!properties.isConfigured()) {
            throw new DeepSeekNotConfiguredException("DEEPSEEK_API_KEY is not configured");
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .stream(false)
                .formatter(new DeepSeekFormatter())
                .build();

        ChatResponse response = model.stream(
                        List.of(new UserMessage("Reply with exactly: AI_WORKBENCH_OK")),
                        List.of(),
                        null)
                .blockLast(properties.requestTimeout());
        if (response == null) {
            throw new IllegalStateException("DeepSeek returned no response");
        }
        return response.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .reduce("", (left, right) -> left + right)
                .trim();
    }
}
