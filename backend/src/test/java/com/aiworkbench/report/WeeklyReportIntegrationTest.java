package com.aiworkbench.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class WeeklyReportIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate MONDAY = LocalDate.of(2044, 5, 2);
    @Autowired ReportService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ReportAiGateway gateway;
    private final List<UUID> requestIds = new ArrayList<>();
    private final List<UUID> recordIds = new ArrayList<>();
    private final List<UUID> taskIds = new ArrayList<>();
    private final List<UUID> projectIds = new ArrayList<>();

    @BeforeEach void resetGateway() { reset(gateway); }

    @AfterEach void cleanup() {
        requestIds.forEach(id -> jdbc.update("""
                UPDATE reports SET previous_report_id=NULL
                WHERE request_id=? OR previous_report_id IN (SELECT id FROM reports WHERE request_id=?)
                """, id, id));
        requestIds.forEach(id -> jdbc.update(
                "DELETE FROM report_sources WHERE report_id IN (SELECT id FROM reports WHERE request_id=?)", id));
        requestIds.forEach(id -> jdbc.update("DELETE FROM reports WHERE request_id=?", id));
        recordIds.forEach(id -> jdbc.update("DELETE FROM work_records WHERE id=?", id));
        taskIds.forEach(id -> jdbc.update("DELETE FROM todo_items WHERE id=?", id));
        projectIds.forEach(id -> jdbc.update("DELETE FROM projects WHERE id=?", id));
    }

    @Test
    void normalizesToMondayAndFreezesRoleBoundariesIncludingSameTaskTwice() {
        Instant weekStart = at(MONDAY);
        Instant weekEnd = at(MONDAY.plusWeeks(1));
        UUID inside = insertRecord("本周有效成果", weekStart, null, true);
        insertRecord("边界之前", weekStart.minusMillis(1), null, true);
        insertRecord("右边界", weekEnd, null, true);
        insertRecord("已失效成果", weekStart.plusSeconds(1), null, false);
        UUID current = insertTask("本周更新但无期限", null, weekStart.plusSeconds(2), weekStart.plusSeconds(3));
        UUID dualRole = insertTask("本周创建且下周到期", weekEnd.plusSeconds(60),
                weekStart.plusSeconds(4), weekStart.plusSeconds(4));
        insertTask("无期限且本周外无活动", null, weekStart.minusSeconds(100), weekStart.minusSeconds(100));
        when(gateway.generateWeekly(any(), any(), any(), anyList())).thenAnswer(invocation -> {
            List<ReportSourcePrompt> sources = invocation.getArgument(3);
            UUID record = source(sources, ReportSourceRole.WEEK_RECORD, inside);
            UUID currentSource = source(sources, ReportSourceRole.CURRENT_TASK, current);
            UUID plan = source(sources, ReportSourceRole.NEXT_WEEK_TASK, dualRole);
            return result(section(ReportSectionType.ACHIEVEMENTS, "完成成果", record),
                    section(ReportSectionType.PROGRESS, "推进当前事项", currentSource),
                    section(ReportSectionType.PLANS, "落实下周任务", plan));
        });

        ReportResponse report = await(create(MONDAY.plusDays(3)));

        assertThat(report.date()).isEqualTo(MONDAY);
        assertThat(report.periodEnd()).isEqualTo(MONDAY.plusWeeks(1));
        assertThat(report.sources()).extracting(ReportResponse.Source::entityId)
                .contains(inside, current, dualRole);
        assertThat(report.sources().stream().filter(source -> source.entityId().equals(dualRole)))
                .extracting(ReportResponse.Source::role)
                .containsExactlyInAnyOrder(ReportSourceRole.CURRENT_TASK, ReportSourceRole.NEXT_WEEK_TASK);
        assertThat(report.content()).contains("## 本周完成", "## 进行中与阻碍", "## 下周计划");
    }

    @Test
    void regeneratesAsLinkedVersionAndKeepsProjectSnapshotAndOldDraft() {
        UUID projectId = insertProject("旧项目名");
        UUID recordId = insertRecord("初始成果", at(MONDAY).plusSeconds(30), projectId, true);
        when(gateway.generateWeekly(any(), any(), any(), anyList())).thenAnswer(invocation -> {
            List<ReportSourcePrompt> sources = invocation.getArgument(3);
            return result(section(ReportSectionType.ACHIEVEMENTS, "冻结成果",
                    source(sources, ReportSourceRole.WEEK_RECORD, recordId)));
        });
        ReportResponse first = await(create(MONDAY));
        ReportResponse edited = service.update(first.id(), new UpdateReportRequest("用户编辑后的旧稿", first.version()));

        jdbc.update("UPDATE projects SET name='新项目名' WHERE id=?", projectId);
        UUID backfill = insertRecord("后来补录", at(MONDAY).plusSeconds(60), projectId, true);
        ReportResponse second = await(create(MONDAY.plusDays(5)));
        jdbc.update("DELETE FROM work_records WHERE id=?", recordId);

        assertThat(second.previousReportId()).isEqualTo(first.id());
        assertThat(second.sources()).extracting(ReportResponse.Source::entityId).contains(recordId, backfill);
        assertThat(second.sources()).extracting(ReportResponse.Source::projectName).contains("新项目名");
        ReportResponse frozen = service.get(first.id());
        assertThat(frozen.content()).isEqualTo(edited.content());
        assertThat(frozen.sources()).singleElement().extracting(ReportResponse.Source::projectName).isEqualTo("旧项目名");
        assertThat(service.get(second.id()).sources()).extracting(ReportResponse.Source::entityId)
                .contains(recordId, backfill);
    }

    @Test
    void keepsManualAdditionsSeparateAndUsesOptimisticVersion() {
        UUID recordId = insertRecord("事实", at(MONDAY).plusSeconds(90), null, true);
        when(gateway.generateWeekly(any(), any(), any(), anyList())).thenAnswer(invocation -> result(
                section(ReportSectionType.ACHIEVEMENTS, "AI 正文",
                        source(invocation.getArgument(3), ReportSourceRole.WEEK_RECORD, recordId))));
        ReportResponse generated = await(create(MONDAY));

        ReportResponse saved = service.updateManualAdditions(generated.id(),
                new UpdateManualAdditionsRequest("这是用户判断，不附来源", generated.version()));

        assertThat(saved.content()).contains("AI 正文");
        assertThat(saved.manualAdditions()).isEqualTo("这是用户判断，不附来源");
        assertThat(saved.manualEditedAt()).isNotNull();
        assertThatThrownBy(() -> service.update(generated.id(),
                new UpdateReportRequest("过期正文", generated.version())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void permitsMergedAchievementWithMultipleRecordSourcesButRejectsWrongRoles() {
        ReportSourceRow first = sourceRow(ReportSourceRole.WEEK_RECORD);
        ReportSourceRow second = sourceRow(ReportSourceRole.WEEK_RECORD);
        ReportSourceRow plan = sourceRow(ReportSourceRole.NEXT_WEEK_TASK);
        var validated = service.validate(result(new AiReportResult.Section("ACHIEVEMENTS", List.of(
                new AiReportResult.Bullet("合并重复成果", List.of(first.id(), second.id()))))),
                List.of(first, second), "WEEKLY");
        assertThat(validated.get(ReportSectionType.ACHIEVEMENTS)).singleElement()
                .extracting(ReportService.ValidatedBullet::sourceIds)
                .asList().hasSize(2);
        assertThatThrownBy(() -> service.validate(result(section(ReportSectionType.PLANS, "无期限冒充计划", first.id())),
                List.of(first, plan), "WEEKLY"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("事实边界");
    }

    @Test
    void sameWeeklyRequestIsIdempotent() {
        UUID recordId = insertRecord("幂等事实", at(MONDAY).plusSeconds(120), null, true);
        UUID requestId = UUID.randomUUID(); requestIds.add(requestId);
        when(gateway.generateWeekly(any(), any(), any(), anyList())).thenAnswer(invocation -> result(
                section(ReportSectionType.PROGRESS, "幂等生成",
                        source(invocation.getArgument(3), ReportSourceRole.WEEK_RECORD, recordId))));
        ReportResponse first = service.create(new CreateReportRequest(requestId, "WEEKLY", MONDAY));
        ReportResponse second = service.create(new CreateReportRequest(requestId, "WEEKLY", MONDAY.plusDays(2)));
        assertThat(second.id()).isEqualTo(first.id());
        await(first);
        verify(gateway, times(1)).generateWeekly(any(), any(), any(), anyList());
    }

    @Test
    void concurrentWeeklyVersionsFormOneLinearChain() throws Exception {
        LocalDate isolatedMonday = LocalDate.of(2094, 8, 2);
        List<UUID> concurrentRequestIds = java.util.stream.IntStream.range(0, 6)
                .mapToObj(ignored -> UUID.randomUUID()).toList();
        requestIds.addAll(concurrentRequestIds);
        CountDownLatch ready = new CountDownLatch(concurrentRequestIds.size());
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(concurrentRequestIds.size());
        try {
            List<Future<ReportResponse>> futures = concurrentRequestIds.stream().map(requestId -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return service.create(new CreateReportRequest(requestId, "WEEKLY", isolatedMonday));
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ReportResponse> versions = futures.stream().map(future -> {
                try { return future.get(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            Set<UUID> ids = versions.stream().map(ReportResponse::id).collect(java.util.stream.Collectors.toSet());
            List<UUID> predecessors = versions.stream().map(version -> service.get(version.id()).previousReportId())
                    .filter(java.util.Objects::nonNull).toList();

            assertThat(ids).hasSize(concurrentRequestIds.size());
            assertThat(predecessors).hasSize(concurrentRequestIds.size() - 1).doesNotHaveDuplicates();
            assertThat(ids).containsAll(predecessors);
        } finally {
            executor.shutdownNow();
        }
    }

    private ReportResponse create(LocalDate date) {
        UUID requestId = UUID.randomUUID(); requestIds.add(requestId);
        return service.create(new CreateReportRequest(requestId, "WEEKLY", date));
    }

    private ReportResponse await(ReportResponse initial) {
        ReportResponse current = initial;
        for (int i = 0; current.status() == ReportStatus.PROCESSING && i < 120; i++) {
            try { Thread.sleep(25); } catch (InterruptedException exception) {
                Thread.currentThread().interrupt(); throw new AssertionError(exception);
            }
            current = service.get(initial.id());
        }
        assertThat(current.status()).isNotEqualTo(ReportStatus.PROCESSING);
        return current;
    }

    private Instant at(LocalDate date) { return date.atStartOfDay(ZONE).toInstant(); }

    private UUID insertProject(String name) {
        UUID id = UUID.randomUUID(); projectIds.add(id);
        jdbc.update("INSERT INTO projects(id,name,status) VALUES (?,?,'ACTIVE')", id, name);
        return id;
    }

    private UUID insertRecord(String content, Instant occurredAt, UUID projectId, boolean active) {
        UUID id = UUID.randomUUID(); recordIds.add(id);
        jdbc.update("INSERT INTO work_records(id,project_id,content,occurred_at,is_active) VALUES (?,?,?,?,?)",
                id, projectId, content, Timestamp.from(occurredAt), active);
        return id;
    }

    private UUID insertTask(String title, Instant dueAt, Instant createdAt, Instant updatedAt) {
        UUID id = UUID.randomUUID(); taskIds.add(id);
        jdbc.update("""
                INSERT INTO todo_items(id,title,status,due_at,priority,notes,version,created_at,updated_at)
                VALUES (?,?,'PENDING',?,'MEDIUM','',0,?,?)
                """, id, title, dueAt == null ? null : Timestamp.from(dueAt),
                Timestamp.from(createdAt), Timestamp.from(updatedAt));
        return id;
    }

    private UUID source(List<ReportSourcePrompt> sources, ReportSourceRole role, UUID entityId) {
        return sources.stream().filter(item -> item.role() == role)
                .filter(item -> jdbc.queryForObject("SELECT entity_id FROM report_sources WHERE id=?", UUID.class, item.id())
                        .equals(entityId))
                .findFirst().orElseThrow().id();
    }

    private ReportSourceRow sourceRow(ReportSourceRole role) {
        ReportSourceType type = role == ReportSourceRole.WEEK_RECORD ? ReportSourceType.RECORD : ReportSourceType.TASK;
        return new ReportSourceRow(UUID.randomUUID(), UUID.randomUUID(), type, role, UUID.randomUUID(), "来源",
                null, null, type.name(), Instant.now(), "{}");
    }

    private AiReportResult.Section section(ReportSectionType type, String text, UUID... sourceIds) {
        return new AiReportResult.Section(type.name(), List.of(new AiReportResult.Bullet(text, List.of(sourceIds))));
    }

    private AiReportResult result(AiReportResult.Section... sections) { return new AiReportResult(List.of(sections)); }
}
