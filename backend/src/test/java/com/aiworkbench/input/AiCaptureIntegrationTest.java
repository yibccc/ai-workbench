package com.aiworkbench.input;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.dto.input.CreateInputRequest;
import com.aiworkbench.dto.input.InputResponse;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.enums.InputStatus;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.service.InputService;
import com.aiworkbench.service.ProjectService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AiCaptureIntegrationTest {
    @Autowired InputService inputService;
    @Autowired ProjectService projectService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WorkbenchAiGateway gateway;

    @BeforeEach void resetGateway() { reset(gateway); }

    @AfterEach void removeD6FailureFixtures() {
        jdbc.update("DELETE FROM capture_generated_items WHERE input_id IN (SELECT id FROM capture_inputs WHERE client_request_id LIKE 'timeout-%' OR client_request_id LIKE 'invalid-date-%')");
        jdbc.update("DELETE FROM work_records WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE client_request_id LIKE 'timeout-%' OR client_request_id LIKE 'invalid-date-%')");
        jdbc.update("DELETE FROM todo_items WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE client_request_id LIKE 'timeout-%' OR client_request_id LIKE 'invalid-date-%')");
        jdbc.update("DELETE FROM capture_inputs WHERE client_request_id LIKE 'timeout-%' OR client_request_id LIKE 'invalid-date-%'");
    }

    @Test
    void commitsOriginalAndReferenceBeforeCallingModelThenPersistsMixedBatch() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("AI捕获项目-" + UUID.randomUUID()));
        String requestId = "mixed-" + UUID.randomUUID();
        AtomicReference<Instant> reference = new AtomicReference<>();
        doAnswer(invocation -> {
            Instant seen = invocation.getArgument(1);
            reference.set(seen);
            assertThat(jdbc.queryForObject("SELECT status FROM capture_inputs WHERE client_request_id=?", String.class, requestId))
                    .isEqualTo("PROCESSING");
            return new AiCaptureResult(
                    List.of(new AiCaptureResult.RecordItem("完成接口联调", project.name(), seen.minusSeconds(60).toString())),
                    List.of(new AiCaptureResult.TaskItem("补充回归测试", "覆盖失败路径", project.name(), null, "UNKNOWN")));
        }).when(gateway).extract(anyString(), any(Instant.class), any(ZoneId.class), anyList());

        InputResponse response = awaitTerminal(inputService.create(new CreateInputRequest(requestId, "完成接口联调，接下来补充回归测试")).id());

        assertThat(response.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(response.referenceAt()).isEqualTo(reference.get());
        assertThat(response.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.projectId()).isEqualTo(project.id()); assertThat(item.content()).isEqualTo("完成接口联调");
        });
        assertThat(response.tasks()).singleElement().satisfies(item -> {
            assertThat(item.projectId()).isEqualTo(project.id()); assertThat(item.priority()).isEqualTo(TaskPriority.MEDIUM);
        });
    }

    @Test
    void requestIdIsIdempotentButEqualTextWithAnotherRequestCreatesAnotherBatch() {
        Instant occurred = Instant.now().minusSeconds(30);
        when(gateway.extract(anyString(), any(), any(), anyList())).thenReturn(new AiCaptureResult(
                List.of(new AiCaptureResult.RecordItem("同一文本", null, occurred.toString())), List.of()));
        String requestId = "idem-" + UUID.randomUUID();
        InputResponse first = awaitTerminal(inputService.create(new CreateInputRequest(requestId, "同一文本")).id());
        InputResponse repeat = inputService.create(new CreateInputRequest(requestId, "同一请求标识下的新文本不会覆盖原文"));
        InputResponse distinct = awaitTerminal(inputService.create(new CreateInputRequest("idem-" + UUID.randomUUID(), "同一文本")).id());

        assertThat(repeat.id()).isEqualTo(first.id());
        assertThat(repeat.content()).isEqualTo("同一文本");
        assertThat(distinct.id()).isNotEqualTo(first.id());
        verify(gateway, times(2)).extract(anyString(), any(), any(), anyList());
    }

    @Test
    void retryRejectsAnyStatusOtherThanFailed() {
        Instant occurred = Instant.now().minusSeconds(30);
        when(gateway.extract(anyString(), any(), any(), anyList())).thenReturn(new AiCaptureResult(
                List.of(new AiCaptureResult.RecordItem("已经成功", null, occurred.toString())), List.of()));
        InputResponse succeeded = awaitTerminal(inputService.create(
                new CreateInputRequest("retry-state-" + UUID.randomUUID(), "已经成功")).id());

        assertThatThrownBy(() -> inputService.retry(succeeded.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("当前输入不能重试");
        verify(gateway).extract(anyString(), any(), any(), anyList());
    }

    @Test
    void supportsMultipleItemsTaskOnlyAndRejectsAnEmptyExtraction() {
        when(gateway.extract(anyString(), any(), any(), anyList()))
                .thenAnswer(invocation -> new AiCaptureResult(List.of(), List.of(
                            new AiCaptureResult.TaskItem("第一项", "", null, null, null),
                            new AiCaptureResult.TaskItem("第二项", "", null, null, "LOW"))))
                .thenReturn(new AiCaptureResult(List.of(), List.of()));

        InputResponse taskOnly = awaitTerminal(inputService.create(
                new CreateInputRequest("task-only-" + UUID.randomUUID(), "安排两项后续工作")).id());
        InputResponse empty = awaitTerminal(inputService.create(
                new CreateInputRequest("empty-" + UUID.randomUUID(), "没有可提取条目")).id());

        assertThat(taskOnly.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(taskOnly.records()).isEmpty();
        assertThat(taskOnly.tasks()).hasSize(2);
        assertThat(taskOnly.tasks()).allSatisfy(item -> assertThat(item.dueAt()).isNull());
        assertThat(taskOnly.tasks()).extracting(InputResponse.GeneratedTask::priority)
                .containsExactlyInAnyOrder(TaskPriority.MEDIUM, TaskPriority.LOW);
        assertThat(empty.status()).isEqualTo(InputStatus.FAILED);
        assertThat(empty.records()).isEmpty();
        assertThat(empty.tasks()).isEmpty();
    }

    @Test
    void invalidBatchWritesNothingAndRetryReusesOriginalReference() {
        AtomicReference<Instant> firstReference = new AtomicReference<>();
        when(gateway.extract(anyString(), any(), any(), anyList()))
                .thenAnswer(invocation -> {
                    firstReference.set(invocation.getArgument(1));
                    return new AiCaptureResult(List.of(
                            new AiCaptureResult.RecordItem("有效项", null, firstReference.get().minusSeconds(5).toString()),
                            new AiCaptureResult.RecordItem("", null, firstReference.get().toString())), List.of());
                })
                .thenAnswer(invocation -> {
                    assertThat((Instant) invocation.getArgument(1)).isEqualTo(firstReference.get());
                    return new AiCaptureResult(List.of(new AiCaptureResult.RecordItem(
                            "重试成功", "不存在的项目", firstReference.get().minusSeconds(5).toString())), List.of());
                });
        InputResponse failed = awaitTerminal(inputService.create(new CreateInputRequest("retry-" + UUID.randomUUID(), "先失败再重试")).id());
        assertThat(failed.status()).isEqualTo(InputStatus.FAILED);
        assertThat(failed.records()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE capture_input_id=?", Integer.class, failed.id())).isZero();

        inputService.retry(failed.id());
        InputResponse retried = awaitTerminal(failed.id());
        assertThat(retried.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(retried.referenceAt()).isEqualTo(firstReference.get());
        assertThat(retried.attemptCount()).isEqualTo(2);
        assertThat(retried.records()).singleElement().satisfies(item -> assertThat(item.projectId()).isNull());
    }

    @Test
    void databaseFailureDuringSecondItemRollsBackTheWholeBatchAndKeepsOriginal() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("待删除项目-" + UUID.randomUUID()));
        String requestId = "atomic-" + UUID.randomUUID();
        when(gateway.extract(anyString(), any(), any(), anyList())).thenAnswer(invocation -> {
            jdbc.update("DELETE FROM projects WHERE id=?", project.id());
            Instant referenceAt = invocation.getArgument(1);
            return new AiCaptureResult(
                    List.of(new AiCaptureResult.RecordItem("先写入但应回滚", null, referenceAt.toString())),
                    List.of(new AiCaptureResult.TaskItem("触发外键失败", "", project.name(), null, "MEDIUM")));
        });

        InputResponse failed = awaitTerminal(inputService.create(
                new CreateInputRequest(requestId, "验证整批事务回滚")).id());

        assertThat(failed.status()).isEqualTo(InputStatus.FAILED);
        assertThat(failed.content()).isEqualTo("验证整批事务回滚");
        assertThat(failed.records()).isEmpty();
        assertThat(failed.tasks()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE capture_input_id=?", Integer.class, failed.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE capture_input_id=?", Integer.class, failed.id())).isZero();
    }

    @Test
    void timeoutAndInvalidDateRemainRetryableWithoutBusinessWrites() {
        when(gateway.extract(anyString(), any(), any(), anyList()))
                .thenThrow(new RuntimeException("upstream timeout with private details"))
                .thenReturn(new AiCaptureResult(
                        List.of(new AiCaptureResult.RecordItem("日期无效", null, "tomorrow")), List.of()));

        InputResponse timeout = awaitTerminal(inputService.create(
                new CreateInputRequest("timeout-" + UUID.randomUUID(), "超时也要保留的原文")).id());
        InputResponse invalidDate = awaitTerminal(inputService.create(
                new CreateInputRequest("invalid-date-" + UUID.randomUUID(), "非法日期也要保留的原文")).id());

        assertThat(timeout.status()).isEqualTo(InputStatus.FAILED);
        assertThat(timeout.content()).isEqualTo("超时也要保留的原文");
        assertThat(timeout.errorMessage()).doesNotContain("private details");
        assertThat(invalidDate.status()).isEqualTo(InputStatus.FAILED);
        assertThat(invalidDate.content()).isEqualTo("非法日期也要保留的原文");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM work_records WHERE capture_input_id IN (?, ?)", Integer.class,
                timeout.id(), invalidDate.id())).isZero();
    }

    private InputResponse awaitTerminal(UUID id) {
        for (int attempt = 0; attempt < 100; attempt++) {
            InputResponse current = inputService.get(id);
            if (current.status() != InputStatus.PROCESSING) return current;
            try { Thread.sleep(25); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
        }
        throw new AssertionError("AI capture did not finish");
    }
}
