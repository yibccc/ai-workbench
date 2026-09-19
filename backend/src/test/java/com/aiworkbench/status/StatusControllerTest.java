package com.aiworkbench.status;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiworkbench.ai.DeepSeekGateway;
import com.aiworkbench.ai.DeepSeekNotConfiguredException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private DeepSeekGateway deepSeekGateway;

    @Test
    void returnsStatusContract() throws Exception {
        when(statusService.current()).thenReturn(new WorkbenchStatus(
                "ai-workbench",
                Instant.parse("2026-09-19T00:00:00Z"),
                Map.of("backend", ProbeStatus.up("ok")),
                Map.of("springBoot", "3.5.16")));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("ai-workbench"))
                .andExpect(jsonPath("$.components.backend.status").value("UP"));
    }

    @Test
    void reportsAiProbeAsNotVerifiedWhenCredentialIsMissing() throws Exception {
        when(deepSeekGateway.probe()).thenThrow(new DeepSeekNotConfiguredException("not configured"));

        mockMvc.perform(post("/api/ai/probe"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("NOT_VERIFIED"));
    }

    @Test
    void reportsUnexpectedAiProbeFailureAsDown() throws Exception {
        when(deepSeekGateway.probe()).thenThrow(new IllegalStateException("unexpected model failure"));

        mockMvc.perform(post("/api/ai/probe"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.detail").value("DeepSeek probe failed: IllegalStateException"));
    }
}
