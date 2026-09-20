package com.aiworkbench.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class DailyReportIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2040, 1, 2);
    @Autowired ReportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired Validator validator;
    @MockitoBean ReportAiGateway gateway;
    private final List<UUID> requestIds = new ArrayList<>();
    private final List<UUID> recordIds = new ArrayList<>();
    private final List<UUID> taskIds = new ArrayList<>();

    @BeforeEach void resetGateway() {
        reset(gateway);
        jdbc.update("UPDATE reports SET previous_report_id=NULL WHERE period_start IN (?,?)", DATE, LocalDate.of(2041, 6, 7));
        jdbc.update("DELETE FROM report_sources WHERE report_id IN (SELECT id FROM reports WHERE period_start IN (?,?))",
                DATE, LocalDate.of(2041, 6, 7));
        jdbc.update("DELETE FROM reports WHERE period_start IN (?,?)", DATE, LocalDate.of(2041, 6, 7));
        jdbc.update("DELETE FROM work_records WHERE occurred_at>=? AND occurred_at<?",
                Timestamp.from(DATE.atStartOfDay(ZONE).toInstant().minusSeconds(1)),
                Timestamp.from(LocalDate.of(2041, 6, 9).atStartOfDay(ZONE).toInstant()));
        jdbc.update("DELETE FROM todo_items WHERE due_at>=? AND due_at<?",
                Timestamp.from(DATE.atStartOfDay(ZONE).toInstant().minusSeconds(1)),
                Timestamp.from(LocalDate.of(2041, 6, 9).atStartOfDay(ZONE).toInstant()));
    }

    @AfterEach void cleanup() {
        for (UUID requestId : requestIds) {
            jdbc.update("UPDATE reports SET previous_report_id=NULL WHERE previous_report_id IN (SELECT id FROM reports WHERE request_id=?)", requestId);
        }
        for (UUID requestId : requestIds) {
            jdbc.update("DELETE FROM report_sources WHERE report_id IN (SELECT id FROM reports WHERE request_id=?)", requestId);
            jdbc.update("DELETE FROM reports WHERE request_id=?", requestId);
        }
        recordIds.forEach(id -> jdbc.update("DELETE FROM work_records WHERE id=?", id));
        taskIds.forEach(id -> jdbc.update("DELETE FROM todo_items WHERE id=?", id));
    }

    @Test
    void freezesOnlyActiveRecordsAndPendingPlansInsideShanghaiDay() {
        Instant start = DATE.atStartOfDay(ZONE).toInstant();
        UUID first = insertRecord("边界内成果", start, true);
        insertRecord("左边界外", start.minusMillis(1), true);
        insertRecord("右边界外", DATE.plusDays(1).atStartOfDay(ZONE).toInstant(), true);
        insertRecord("已失效完成", start.plusSeconds(1), false);
        UUID plan = insertTask("当天计划", start.plusSeconds(2), "PENDING", null);
        UUID completedTask = insertTask("已完成且已有完成事实", start.plusSeconds(3), "COMPLETED", null);
        UUID completionRecord = insertCompletionRecord(completedTask, "已完成且已有完成事实", start.plusSeconds(3));
        insertTask("已撤销计划", start.plusSeconds(4), "PENDING", start.plusSeconds(5));
        when(gateway.generate(any(), any(), anyList())).thenAnswer(invocation -> {
            List<ReportSourcePrompt> sources = invocation.getArgument(2);
            UUID recordSource = sources.stream().filter(s -> s.type() == ReportSourceType.RECORD).findFirst().orElseThrow().id();
            UUID taskSource = sources.stream().filter(s -> s.type() == ReportSourceType.TASK).findFirst().orElseThrow().id();
            return result(bullet(ReportSectionType.ACHIEVEMENTS, "完成边界成果", recordSource),
                    bullet(ReportSectionType.PLANS, "执行当天计划", taskSource));
        });

        ReportResponse report = await(create());

        assertThat(report.status()).isEqualTo(ReportStatus.SUCCEEDED);
        assertThat(report.sources()).extracting(ReportResponse.Source::entityId).containsExactlyInAnyOrder(first, completionRecord, plan);
        assertThat(report.sources()).extracting(ReportResponse.Source::entityId).doesNotContain(completedTask);
        assertThat(report.content()).contains("完成边界成果", "执行当天计划", "## 工作进展", "暂无记录", "[来源 1]", "[来源 2]");
    }

    @Test
    void keepsSnapshotAndOldDraftWhenLaterGenerationFailsAndAllowsMultipleVersions() {
        Instant occurredAt = DATE.atStartOfDay(ZONE).toInstant().plusSeconds(60);
        UUID recordId = insertRecord("生成时原文", occurredAt, true);
        when(gateway.generate(any(), any(), anyList())).thenAnswer(invocation -> {
            List<ReportSourcePrompt> sources = invocation.getArgument(2);
            return result(bullet(ReportSectionType.PROGRESS, "保持进展", sources.get(0).id()));
        });
        ReportResponse first = await(create());
        jdbc.update("UPDATE work_records SET content='源记录后来已修改' WHERE id=?", recordId);
        ReportResponse stillFrozen = service.get(first.id());
        assertThat(stillFrozen.sources()).singleElement().extracting(ReportResponse.Source::content).isEqualTo("生成时原文");

        doReturn(result(bullet(ReportSectionType.PROGRESS, "越界引用", UUID.randomUUID())))
                .when(gateway).generate(any(), any(), anyList());
        ReportResponse failed = await(create());

        assertThat(failed.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(service.get(first.id()).content()).contains("保持进展");
        assertThat(service.list(DATE)).extracting(ReportResponse::id).contains(first.id(), failed.id());
    }

    @Test
    void emptyDayUsesDeterministicTextWithoutCallingModel() {
        LocalDate emptyDate = LocalDate.of(2041, 6, 7);
        UUID requestId = UUID.randomUUID(); requestIds.add(requestId);
        ReportResponse report = await(service.create(new CreateReportRequest(requestId, "DAILY", emptyDate)));
        assertThat(report.content()).contains("暂无记录", "暂无已安排计划");
        assertThat(report.sources()).isEmpty();
        verify(gateway, never()).generate(any(), any(), anyList());
    }

    @Test
    void explicitSaveUsesOptimisticVersionAndDoesNotMutateSnapshot() {
        UUID recordId = insertRecord("保存前来源", DATE.atStartOfDay(ZONE).toInstant().plusSeconds(120), true);
        when(gateway.generate(any(), any(), anyList())).thenAnswer(invocation -> result(
                bullet(ReportSectionType.ACHIEVEMENTS, "初始正文",
                        invocation.<List<ReportSourcePrompt>>getArgument(2).get(0).id())));
        ReportResponse generated = await(create());
        ReportResponse saved = service.update(generated.id(), new UpdateReportRequest("人工正文", generated.version()));
        assertThat(saved.content()).isEqualTo("人工正文");
        assertThat(saved.version()).isEqualTo(generated.version() + 1);
        assertThat(saved.editedAt()).isNotNull();
        assertThat(saved.sources()).singleElement().extracting(ReportResponse.Source::entityId).isEqualTo(recordId);
        assertThatThrownBy(() -> service.update(generated.id(), new UpdateReportRequest("过期写入", generated.version())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(validator.validate(new UpdateReportRequest("缺少版本", null))).isNotEmpty();
    }

    @Test
    void requestIdIsIdempotentUnderConcurrentCreateAndConflictsAcrossDates() throws Exception {
        insertRecord("并发日报来源", DATE.atStartOfDay(ZONE).toInstant().plusSeconds(180), true);
        UUID requestId = UUID.randomUUID(); requestIds.add(requestId);
        when(gateway.generate(any(), any(), anyList())).thenAnswer(invocation -> result(
                bullet(ReportSectionType.PROGRESS, "并发只生成一次",
                        invocation.<List<ReportSourcePrompt>>getArgument(2).get(0).id())));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ReportResponse> first = callers.submit(() -> { start.await(); return service.create(new CreateReportRequest(requestId, "DAILY", DATE)); });
            Future<ReportResponse> second = callers.submit(() -> { start.await(); return service.create(new CreateReportRequest(requestId, "DAILY", DATE)); });
            start.countDown();
            ReportResponse firstResponse = first.get();
            ReportResponse secondResponse = second.get();
            assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
            await(firstResponse);
            verify(gateway, times(1)).generate(any(), any(), anyList());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE request_id=?", Integer.class, requestId)).isEqualTo(1);
            assertThatThrownBy(() -> service.create(new CreateReportRequest(requestId, "DAILY", DATE.plusDays(1))))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void rejectsMissingDuplicateAndCrossSectionSourceReferences() {
        ReportSourceRow record = source(ReportSourceType.RECORD);
        ReportSourceRow task = source(ReportSourceType.TASK);
        assertThatThrownBy(() -> service.validate(result(new AiReportResult.Section("PROGRESS", List.of(
                new AiReportResult.Bullet("无来源", List.of())))), List.of(record)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("缺少来源");
        assertThatThrownBy(() -> service.validate(result(new AiReportResult.Section("ACHIEVEMENTS", List.of(
                new AiReportResult.Bullet("重复", List.of(record.id(), record.id()))))), List.of(record)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复引用");
        assertThatThrownBy(() -> service.validate(result(bullet(ReportSectionType.PLANS, "越界", record.id())), List.of(record, task)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("事实边界");
    }

    private ReportResponse create() {
        UUID requestId = UUID.randomUUID(); requestIds.add(requestId);
        return service.create(new CreateReportRequest(requestId, "DAILY", DATE));
    }

    private ReportResponse await(ReportResponse initial) {
        ReportResponse current = initial;
        for (int i = 0; current.status() == ReportStatus.PROCESSING && i < 100; i++) {
            try { Thread.sleep(25); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
            current = service.get(initial.id());
        }
        assertThat(current.status()).isNotEqualTo(ReportStatus.PROCESSING);
        return current;
    }

    private UUID insertRecord(String content, Instant occurredAt, boolean active) {
        UUID id = UUID.randomUUID(); recordIds.add(id);
        jdbc.update("INSERT INTO work_records(id,content,occurred_at,is_active) VALUES (?,?,?,?)", id, content, Timestamp.from(occurredAt), active);
        return id;
    }

    private UUID insertTask(String title, Instant dueAt, String status, Instant deletedAt) {
        UUID id = UUID.randomUUID(); taskIds.add(id);
        jdbc.update("INSERT INTO todo_items(id,title,status,due_at,deleted_at,priority,notes,version) VALUES (?,?,?,?,?,'MEDIUM','',0)",
                id, title, status, Timestamp.from(dueAt), deletedAt == null ? null : Timestamp.from(deletedAt));
        return id;
    }

    private UUID insertCompletionRecord(UUID taskId, String content, Instant occurredAt) {
        UUID id = UUID.randomUUID(); recordIds.add(id);
        jdbc.update("""
                INSERT INTO work_records(id,content,source,todo_id,completion_result,occurred_at,is_active)
                VALUES (?,?,'TASK_COMPLETION',?,'',?,true)
                """, id, content, taskId, Timestamp.from(occurredAt));
        return id;
    }

    private ReportSourceRow source(ReportSourceType type) {
        return new ReportSourceRow(UUID.randomUUID(), UUID.randomUUID(), type, UUID.randomUUID(), "来源", null,
                null, type == ReportSourceType.RECORD ? "MANUAL" : "PENDING", Instant.now(), "{}");
    }

    private AiReportResult.Section bullet(ReportSectionType type, String text, UUID sourceId) {
        return new AiReportResult.Section(type.name(), List.of(new AiReportResult.Bullet(text, List.of(sourceId))));
    }
    private AiReportResult result(AiReportResult.Section... sections) { return new AiReportResult(List.of(sections)); }
}
