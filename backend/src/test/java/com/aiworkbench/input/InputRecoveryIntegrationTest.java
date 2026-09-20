package com.aiworkbench.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doAnswer;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.record.UpdateWorkRecordRequest;
import com.aiworkbench.record.WorkRecordService;
import com.aiworkbench.task.CompleteTaskRequest;
import com.aiworkbench.task.TaskPriority;
import com.aiworkbench.task.TaskService;
import com.aiworkbench.task.TaskVersionRequest;
import com.aiworkbench.task.UpdateTaskRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class InputRecoveryIntegrationTest {
    @Autowired InputService inputService;
    @Autowired InputPersistenceService persistence;
    @Autowired WorkRecordService workRecordService;
    @Autowired TaskService taskService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WorkbenchAiGateway gateway;

    @BeforeEach
    void resetGateway() {
        reset(gateway);
    }

    @AfterEach
    void removeRecoveryFixtures() {
        String predicate = "client_request_id LIKE 'claim-%' OR client_request_id LIKE 'recovery-%' "
                + "OR client_request_id LIKE 'revert-ok-%' OR client_request_id LIKE 'revert-edit-%' "
                + "OR client_request_id LIKE 'revert-delete-%' OR client_request_id LIKE 'revert-complete-%' "
                + "OR client_request_id LIKE 'revert-task-%' OR client_request_id LIKE 'fence-%'";
        jdbc.update("DELETE FROM task_events WHERE todo_id IN (SELECT id FROM todo_items WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE " + predicate + "))");
        jdbc.update("DELETE FROM work_records WHERE todo_id IN (SELECT id FROM todo_items WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE " + predicate + "))");
        jdbc.update("DELETE FROM capture_generated_items WHERE input_id IN (SELECT id FROM capture_inputs WHERE " + predicate + ")");
        jdbc.update("DELETE FROM work_records WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE " + predicate + ")");
        jdbc.update("DELETE FROM todo_items WHERE capture_input_id IN (SELECT id FROM capture_inputs WHERE " + predicate + ")");
        jdbc.update("DELETE FROM capture_inputs WHERE " + predicate);
    }

    @Test
    void concurrentCreateAndRetryClaimsHaveExactlyOneDatabaseOwner() throws Exception {
        String requestId = "claim-" + UUID.randomUUID();
        Instant referenceAt = Instant.now();
        List<ProcessingClaim> creates = race(() -> persistence.createOrGet(
                requestId, "并发原文", referenceAt, ZoneId.of("Asia/Shanghai")));

        assertThat(creates).filteredOn(ProcessingClaim::owner).hasSize(1);
        assertThat(creates).extracting(claim -> claim.row().id()).containsOnly(creates.get(0).row().id());
        ProcessingClaim owner = creates.stream().filter(ProcessingClaim::owner).findFirst().orElseThrow();
        persistence.fail(owner.row().id(), owner.token(), "可重试", Instant.now());

        List<Object> retries = raceAllowingConflict(() -> persistence.beginRetry(owner.row().id(), Instant.now()));
        assertThat(retries).filteredOn(ProcessingClaim.class::isInstance)
                .map(ProcessingClaim.class::cast).filteredOn(ProcessingClaim::owner).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempt_count FROM capture_inputs WHERE id=?", Integer.class, owner.row().id())).isEqualTo(2);
    }

    @Test
    void recoveryOnlyFailsExpiredLeaseAndPreservesOriginalBaseline() {
        Instant referenceAt = Instant.parse("2026-09-19T01:02:03Z");
        UUID expired = insertProcessing("expired", referenceAt, referenceAt.minusSeconds(1));
        UUID valid = insertProcessing("valid", referenceAt, Instant.now().plusSeconds(300));

        assertThat(persistence.recoverExpiredProcessing(Instant.now())).isEqualTo(1);

        InputResponse recovered = inputService.get(expired);
        assertThat(recovered.status()).isEqualTo(InputStatus.FAILED);
        assertThat(recovered.content()).isEqualTo("expired");
        assertThat(recovered.referenceAt()).isEqualTo(referenceAt);
        assertThat(recovered.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(inputService.get(valid).status()).isEqualTo(InputStatus.PROCESSING);
    }

    @Test
    void recoveryAlsoFailsLegacyProcessingWithoutLease() {
        Instant referenceAt = Instant.parse("2026-09-19T01:02:03Z");
        UUID legacy = insertProcessing("legacy-no-lease", referenceAt, null);

        assertThat(persistence.recoverExpiredProcessing(Instant.now())).isEqualTo(1);
        assertThat(inputService.get(legacy).status()).isEqualTo(InputStatus.FAILED);
    }

    @Test
    void expiredOwnerCannotCommitOrFailAfterAnotherRetryOwnsTheInput() {
        Instant startedAt = Instant.parse("2026-09-19T01:02:03Z");
        ProcessingClaim oldClaim = persistence.createOrGet(
                "fence-" + UUID.randomUUID(), "围栏原文", startedAt, ZoneId.of("Asia/Shanghai"));
        Instant afterLease = startedAt.plusSeconds(301);
        assertThat(persistence.recoverExpiredProcessing(afterLease)).isEqualTo(1);
        ProcessingClaim newClaim = persistence.beginRetry(oldClaim.row().id(), afterLease);
        PreparedCapture oldBatch = new PreparedCapture(
                List.of(new PreparedCapture.RecordItem(null, "旧 owner 记录", startedAt)), List.of());

        assertThatThrownBy(() -> persistence.succeed(
                oldClaim.row().id(), oldClaim.token(), oldBatch, afterLease))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理权已失效");
        persistence.fail(oldClaim.row().id(), oldClaim.token(), "旧 owner 失败", afterLease);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM work_records WHERE capture_input_id=?", Integer.class,
                oldClaim.row().id())).isZero();
        assertThat(inputService.get(oldClaim.row().id()).status()).isEqualTo(InputStatus.PROCESSING);

        PreparedCapture newBatch = new PreparedCapture(List.of(), List.of(new PreparedCapture.TaskItem(
                null, "新 owner 待办", "", null, TaskPriority.MEDIUM)));
        persistence.succeed(newClaim.row().id(), newClaim.token(), newBatch, afterLease.plusSeconds(1));
        InputResponse response = inputService.get(oldClaim.row().id());
        assertThat(response.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(response.records()).isEmpty();
        assertThat(response.tasks()).hasSize(1);
    }

    @Test
    void revertsAnUnchangedBatchAtomicallyAndRepeatsIdempotently() {
        InputResponse succeeded = createMixedSuccess("revert-ok-");

        InputResponse reverted = inputService.revert(succeeded.id());
        InputResponse repeated = inputService.revert(succeeded.id());

        assertThat(reverted.status()).isEqualTo(InputStatus.REVERTED);
        assertThat(repeated.status()).isEqualTo(InputStatus.REVERTED);
        assertThat(repeated.content()).isEqualTo(succeeded.content());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM work_records WHERE capture_input_id=? AND is_active", Integer.class, succeeded.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM todo_items WHERE capture_input_id=? AND deleted_at IS NULL", Integer.class, succeeded.id())).isZero();
        assertThat(reverted.records()).hasSameSizeAs(succeeded.records());
        assertThat(reverted.tasks()).hasSameSizeAs(succeeded.tasks());
    }

    @Test
    void editedOrDeletedRecordBlocksRevertWithoutPartialChanges() {
        InputResponse edited = createMixedSuccess("revert-edit-");
        var record = edited.records().get(0);
        workRecordService.update(record.id(), new UpdateWorkRecordRequest(
                record.projectId(), "用户后续编辑", record.occurredAt()));

        assertConflict(() -> inputService.revert(edited.id()));
        assertBatchStillSucceeded(edited.id());

        InputResponse deleted = createMixedSuccess("revert-delete-");
        workRecordService.delete(deleted.records().get(0).id());
        assertConflict(() -> inputService.revert(deleted.id()));
        assertBatchStillSucceeded(deleted.id());
    }

    @Test
    void completedTaskBlocksRevertAndKeepsCompletionHistory() {
        InputResponse succeeded = createMixedSuccess("revert-complete-");
        var task = succeeded.tasks().get(0);
        taskService.complete(task.id(), new CompleteTaskRequest(task.version(), "已交付"));

        assertConflict(() -> inputService.revert(succeeded.id()));

        assertBatchStillSucceeded(succeeded.id());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM todo_items WHERE id=?", String.class, task.id())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM task_events WHERE todo_id=? AND event_type='COMPLETED'", Integer.class, task.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM work_records WHERE todo_id=? AND is_active", Integer.class, task.id())).isEqualTo(1);
    }

    @Test
    void taskEditDeleteAndReopenEachBlockBatchRevert() {
        InputResponse edited = createMixedSuccess("revert-task-edit-");
        var editedTask = edited.tasks().get(0);
        taskService.update(editedTask.id(), new UpdateTaskRequest(
                editedTask.projectId(), "用户编辑待办", editedTask.notes(), editedTask.dueAt(),
                editedTask.priority(), editedTask.version()));
        assertConflict(() -> inputService.revert(edited.id()));
        assertBatchStillSucceeded(edited.id());

        InputResponse deleted = createMixedSuccess("revert-task-delete-");
        var deletedTask = deleted.tasks().get(0);
        taskService.delete(deletedTask.id(), deletedTask.version());
        assertConflict(() -> inputService.revert(deleted.id()));
        assertThat(inputService.get(deleted.id()).status()).isEqualTo(InputStatus.SUCCEEDED);

        InputResponse reopened = createMixedSuccess("revert-task-reopen-");
        var reopenedTask = reopened.tasks().get(0);
        var completed = taskService.complete(
                reopenedTask.id(), new CompleteTaskRequest(reopenedTask.version(), "完成后重开"));
        taskService.reopen(completed.id(), new TaskVersionRequest(completed.version()));
        assertConflict(() -> inputService.revert(reopened.id()));
        assertBatchStillSucceeded(reopened.id());
    }

    private InputResponse createMixedSuccess(String prefix) {
        doAnswer(invocation -> {
            Instant referenceAt = invocation.getArgument(1);
            return new AiCaptureResult(
                    List.of(new AiCaptureResult.RecordItem("生成记录", null, referenceAt.toString())),
                    List.of(new AiCaptureResult.TaskItem("生成待办", "", null, null, "MEDIUM")));
        }).when(gateway).extract(anyString(), any(), any(), anyList());
        InputResponse created = inputService.create(new CreateInputRequest(prefix + UUID.randomUUID(), "保留的原文"));
        return awaitTerminal(created.id());
    }

    private UUID insertProcessing(String content, Instant referenceAt, Instant leaseExpiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO capture_inputs
                    (id, content, source, captured_at, client_request_id, reference_at, zone_id, status,
                     processing_token, lease_expires_at)
                VALUES (?, ?, 'AI', ?, ?, ?, 'Asia/Shanghai', 'PROCESSING', ?, ?)
                """, id, content, Timestamp.from(referenceAt), "recovery-" + id, Timestamp.from(referenceAt),
                UUID.randomUUID(), leaseExpiresAt == null ? null : Timestamp.from(leaseExpiresAt));
        return id;
    }

    private void assertBatchStillSucceeded(UUID inputId) {
        assertThat(inputService.get(inputId).status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM todo_items WHERE capture_input_id=? AND deleted_at IS NULL", Integer.class, inputId)).isEqualTo(1);
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能撤销");
    }

    private InputResponse awaitTerminal(UUID id) {
        for (int attempt = 0; attempt < 100; attempt++) {
            InputResponse current = inputService.get(id);
            if (current.status() != InputStatus.PROCESSING) return current;
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("AI capture did not finish");
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<T> participant = () -> {
                ready.countDown();
                start.await();
                return action.call();
            };
            Future<T> first = executor.submit(participant);
            Future<T> second = executor.submit(participant);
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Object> raceAllowingConflict(Callable<ProcessingClaim> action) throws Exception {
        Callable<Object> tolerant = () -> {
            try {
                return action.call();
            } catch (ResponseStatusException exception) {
                return exception;
            }
        };
        return race(tolerant);
    }
}
