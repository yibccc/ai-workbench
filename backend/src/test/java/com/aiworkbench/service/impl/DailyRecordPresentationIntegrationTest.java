package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.dto.task.TaskVersionRequest;
import com.aiworkbench.dto.task.UpdateCompletionResultRequest;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.WorkRecordSource;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.mapper.TaskMapper;
import com.aiworkbench.mapper.WorkRecordMapper;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.TaskService;
import com.aiworkbench.service.WorkRecordService;
import com.aiworkbench.support.OwnerTestContext;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DailyRecordPresentationIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2091, 10, 2);
    private static final Instant START = DATE.atStartOfDay(ZONE).toInstant();
    @Autowired WorkRecordService records;
    @Autowired TaskService tasks;
    @Autowired ProjectService projects;
    @Autowired TaskMapper taskMapper;
    @Autowired WorkRecordMapper recordMapper;
    @Autowired FocusStore store;
    @Autowired ReportMapper reports;
    @Autowired WorkbenchEventHub events;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() {
        OwnerTestContext.ensureAccounts(jdbc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
    }

    @Test void mergesBeforeCountingAndPagingEvenWhenRawPairCrossesPageBoundary() throws Exception {
        var task = task("跨页待办");
        UUID firstFocus = focus(task.id(), START.plusSeconds(60), 30_000, 5_000);
        UUID secondFocus = focus(task.id(), START.plusSeconds(120), 60_000, 10_000);
        var completed = complete(task, START.plusSeconds(180), "一次完成结果");
        createdAt(firstFocus, 4); createdAt(secondFocus, 3); createdAt(completed.completionRecordId(), 5);
        for (int index : new int[] {0, 6, 7, 8, 9}) {
            UUID id = UUID.randomUUID();
            recordMapper.insert(OwnerTestContext.USER_ID, id, null, "独立记录" + index, START.plusSeconds(300));
            createdAt(id, index);
        }

        var first = records.page(DATE, 0, 5);
        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(5);
        assertThat(first.items().get(4)).satisfies(item -> {
            assertThat(item.id()).isEqualTo(completed.completionRecordId());
            assertThat(item.source()).isEqualTo(WorkRecordSource.TASK_COMPLETION);
            assertThat(item.content()).isEqualTo("完成待办：" + task.title());
            assertThat(item.completionResult()).isEqualTo("一次完成结果");
            assertThat(item.focusMs()).isEqualTo(90_000L);
            assertThat(item.breakMs()).isEqualTo(15_000L);
            assertThat(item.sessionId()).isNull();
        });
        assertThat(records.page(DATE, 1, 5).items()).singleElement()
                .extracting(WorkRecordResponse::content).isEqualTo("独立记录0");
        var beyond = records.page(DATE, 2, 5);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(6);
        assertThat(records.list(DATE)).hasSize(8);
        assertThat(records.get(firstFocus).focusMs()).isEqualTo(30_000L);
        assertThat(records.get(completed.completionRecordId()).focusMs()).isNull();
        assertThat(store.todayTotals(OwnerTestContext.USER_ID, DATE).focusMs()).isEqualTo(90_000L);
        assertThat(reports.findCandidateRecords(OwnerTestContext.USER_ID, START, START.plus(Duration.ofDays(1))))
                .hasSize(8).extracting(row -> row.entityId()).contains(firstFocus, secondFocus, completed.completionRecordId());

        var session = OwnerTestContext.login(mvc);
        mvc.perform(OwnerTestContext.authenticated(get("/api/records/page")
                        .param("date", DATE.toString()).param("page", "0").param("size", "5"), session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[4].focusMs").value(90_000))
                .andExpect(jsonPath("$.items[4].completionResult").value("一次完成结果"));
    }

    @Test void isolatesDayOwnerAndTaskAndKeepsUnlinkedAndStandaloneEntries() {
        var completedTask = task("相同名称");
        UUID priorDay = focus(completedTask.id(), START.minusSeconds(1), 500, 0);
        focus(completedTask.id(), START, 1_000, 100);
        UUID nextDay = focus(completedTask.id(), START.plus(Duration.ofDays(1)), 2_000, 200);
        var completed = complete(completedTask, START.plusSeconds(60), "当天结果");
        var laterTask = task("相同名称");
        UUID todayPending = focus(laterTask.id(), START.plusSeconds(180), 3_000, 300);
        complete(laterTask, START.plus(Duration.ofDays(1)).plusSeconds(60), "明天结果");
        var standalone = complete(task("没有专注"), START.plusSeconds(240), "独立完成");
        UUID unlinked = focus(null, START.plusSeconds(300), 4_000, 400);

        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        var foreign = task("相同名称");
        focus(foreign.id(), START, 10_000, 1_000);
        var foreignCompleted = complete(foreign, START.plusSeconds(60), "另一用户结果");
        var foreignPage = records.page(DATE, 0, 5);
        assertThat(foreignPage.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(foreignCompleted.completionRecordId());
            assertThat(item.focusMs()).isEqualTo(10_000L);
        });

        OwnerTestContext.use(OwnerTestContext.USER_ID);
        var page = records.page(DATE, 0, 5);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.items()).extracting(WorkRecordResponse::id)
                .containsExactlyInAnyOrder(completed.completionRecordId(), todayPending, standalone.completionRecordId(), unlinked);
        assertThat(page.items()).filteredOn(item -> item.id().equals(completed.completionRecordId()))
                .singleElement().satisfies(item -> assertThat(item.focusMs()).isEqualTo(1_000L));
        assertThat(page.items()).filteredOn(item -> item.id().equals(standalone.completionRecordId()))
                .singleElement().satisfies(item -> assertThat(item.focusMs()).isNull());
        assertThat(records.page(DATE.minusDays(1), 0, 5).items()).extracting(WorkRecordResponse::id).containsExactly(priorDay);
        assertThat(records.page(DATE.plusDays(1), 0, 5).items()).extracting(WorkRecordResponse::id).contains(nextDay);
    }

    @Test void linkedProgressRetriesResultEditsAndReopeningRetainRawTimingAndReportFacts() {
        LocalDate today = LocalDate.now(ZONE);
        MutableClock clock = new MutableClock(today.atTime(12, 0).atZone(ZONE).toInstant());
        var focus = new FocusServiceImpl(store, taskMapper, tasks, projects, records, events, ZONE, clock);
        var task = task("保存专注结果");
        Session first = focus.start(new Start(UUID.randomUUID(), "第一段专注", task.id(), null, 25, 10));
        clock.advance(20);
        first = focus.end(first.id(), new Version(first.version()));
        Session second = focus.start(new Start(UUID.randomUUID(), "第二段专注", task.id(), null, 25, 10));
        clock.advance(40);
        second = focus.end(second.id(), new Version(second.version()));
        second = focus.progress(second.id(), new Progress(second.version(), "原始完成结果"));
        second = focus.progress(second.id(), new Progress(second.version(), "原始完成结果"));
        var completed = tasks.get(task.id());
        assertThat(records.page(today, 0, 5).items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(completed.completionRecordId());
            assertThat(item.focusMs()).isEqualTo(60_000L);
            assertThat(item.completionResult()).isEqualTo("原始完成结果");
        });
        var rawBefore = records.list(today);
        assertThat(rawBefore).hasSize(3);
        var totalsBefore = store.todayTotals(OwnerTestContext.USER_ID, today);
        Instant start = today.atStartOfDay(ZONE).toInstant();
        var sourceIds = reports.findCandidateRecords(OwnerTestContext.USER_ID, start, start.plus(Duration.ofDays(1)))
                .stream().map(row -> row.entityId()).toList();
        assertThat(sourceIds).hasSize(3).contains(completed.completionRecordId());

        var updated = tasks.updateCompletionResult(task.id(), new UpdateCompletionResultRequest(completed.version(), "修改后的结果"));
        assertThat(records.page(today, 0, 5).items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(completed.completionRecordId());
            assertThat(item.completionResult()).isEqualTo("修改后的结果");
            assertThat(item.focusMs()).isEqualTo(60_000L);
        });
        tasks.reopen(task.id(), new TaskVersionRequest(updated.version()));
        var reopened = records.page(today, 0, 5);
        assertThat(reopened.totalElements()).isEqualTo(2);
        assertThat(reopened.items()).allSatisfy(item -> assertThat(item.source()).isEqualTo(WorkRecordSource.FOCUS_SESSION));
        assertThat(reopened.items()).extracting(WorkRecordResponse::sessionId).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(store.todayTotals(OwnerTestContext.USER_ID, today)).isEqualTo(totalsBefore);
        assertThat(records.list(today)).containsExactlyInAnyOrderElementsOf(rawBefore.stream()
                .filter(item -> item.source() == WorkRecordSource.FOCUS_SESSION).toList());
        assertThat(reports.findCandidateRecords(OwnerTestContext.USER_ID, start, start.plus(Duration.ofDays(1))))
                .hasSize(2).extracting(row -> row.entityId()).containsAll(sourceIds.stream()
                        .filter(id -> !id.equals(completed.completionRecordId())).toList());
    }

    private TaskResponse task(String title) {
        return tasks.create(new CreateTaskRequest(null, title, "", null, TaskPriority.MEDIUM));
    }

    private TaskResponse complete(TaskResponse task, Instant occurredAt, String result) {
        var completed = tasks.complete(task.id(), new CompleteTaskRequest(task.version(), result));
        // Explicit historical-day fixture: production completion uses the real clock.
        jdbc.update("UPDATE work_records SET occurred_at=? WHERE id=?", Timestamp.from(occurredAt), completed.completionRecordId());
        return completed;
    }

    private UUID focus(UUID taskId, Instant start, long focusMs, long breakMs) {
        UUID userId = com.aiworkbench.security.CurrentUser.requireId();
        UUID sessionId = UUID.randomUUID();
        Instant end = start.plusMillis(focusMs + breakMs);
        store.insertSession(sessionId, userId, UUID.randomUUID(), taskId, null, "专注投入", 60_000, 60_000, ZONE.getId(), start);
        jdbc.update("UPDATE focus_sessions SET phase='ENDED', ended_at=?, focus_ms=?, break_ms=? WHERE id=?",
                Timestamp.from(end), focusMs, breakMs, sessionId);
        UUID recordId = UUID.randomUUID();
        store.insertFocusRecord(recordId, userId, null, taskId, sessionId, "专注投入", start.atZone(ZONE).toLocalDate(), focusMs, breakMs, start, end);
        return recordId;
    }

    private void createdAt(UUID id, int seconds) {
        jdbc.update("UPDATE work_records SET created_at=? WHERE id=?", Timestamp.from(START.plusSeconds(seconds)), id);
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
