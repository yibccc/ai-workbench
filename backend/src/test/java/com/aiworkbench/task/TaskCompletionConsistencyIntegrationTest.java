package com.aiworkbench.task;

import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskEventResponse;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.dto.task.TaskVersionRequest;
import com.aiworkbench.dto.task.UpdateCompletionResultRequest;
import com.aiworkbench.dto.task.UpdateTaskRequest;
import com.aiworkbench.enums.TaskDueFilter;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import com.aiworkbench.service.TaskService;
import com.aiworkbench.service.WorkRecordService;
import com.aiworkbench.support.OwnerTestContext;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class TaskCompletionConsistencyIntegrationTest {
    @Autowired TaskService taskService;
    @Autowired WorkRecordService workRecordService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    private Cookie ownerSession;

    @BeforeEach
    void owner() throws Exception {
        OwnerTestContext.ensureAccounts(jdbcTemplate);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        ownerSession = OwnerTestContext.login(mockMvc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        try { return mockMvc.perform(OwnerTestContext.authenticated(request, ownerSession)); }
        finally { OwnerTestContext.use(OwnerTestContext.USER_ID); }
    }

    @AfterEach
    void removeOwnerFixtures() {
        OwnerTestContext.removeBusinessData(jdbcTemplate);
    }

    @BeforeEach
    @AfterEach
    void cleanFixtures() {
        String fixture = "title LIKE '[D4-TEST]%' OR title LIKE '完成序列 %' OR title LIKE '并发完成 %' "
                + "OR title LIKE '补充结果 %' OR title LIKE '删除联动 %' OR title LIKE 'HTTP 完成 %' "
                + "OR title = '真实 HTTP D4 验收'";
        jdbcTemplate.update("DELETE FROM task_events WHERE todo_id IN (SELECT id FROM todo_items WHERE " + fixture + ")");
        jdbcTemplate.update("DELETE FROM work_records WHERE todo_id IN (SELECT id FROM todo_items WHERE " + fixture + ")");
        jdbcTemplate.update("DELETE FROM todo_items WHERE " + fixture);
        jdbcTemplate.update("DELETE FROM work_records WHERE source = 'MANUAL' AND content = '[D4-TEST] 手工记录不受影响'");
    }

    @Test
    void keepsExactlyOneCurrentRecordAcrossCompleteRetryReopenAndCompleteAgain() {
        TaskResponse task = createTask("完成序列");

        TaskResponse completed = taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "第一版结果"));
        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
        TaskResponse retry = taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "重试不覆盖"));
        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
        assertThat(retry.completionRecordId()).isEqualTo(completed.completionRecordId());
        assertThat(retry.completionResult()).isEqualTo("第一版结果");

        TaskResponse reopened = taskService.reopen(task.id(), new TaskVersionRequest(completed.version()));
        assertThat(activeCompletionCount(task.id())).isZero();
        TaskResponse reopenRetry = taskService.reopen(task.id(), new TaskVersionRequest(completed.version()));
        assertThat(reopenRetry.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(activeCompletionCount(task.id())).isZero();
        TaskResponse completedAgain = taskService.complete(task.id(), new CompleteTaskRequest(reopened.version(), "第二版结果"));
        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
        assertThat(completedAgain.completionRecordId()).isNotEqualTo(completed.completionRecordId());

        List<String> results = jdbcTemplate.queryForList(
                "SELECT completion_result FROM work_records WHERE todo_id = ? ORDER BY created_at", String.class, task.id());
        assertThat(results).containsExactly("第一版结果", "第二版结果");
        assertThat(taskService.get(task.id()).completionResult()).isEqualTo("第二版结果");
        assertThat(taskService.events(task.id())).extracting(TaskEventResponse::eventType)
                .containsExactly("COMPLETED", "REOPENED", "COMPLETED");
    }

    @Test
    void concurrentCompletionUsesConditionalUpdateAndDatabaseUniqueness() throws Exception {
        TaskResponse task = createTask("并发完成");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TaskResponse> first = executor.submit(OwnerTestContext.as(OwnerTestContext.USER_ID,
                    () -> completeTogether(task, ready, start, "结果 A")));
            Future<TaskResponse> second = executor.submit(OwnerTestContext.as(OwnerTestContext.USER_ID,
                    () -> completeTogether(task, ready, start, "结果 B")));
            ready.await();
            start.countDown();
            assertThat(first.get().status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(second.get().status()).isEqualTo(TaskStatus.COMPLETED);
        } finally {
            executor.shutdownNow();
        }

        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM task_events WHERE todo_id = ? AND event_type = 'COMPLETED'",
                Integer.class, task.id())).isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondCurrentAutomaticCompletionRecord() {
        TaskResponse task = createTask("唯一约束");
        taskService.complete(task.id(), new CompleteTaskRequest(task.version(), ""));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO work_records (id, user_id, todo_id, content, source, is_active, occurred_at) "
                        + "VALUES (?, ?, ?, ?, 'TASK_COMPLETION', TRUE, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), OwnerTestContext.USER_ID, task.id(), "不允许的重复完成记录"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
    }

    @Test
    void supplementsResultAndKeepsItInHistoryAfterReopen() {
        TaskResponse task = createTask("补充结果");
        TaskResponse completed = taskService.complete(task.id(), new CompleteTaskRequest(task.version(), ""));
        TaskResponse supplemented = taskService.updateCompletionResult(task.id(),
                new UpdateCompletionResultRequest(completed.version(), "已部署并完成回归"));

        assertThat(supplemented.completionResult()).isEqualTo("已部署并完成回归");
        taskService.reopen(task.id(), new TaskVersionRequest(supplemented.version()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completion_result FROM work_records WHERE id = ?", String.class, completed.completionRecordId()))
                .isEqualTo("已部署并完成回归");
        assertThat(taskService.events(task.id())).extracting(TaskEventResponse::result)
                .contains("已部署并完成回归");
    }

    @Test
    void softDeleteInvalidatesOnlyAutomaticRecordAndPreservesQueryableHistory() throws Exception {
        TaskResponse task = createTask("删除联动");
        WorkRecordResponse manual = workRecordService.create(new CreateWorkRecordRequest(
                null, "[D4-TEST] 手工记录不受影响", Instant.now()));
        TaskResponse completed = taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "保留结果"));

        taskService.delete(task.id(), completed.version());

        assertThat(activeCompletionCount(task.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM work_records WHERE id = ?",
                Boolean.class, completed.completionRecordId())).isFalse();
        assertThat(workRecordService.get(manual.id()).content()).isEqualTo("[D4-TEST] 手工记录不受影响");
        assertThat(taskService.list(null, null, false, null, TaskDueFilter.ALL))
                .extracting(TaskResponse::id).doesNotContain(task.id());
        assertThatThrownBy(() -> taskService.get(task.id())).isInstanceOf(ResponseStatusException.class);

        perform(get("/api/tasks/{id}/events", task.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].eventType").value("DELETED"))
                .andExpect(jsonPath("$[1].result").value(""));
    }

    @Test
    void exposesDedicatedHttpCommandsAndKeepsGenericUpdateStatusFree() throws Exception {
        TaskResponse task = createTask("HTTP 完成");
        CompleteTaskRequest request = new CompleteTaskRequest(task.version(), "接口结果");
        perform(post("/api/tasks/{id}/complete", task.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionResult").value("接口结果"));

        perform(post("/api/tasks/{id}/complete", task.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        perform(post("/api/tasks/{id}/reopen", task.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("版本不能为空"));
    }

    @Test
    void rejectsGenericEditingAndDeletionOfAutomaticCompletionRecordsWithProblemDetails() throws Exception {
        TaskResponse task = createTask("自动记录保护");
        TaskResponse completed = taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "受保护"));
        UUID recordId = completed.completionRecordId();

        perform(put("/api/records/{id}", recordId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":null,\"content\":\"绕过待办修改\",\"occurredAt\":\"2026-09-19T00:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("自动完成记录请通过待办操作维护"));
        perform(delete("/api/records/{id}", recordId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("自动完成记录请通过待办操作维护"));

        assertThat(activeCompletionCount(task.id())).isEqualTo(1);
        assertThat(taskService.get(task.id()).completionResult()).isEqualTo("受保护");
    }

    @Test
    void rollsBackTaskStateWhenAutomaticRecordCannotBePersisted() {
        TaskResponse task = createTask("事务回滚");

        assertThatThrownBy(() -> taskService.complete(task.id(),
                new CompleteTaskRequest(task.version(), "x".repeat(4001))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(taskService.get(task.id()).status()).isEqualTo(TaskStatus.PENDING);
        assertThat(activeCompletionCount(task.id())).isZero();
        assertThat(taskService.events(task.id())).isEmpty();
    }

    @Test
    void rejectsStaleVersionsUnlessTheRequestedTargetStateWasAlreadyReached() {
        TaskResponse task = createTask("陈旧版本");
        TaskResponse edited = taskService.update(task.id(), new UpdateTaskRequest(
                null, task.title(), "并发修改", null, TaskPriority.MEDIUM, task.version()));
        assertThatThrownBy(() -> taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("刷新后重试");

        TaskResponse completed = taskService.complete(task.id(), new CompleteTaskRequest(edited.version(), ""));
        TaskResponse supplemented = taskService.updateCompletionResult(task.id(),
                new UpdateCompletionResultRequest(completed.version(), "新版本"));
        assertThatThrownBy(() -> taskService.reopen(task.id(), new TaskVersionRequest(completed.version())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("刷新后重试");
        assertThat(supplemented.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    private TaskResponse completeTogether(TaskResponse task, CountDownLatch ready, CountDownLatch start, String result)
            throws Exception {
        ready.countDown();
        start.await();
        return taskService.complete(task.id(), new CompleteTaskRequest(task.version(), result));
    }

    private TaskResponse createTask(String title) {
        return taskService.create(new CreateTaskRequest(
                null, "[D4-TEST] " + title + " " + UUID.randomUUID(), "", null, null));
    }

    private int activeCompletionCount(UUID taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM work_records WHERE todo_id = ? AND source = 'TASK_COMPLETION' AND is_active",
                Integer.class, taskId);
    }
}
