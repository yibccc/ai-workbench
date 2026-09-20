package com.aiworkbench.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.project.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class InputExecutorFailureTest {
    @Test
    void rejectedExecutionReturnsInputAndMarksTheOwnedAttemptRetryable() {
        InputMapper mapper = mock(InputMapper.class);
        InputPersistenceService persistence = mock(InputPersistenceService.class);
        WorkbenchAiGateway gateway = mock(WorkbenchAiGateway.class);
        ProjectService projectService = mock(ProjectService.class);
        TaskExecutor executor = task -> { throw new IllegalStateException("executor rejected"); };
        InputService service = new InputService(
                mapper, persistence, gateway, projectService, executor, "Asia/Shanghai");
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        Instant now = Instant.now();
        InputRow row = new InputRow(id, "request", "原文", now, "Asia/Shanghai", InputStatus.PROCESSING,
                null, 1, null, now, now, true);
        when(persistence.createOrGet(eq("request"), eq("原文"), any(Instant.class), any()))
                .thenReturn(new ProcessingClaim(row, token, true));
        when(persistence.require(id)).thenReturn(row);
        when(mapper.findRecords(id)).thenReturn(List.of());
        when(mapper.findTasks(id)).thenReturn(List.of());

        InputResponse response = service.create(new CreateInputRequest("request", "原文"));

        assertThat(response.id()).isEqualTo(id);
        verify(persistence).fail(eq(id), eq(token), contains("暂时不可用"), any(Instant.class));
    }
}
