package com.aiworkbench.owner;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.AiReportResult;
import com.aiworkbench.ai.ReportAiGateway;
import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.dto.input.CreateInputRequest;
import com.aiworkbench.dto.input.InputResponse;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.UpdateProjectRequest;
import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.UpdateWorkRecordRequest;
import com.aiworkbench.dto.report.CreateReportRequest;
import com.aiworkbench.dto.report.ReportResponse;
import com.aiworkbench.dto.report.UpdateReportRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.UpdateTaskRequest;
import com.aiworkbench.enums.InputStatus;
import com.aiworkbench.enums.ReportStatus;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.service.InputService;
import com.aiworkbench.service.InputPersistenceService;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.ReportService;
import com.aiworkbench.service.TaskService;
import com.aiworkbench.service.WorkRecordService;
import com.aiworkbench.service.AccountService;
import com.aiworkbench.support.OwnerTestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessOwnerIsolationIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2052, 4, 9);
    private static final Instant OCCURRED = Instant.parse("2052-04-09T04:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectService projects;
    @Autowired TaskService tasks;
    @Autowired WorkRecordService records;
    @Autowired InputService inputs;
    @Autowired InputPersistenceService inputPersistence;
    @Autowired ReportService reports;
    @Autowired AccountService accounts;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReportMapper reportMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean WorkbenchAiGateway captureAi;
    @MockitoBean ReportAiGateway reportAi;

    @BeforeEach
    void owner() {
        OwnerTestContext.ensureAccounts(jdbc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        reset(captureAi, reportAi);
    }

    @AfterEach
    void cleanup() {
        OwnerTestContext.removeBusinessData(jdbc);
    }

    @Test
    void listsIdsAndAssociationsArePrivateIncludingForAdministrators() {
        String sameName = "同名项目-" + UUID.randomUUID();
        var projectA = projects.create(new CreateProjectRequest(sameName));
        var taskA = tasks.create(new CreateTaskRequest(projectA.id(), "A 待办", "", null, null));
        var recordA = records.create(new CreateWorkRecordRequest(projectA.id(), "A 记录", OCCURRED));

        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        var projectB = projects.create(new CreateProjectRequest(sameName));
        assertThat(projectB.id()).isNotEqualTo(projectA.id());
        assertThat(projects.list(false)).extracting(p -> p.id()).containsExactly(projectB.id());
        assertThat(projects.page(false, sameName, 0, 5).totalElements()).isEqualTo(1);
        assertThat(tasks.list(null, null, false, null, null)).isEmpty();
        assertThat(records.list(DAY)).isEmpty();
        assertNotFound(() -> projects.get(projectA.id()));
        assertNotFound(() -> projects.archive(projectA.id()));
        assertNotFound(() -> projects.rename(projectA.id(), new UpdateProjectRequest("窃改项目")));
        assertNotFound(() -> tasks.get(taskA.id()));
        assertNotFound(() -> tasks.delete(taskA.id(), taskA.version()));
        assertNotFound(() -> tasks.update(taskA.id(), new UpdateTaskRequest(
                projectB.id(), "窃改待办", "", null, taskA.priority(), taskA.version())));
        assertNotFound(() -> records.get(recordA.id()));
        assertNotFound(() -> records.delete(recordA.id()));
        assertNotFound(() -> records.update(recordA.id(), new UpdateWorkRecordRequest(
                projectB.id(), "窃改记录", OCCURRED)));
        assertNotFound(() -> tasks.create(new CreateTaskRequest(projectA.id(), "窃用项目", "", null, null)));
        assertNotFound(() -> records.create(new CreateWorkRecordRequest(projectA.id(), "窃用项目", OCCURRED)));
        assertNotFound(() -> tasks.list(null, projectA.id(), false, null, null));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO todo_items (id, user_id, project_id, title) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), OwnerTestContext.OTHER_ID, projectA.id(), "跨用户关联"))
                .isInstanceOf(DataIntegrityViolationException.class);

        OwnerTestContext.use(OwnerTestContext.ADMIN_ID);
        assertThat(projects.list(true)).isEmpty();
        assertThat(tasks.list(null, null, false, null, null)).isEmpty();
        assertThat(records.list(DAY)).isEmpty();
        assertNotFound(() -> projects.get(projectA.id()));

        OwnerTestContext.use(OwnerTestContext.USER_ID);
        assertThat(projects.get(projectA.id()).name()).isEqualTo(sameName);
        assertThat(tasks.get(taskA.id()).id()).isEqualTo(taskA.id());
        assertThat(records.get(recordA.id()).id()).isEqualTo(recordA.id());
        assertThat(tasks.get(taskA.id()).title()).isEqualTo("A 待办");
        assertThat(records.get(recordA.id()).content()).isEqualTo("A 记录");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE user_id=?", Integer.class,
                OwnerTestContext.OTHER_ID)).isZero();
    }

    @Test
    void realHttpPagesAndIdsNeverExposeAnotherUsersHistoryOrCounts() throws Exception {
        String markerA = "owner-a-" + UUID.randomUUID();
        String markerB = "owner-b-secret-" + UUID.randomUUID();
        String markerAdmin = "owner-admin-" + UUID.randomUUID();
        UUID[] archivedProjectB = new UUID[1];
        UUID[] historicalRecordB = new UUID[1];
        UUID[] historicalTaskB = new UUID[1];
        UUID[] historicalReportB = new UUID[1];
        UUID[] deletedTaskB = new UUID[1];
        UUID[] inputB = new UUID[1];
        UUID[] ownRecordAdmin = new UUID[1];
        for (int ownerIndex = 0; ownerIndex < 3; ownerIndex++) {
            UUID ownerId = ownerIndex == 0 ? OwnerTestContext.USER_ID
                    : ownerIndex == 1 ? OwnerTestContext.OTHER_ID : OwnerTestContext.ADMIN_ID;
            String marker = ownerIndex == 0 ? markerA : ownerIndex == 1 ? markerB : markerAdmin;
            OwnerTestContext.use(ownerId);
            UUID firstProject = null;
            UUID firstRecord = null;
            UUID firstTask = null;
            for (int index = 0; index < 12; index++) {
                var project = projects.create(new CreateProjectRequest(marker + "-project-" + index));
                var record = records.create(new CreateWorkRecordRequest(
                        project.id(), marker + "-record-" + index, OCCURRED));
                var task = tasks.create(new CreateTaskRequest(
                        project.id(), marker + "-task-" + index, "", null, null));
                if (index == 0) {
                    firstProject = project.id();
                    firstRecord = record.id();
                    firstTask = task.id();
                }
                UUID reportId = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO reports(id,user_id,request_id,report_type,period_start,period_end,status,content)
                        VALUES (?,?,?,'DAILY',?,?,'SUCCEEDED',?)
                        """, reportId, ownerId, UUID.randomUUID(), DAY, DAY,
                        marker + "-report-" + index);
                if (index == 0 && ownerIndex == 1) historicalReportB[0] = reportId;
            }
            if (ownerIndex == 1) {
                archivedProjectB[0] = firstProject;
                historicalRecordB[0] = firstRecord;
                historicalTaskB[0] = firstTask;
                UUID sourceId = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO report_sources(id,report_id,source_type,source_role,entity_id,content,
                                                   project_id,project_name,source_status,source_time,snapshot)
                        VALUES (?,?,'RECORD','DAILY_RECORD',?,?,?,?,?,?,'{}'::jsonb)
                        """, sourceId, historicalReportB[0], firstRecord, marker + "-frozen-source",
                        firstProject, marker + "-project-0", "MANUAL", java.sql.Timestamp.from(OCCURRED));
                jdbc.update("UPDATE reports SET source_count=1 WHERE id=?", historicalReportB[0]);
                var deleted = tasks.create(new CreateTaskRequest(
                        firstProject, marker + "-deleted-task", "", null, null));
                deletedTaskB[0] = deleted.id();
                tasks.delete(deleted.id(), deleted.version());
                projects.archive(firstProject);
            }
            if (ownerIndex == 2) ownRecordAdmin[0] = firstRecord;
        }
        when(captureAi.extract(any(), any(), any(), anyList())).thenAnswer(call -> {
            String content = call.getArgument(0);
            Instant referenceAt = call.getArgument(1);
            return new AiCaptureResult(List.of(new AiCaptureResult.RecordItem(
                    "generated-" + content, null, referenceAt.toString())), List.of());
        });
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        awaitInput(inputs.create(new CreateInputRequest("http-owner-a-" + UUID.randomUUID(), markerA)).id());
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        inputB[0] = inputs.create(new CreateInputRequest("http-owner-b-" + UUID.randomUUID(), markerB)).id();
        assertThat(awaitInput(inputB[0]).status()).isEqualTo(InputStatus.SUCCEEDED);
        OwnerTestContext.use(OwnerTestContext.ADMIN_ID);
        awaitInput(inputs.create(new CreateInputRequest("http-owner-admin-" + UUID.randomUUID(), markerAdmin)).id());

        Cookie sessionA = OwnerTestContext.login(mvc, "owner-test-a");
        Cookie sessionB = OwnerTestContext.login(mvc, "owner-test-b");
        Cookie sessionAdmin = OwnerTestContext.login(mvc, "owner-test-admin");
        for (var identity : List.of(
                new HttpOwner(sessionA, OwnerTestContext.USER_ID, "USER", markerB, 12, 12),
                new HttpOwner(sessionB, OwnerTestContext.OTHER_ID, "USER", markerA, 12, 11),
                new HttpOwner(sessionAdmin, OwnerTestContext.ADMIN_ID, "ADMIN", markerB, 12, 12))) {
            assertPage(identity, get("/api/projects/page").param("includeArchived", "true")
                    .param("page", "2").param("size", "5"), identity.allProjects(), 2);
            assertPage(identity, get("/api/projects/page").param("page", "2").param("size", "5"),
                    identity.activeProjects(), identity.activeProjects() - 10);
            assertPage(identity, get("/api/tasks/page").param("page", "2").param("size", "5"),
                    identity.allProjects(), 2);
            assertPage(identity, get("/api/records/page").param("date", DAY.toString())
                    .param("page", "2").param("size", "5"),
                    identity.allProjects(), 2);
            assertPage(identity, get("/api/reports/page").param("reportType", "DAILY")
                    .param("date", DAY.toString()).param("page", "2").param("size", "5"),
                    identity.allProjects(), 2);
        }
        assertPage(new HttpOwner(sessionA, OwnerTestContext.USER_ID, "USER", markerB, 12, 12),
                get("/api/projects/page").param("q", markerB).param("size", "5"), 0, 0);
        assertPage(new HttpOwner(sessionB, OwnerTestContext.OTHER_ID, "USER", markerA, 12, 11),
                get("/api/projects/page").param("q", markerB).param("includeArchived", "true")
                        .param("page", "2").param("size", "5"), 12, 2);
        assertPage(new HttpOwner(sessionAdmin, OwnerTestContext.ADMIN_ID, "ADMIN", markerB, 12, 12),
                get("/api/projects/page").param("q", markerB).param("size", "5"), 0, 0);

        HttpOwner b = new HttpOwner(sessionB, OwnerTestContext.OTHER_ID, "USER", markerA, 12, 11);
        String bProjects = performAs(b, get("/api/projects").param("includeArchived", "true"))
                .getResponse().getContentAsString();
        assertThat(bProjects).contains(archivedProjectB[0].toString(), "ARCHIVED").doesNotContain(markerA);
        String bTask = performAs(b, get("/api/tasks/{id}", historicalTaskB[0]))
                .getResponse().getContentAsString();
        String bRecord = performAs(b, get("/api/records/{id}", historicalRecordB[0]))
                .getResponse().getContentAsString();
        assertThat(bTask).contains(archivedProjectB[0].toString(), "ARCHIVED");
        assertThat(bRecord).contains(archivedProjectB[0].toString(), "ARCHIVED");
        String bEvents = performAs(b, get("/api/tasks/{id}/events", deletedTaskB[0]))
                .getResponse().getContentAsString();
        assertThat(bEvents).contains("DELETED", markerB);
        String bHistory = performAs(b, get("/api/reports").param("date", DAY.toString())
                        .param("reportType", "DAILY"))
                .getResponse().getContentAsString();
        assertThat(bHistory).contains(historicalReportB[0].toString()).doesNotContain(markerA);
        assertThat(bHistory).doesNotContain(markerAdmin);
        String bInput = performAs(b, get("/api/inputs/{id}", inputB[0]))
                .getResponse().getContentAsString();
        assertThat(bInput).contains("generated-" + markerB).doesNotContain(markerA, markerAdmin);
        String bDetail = performAs(b, get("/api/reports/{id}", historicalReportB[0]))
                .getResponse().getContentAsString();
        assertThat(bDetail).contains(markerB + "-frozen-source", markerB + "-project-0");
        assertPage(b, get("/api/reports/{id}/sources/page", historicalReportB[0])
                .param("page", "0").param("size", "5"), 1, 1);
        HttpOwner admin = new HttpOwner(sessionAdmin, OwnerTestContext.ADMIN_ID, "ADMIN", markerB, 12, 12);
        assertThat(performAs(admin, get("/api/records/{id}", ownRecordAdmin[0]))
                .getResponse().getContentAsString()).contains(markerAdmin).doesNotContain(markerB);

        UUID absent = UUID.randomUUID();
        for (HttpOwner outsider : List.of(
                new HttpOwner(sessionA, OwnerTestContext.USER_ID, "USER", markerB, 12, 12),
                new HttpOwner(sessionAdmin, OwnerTestContext.ADMIN_ID, "ADMIN", markerB, 12, 12))) {
            assertSameSafe404(outsider, get("/api/records/{id}", historicalRecordB[0]),
                    get("/api/records/{id}", absent), markerB);
            assertSameSafe404(outsider, get("/api/tasks/{id}", historicalTaskB[0]),
                    get("/api/tasks/{id}", absent), markerB);
            assertSameSafe404(outsider, get("/api/tasks/{id}/events", deletedTaskB[0]),
                    get("/api/tasks/{id}/events", absent), markerB);
            assertSameSafe404(outsider, get("/api/inputs/{id}", inputB[0]),
                    get("/api/inputs/{id}", absent), markerB);
            assertSameSafe404(outsider, get("/api/reports/{id}", historicalReportB[0]),
                    get("/api/reports/{id}", absent), markerB);
            assertSameSafe404(outsider, get("/api/reports/{id}/sources/page", historicalReportB[0])
                            .param("size", "5"),
                    get("/api/reports/{id}/sources/page", absent).param("size", "5"), markerB);
            assertSameSafe404(outsider, post("/api/projects/{id}/archive", archivedProjectB[0]).with(csrf()),
                    post("/api/projects/{id}/archive", absent).with(csrf()), markerB);
        }
    }

    @Test
    void identicalInputRequestIdsAndLateModelResultsStayWithTheirOriginalOwners() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(captureAi.extract(any(), any(), any(), anyList())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("model gate timed out");
            String content = call.getArgument(0);
            Instant referenceAt = call.getArgument(1);
            return new AiCaptureResult(List.of(new AiCaptureResult.RecordItem(
                    content, null, referenceAt.toString())), List.of());
        });
        String requestId = "shared-" + UUID.randomUUID();
        UUID inputA = inputs.create(new CreateInputRequest(requestId, "A 内容")).id();
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        UUID inputB = inputs.create(new CreateInputRequest(requestId, "B 内容")).id();
        try {
            assertThat(inputB).isNotEqualTo(inputA);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertNotFound(() -> inputs.get(inputA));
            assertNotFound(() -> inputs.retry(inputA));
            assertNotFound(() -> inputs.revert(inputA));
        } finally {
            release.countDown();
        }

        InputResponse b = awaitInput(inputB);
        assertThat(b.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(b.records()).singleElement().extracting(InputResponse.GeneratedRecord::content).isEqualTo("B 内容");
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        InputResponse a = awaitInput(inputA);
        assertThat(a.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(a.records()).singleElement().extracting(InputResponse.GeneratedRecord::content).isEqualTo("A 内容");
        assertThat(jdbc.queryForObject("SELECT user_id FROM work_records WHERE id=?", UUID.class,
                a.records().get(0).id())).isEqualTo(OwnerTestContext.USER_ID);
        assertThat(jdbc.queryForObject("SELECT user_id FROM work_records WHERE id=?", UUID.class,
                b.records().get(0).id())).isEqualTo(OwnerTestContext.OTHER_ID);
    }

    @Test
    void reportSourcesRequestIdsAndWeeklyPredecessorsStayWithinAnOwner() {
        var recordA = records.create(new CreateWorkRecordRequest(null, "A 来源", OCCURRED));
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        var recordB = records.create(new CreateWorkRecordRequest(null, "B 来源", OCCURRED));
        when(reportAi.generate(any(), any(), anyList())).thenAnswer(call -> {
            var source = call.<List<com.aiworkbench.ai.ReportSourcePrompt>>getArgument(2).get(0);
            return new AiReportResult(List.of(new AiReportResult.Section("PROGRESS", List.of(
                    new AiReportResult.Bullet("已完成", List.of(source.id()))))));
        });
        UUID sharedRequest = UUID.randomUUID();
        ReportResponse dailyB = awaitReport(reports.create(new CreateReportRequest(sharedRequest, "DAILY", DAY)).id());
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        ReportResponse dailyA = awaitReport(reports.create(new CreateReportRequest(sharedRequest, "DAILY", DAY)).id());
        assertThat(dailyA.id()).isNotEqualTo(dailyB.id());
        assertThat(dailyA.sources()).singleElement().extracting(ReportResponse.Source::entityId).isEqualTo(recordA.id());
        assertThat(dailyB.sources()).singleElement().extracting(ReportResponse.Source::entityId).isEqualTo(recordB.id());
        assertThat(reports.sourcePage(dailyA.id(), 0, 5).totalElements()).isEqualTo(1);
        assertNotFound(() -> reports.get(dailyB.id()));
        assertNotFound(() -> reports.sourcePage(dailyB.id(), 0, 5));
        assertNotFound(() -> reports.delete(dailyB.id(), dailyB.version()));
        assertNotFound(() -> reports.update(dailyB.id(), new UpdateReportRequest("窃改报告", dailyB.version())));

        LocalDate week = LocalDate.of(2053, 2, 3);
        UUID sharedWeeklyRequest = UUID.randomUUID();
        ReportResponse firstA = awaitReport(reports.create(new CreateReportRequest(sharedWeeklyRequest, "WEEKLY", week)).id());
        ReportResponse secondA = awaitReport(reports.create(new CreateReportRequest(UUID.randomUUID(), "WEEKLY", week)).id());
        assertThat(secondA.previousReportId()).isEqualTo(firstA.id());
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        ReportResponse firstB = awaitReport(reports.create(new CreateReportRequest(sharedWeeklyRequest, "WEEKLY", week)).id());
        assertThat(firstB.previousReportId()).isNull();
        assertThat(firstB.id()).isNotEqualTo(firstA.id());
        OwnerTestContext.use(OwnerTestContext.ADMIN_ID);
        assertThat(reports.list("DAILY", DAY)).isEmpty();
        assertNotFound(() -> reports.get(dailyA.id()));
    }

    @Test
    void concurrentSameWeekVersionsUseIndependentOwnerLocksAndLinearChains() throws Exception {
        LocalDate week = LocalDate.of(2057, 5, 7);
        LocalDate periodStart = week.with(java.time.temporal.TemporalAdjusters.previousOrSame(
                java.time.DayOfWeek.MONDAY));
        CountDownLatch ownerLockHeld = new CountDownLatch(1);
        CountDownLatch releaseOwnerLock = new CountDownLatch(1);
        CountDownLatch callersReady = new CountDownLatch(4);
        CountDownLatch startCallers = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            Future<?> ownerLock = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                reportMapper.lockWeeklyVersionChain(OwnerTestContext.USER_ID, periodStart);
                ownerLockHeld.countDown();
                try {
                    if (!releaseOwnerLock.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("owner lock gate timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertThat(ownerLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ReportResponse> a1 = submitWeekly(executor, OwnerTestContext.USER_ID, week, callersReady, startCallers);
            Future<ReportResponse> a2 = submitWeekly(executor, OwnerTestContext.USER_ID, week, callersReady, startCallers);
            Future<ReportResponse> b1 = submitWeekly(executor, OwnerTestContext.OTHER_ID, week, callersReady, startCallers);
            Future<ReportResponse> b2 = submitWeekly(executor, OwnerTestContext.OTHER_ID, week, callersReady, startCallers);
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startCallers.countDown();

            // B must finish while A's transaction still holds A's weekly lock.
            ReportResponse firstB = b1.get(5, TimeUnit.SECONDS);
            ReportResponse secondB = b2.get(5, TimeUnit.SECONDS);
            assertThat(ownerLock.isDone()).as("A's lock transaction must still be active when B finishes").isFalse();
            assertThat(releaseOwnerLock.getCount()).isEqualTo(1);
            releaseOwnerLock.countDown();
            ownerLock.get(5, TimeUnit.SECONDS);
            ReportResponse firstA = a1.get(5, TimeUnit.SECONDS);
            ReportResponse secondA = a2.get(5, TimeUnit.SECONDS);

            assertLinearOwnerChain(OwnerTestContext.USER_ID, List.of(firstA.id(), secondA.id()),
                    Set.of(firstB.id(), secondB.id()));
            assertLinearOwnerChain(OwnerTestContext.OTHER_ID, List.of(firstB.id(), secondB.id()),
                    Set.of(firstA.id(), secondA.id()));
        } finally {
            releaseOwnerLock.countDown();
            startCallers.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            OwnerTestContext.use(OwnerTestContext.USER_ID);
        }
    }

    @Test
    void disablingAndReenablingAnAccountPreservesBusinessHistoryButNotItsOldSession() throws Exception {
        Cookie oldSession = OwnerTestContext.login(mvc);
        Cookie untouchedOldSession = OwnerTestContext.login(mvc);
        assertThat(untouchedOldSession.getValue()).isNotEqualTo(oldSession.getValue());
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        var project = projects.create(new CreateProjectRequest("停用保留项目-" + UUID.randomUUID()));
        var record = records.create(new CreateWorkRecordRequest(project.id(), "停用前记录", OCCURRED));
        var task = tasks.create(new CreateTaskRequest(project.id(), "停用前待办", "", null, null));
        when(captureAi.extract(any(), any(), any(), anyList())).thenAnswer(call -> {
            Instant referenceAt = call.getArgument(1);
            return new AiCaptureResult(
                    List.of(new AiCaptureResult.RecordItem("停用前 AI 记录", project.name(), referenceAt.toString())),
                    List.of(new AiCaptureResult.TaskItem("停用前 AI 待办", "", project.name(), null, "MEDIUM")));
        });
        InputResponse input = awaitInput(inputs.create(new CreateInputRequest(
                "disabled-history-" + UUID.randomUUID(), "停用前输入")).id());
        assertThat(input.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(input.records()).singleElement().extracting(InputResponse.GeneratedRecord::projectId)
                .isEqualTo(project.id());
        assertThat(input.tasks()).singleElement().extracting(InputResponse.GeneratedTask::projectId)
                .isEqualTo(project.id());
        when(reportAi.generate(any(), any(), anyList())).thenAnswer(call -> {
            var source = call.<List<com.aiworkbench.ai.ReportSourcePrompt>>getArgument(2).get(0);
            return new AiReportResult(List.of(new AiReportResult.Section("PROGRESS", List.of(
                    new AiReportResult.Bullet("已完成", List.of(source.id()))))));
        });
        ReportResponse report = awaitReport(reports.create(new CreateReportRequest(
                UUID.randomUUID(), "DAILY", DAY)).id());
        assertThat(report.status()).isEqualTo(ReportStatus.SUCCEEDED);
        assertThat(report.sources()).extracting(ReportResponse.Source::entityId).contains(record.id());

        OwnerTestContext.use(OwnerTestContext.ADMIN_ID);
        Cookie adminSession = OwnerTestContext.login(mvc, "owner-test-admin");
        mvc.perform(patch("/api/admin/users/{id}/enabled", OwnerTestContext.USER_ID)
                        .cookie(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(accounts.require(OwnerTestContext.USER_ID).enabled()).isFalse();
        assertThat(jdbc.queryForObject("SELECT user_id FROM reports WHERE id=?", UUID.class, report.id()))
                .isEqualTo(OwnerTestContext.USER_ID);
        assertThat(jdbc.queryForObject("SELECT user_id FROM capture_inputs WHERE id=?", UUID.class, input.id()))
                .isEqualTo(OwnerTestContext.USER_ID);
        mvc.perform(get("/api/records/{id}", record.id()).cookie(oldSession))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/admin/users/{id}/enabled", OwnerTestContext.USER_ID)
                        .cookie(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        assertThat(accounts.require(OwnerTestContext.USER_ID).authVersion()).isGreaterThan(0);
        mvc.perform(get("/api/records/{id}", record.id()).cookie(untouchedOldSession))
                .andExpect(status().isUnauthorized());

        Cookie freshSession = OwnerTestContext.login(mvc);
        for (String path : List.of("/api/records/" + record.id(), "/api/tasks/" + task.id(),
                "/api/inputs/" + input.id(), "/api/reports/" + report.id())) {
            mvc.perform(get(path).cookie(freshSession)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/projects").cookie(freshSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(project.id().toString()));
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        assertThat(projects.get(project.id()).id()).isEqualTo(project.id());
        assertThat(records.get(record.id()).project().id()).isEqualTo(project.id());
        assertThat(tasks.get(task.id()).project().id()).isEqualTo(project.id());
        InputResponse retainedInput = inputs.get(input.id());
        assertThat(retainedInput.records()).singleElement().extracting(InputResponse.GeneratedRecord::projectId)
                .isEqualTo(project.id());
        assertThat(retainedInput.tasks()).singleElement().extracting(InputResponse.GeneratedTask::projectId)
                .isEqualTo(project.id());
        assertThat(reports.get(report.id()).sources()).extracting(ReportResponse.Source::entityId)
                .contains(record.id());
    }

    @Test
    void failedRetryRecoveryAndRevertKeepThePersistedOwnerWithoutAnHttpContext() throws Exception {
        AtomicInteger failedPathCalls = new AtomicInteger();
        CountDownLatch retryEntered = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        when(captureAi.extract(any(), any(), any(), anyList())).thenAnswer(call -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            String content = call.getArgument(0);
            Instant referenceAt = call.getArgument(1);
            if ("failed path".equals(content)) {
                if (failedPathCalls.incrementAndGet() == 1) throw new IllegalStateException("synthetic failure");
                retryEntered.countDown();
                if (!releaseRetry.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("retry gate timed out");
            }
            return new AiCaptureResult(List.of(new AiCaptureResult.RecordItem(
                    content, null, referenceAt.toString())), List.of());
        });

        UUID failedId = inputs.create(new CreateInputRequest("owner-failed-" + UUID.randomUUID(), "failed path")).id();
        assertThat(awaitInput(failedId).status()).isEqualTo(InputStatus.FAILED);
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        assertNotFound(() -> inputs.get(failedId));
        assertNotFound(() -> inputs.retry(failedId));
        assertNotFound(() -> inputs.revert(failedId));

        OwnerTestContext.use(OwnerTestContext.USER_ID);
        inputs.retry(failedId);
        try {
            assertThat(retryEntered.await(5, TimeUnit.SECONDS)).isTrue();
            SecurityContextHolder.clearContext();
        } finally {
            releaseRetry.countDown();
        }
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        InputResponse succeeded = awaitInput(failedId);
        assertThat(succeeded.status()).isEqualTo(InputStatus.SUCCEEDED);
        UUID generatedId = succeeded.records().get(0).id();
        assertThat(jdbc.queryForObject("SELECT user_id FROM work_records WHERE id=?", UUID.class, generatedId))
                .isEqualTo(OwnerTestContext.USER_ID);
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        assertNotFound(() -> inputs.revert(failedId));
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        assertThat(inputs.revert(failedId).status()).isEqualTo(InputStatus.REVERTED);
        assertThat(jdbc.queryForObject("SELECT is_active FROM work_records WHERE id=?", Boolean.class, generatedId))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capture_generated_items WHERE input_id=?",
                Integer.class, failedId)).isEqualTo(1);

        var claim = inputPersistence.createOrGet("owner-recovery-" + UUID.randomUUID(), "recovery path",
                Instant.now(), java.time.ZoneId.of("Asia/Shanghai"));
        UUID recoveredId = claim.row().id();
        jdbc.update("UPDATE capture_inputs SET lease_expires_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), recoveredId);
        SecurityContextHolder.clearContext();
        assertThat(inputPersistence.recoverExpiredProcessing(Instant.now())).isEqualTo(1);
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        assertNotFound(() -> inputs.get(recoveredId));
        assertNotFound(() -> inputs.retry(recoveredId));
        assertNotFound(() -> inputs.revert(recoveredId));
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        assertThat(inputs.get(recoveredId).status()).isEqualTo(InputStatus.FAILED);
        inputs.retry(recoveredId);
        SecurityContextHolder.clearContext();
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        InputResponse recovered = awaitInput(recoveredId);
        assertThat(recovered.status()).isEqualTo(InputStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT user_id FROM work_records WHERE id=?", UUID.class,
                recovered.records().get(0).id())).isEqualTo(OwnerTestContext.USER_ID);
    }

    private record HttpOwner(Cookie session, UUID id, String role, String forbiddenMarker,
                             int allProjects, int activeProjects) {}

    private MvcResult performAs(HttpOwner owner, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(OwnerTestContext.authenticated(request, owner.session(), owner.id(), owner.role(), 0))
                .andExpect(status().isOk()).andReturn();
    }

    private void assertPage(HttpOwner owner, MockHttpServletRequestBuilder request,
                            int expectedTotal, int expectedItems) throws Exception {
        String body = performAs(owner, request).getResponse().getContentAsString();
        JsonNode page = json.readTree(body);
        assertThat(page.path("totalElements").asInt()).isEqualTo(expectedTotal);
        assertThat(page.path("items").size()).isEqualTo(expectedItems);
        assertThat(body).doesNotContain(owner.forbiddenMarker());
    }

    private void assertSameSafe404(HttpOwner owner, MockHttpServletRequestBuilder foreignRequest,
                                   MockHttpServletRequestBuilder missingRequest, String secret) throws Exception {
        String foreign = mvc.perform(OwnerTestContext.authenticated(
                        foreignRequest, owner.session(), owner.id(), owner.role(), 0))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(OwnerTestContext.authenticated(
                        missingRequest, owner.session(), owner.id(), owner.role(), 0))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        JsonNode foreignProblem = json.readTree(foreign);
        JsonNode missingProblem = json.readTree(missing);
        assertThat(foreignProblem.path("status").asInt()).isEqualTo(404);
        assertThat(foreignProblem.path("detail").asText()).isEqualTo(missingProblem.path("detail").asText());
        assertThat(foreign).doesNotContain(secret);
        assertThat(missing).doesNotContain(secret);
    }

    private Future<ReportResponse> submitWeekly(ExecutorService executor, UUID userId, LocalDate week,
            CountDownLatch ready, CountDownLatch start) {
        return executor.submit(OwnerTestContext.as(userId, () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("weekly caller gate timed out");
            return reports.create(new CreateReportRequest(UUID.randomUUID(), "WEEKLY", week));
        }));
    }

    private void assertLinearOwnerChain(UUID userId, List<UUID> ids, Set<UUID> otherOwnerIds) {
        OwnerTestContext.use(userId);
        List<ReportResponse> chain = ids.stream().map(this::awaitReport).toList();
        assertThat(chain).extracting(ReportResponse::id).doesNotHaveDuplicates();
        assertThat(chain.stream().filter(row -> row.previousReportId() == null)).hasSize(1);
        ReportResponse linked = chain.stream().filter(row -> row.previousReportId() != null).findFirst().orElseThrow();
        assertThat(ids).contains(linked.previousReportId());
        assertThat(otherOwnerIds).doesNotContain(linked.previousReportId());
        for (UUID id : ids) {
            assertThat(jdbc.queryForObject("SELECT user_id FROM reports WHERE id=?", UUID.class, id)).isEqualTo(userId);
        }
    }

    private InputResponse awaitInput(UUID id) {
        for (int attempt = 0; attempt < 120; attempt++) {
            InputResponse row = inputs.get(id);
            if (row.status() != InputStatus.PROCESSING) return row;
            sleep();
        }
        throw new AssertionError("input stayed PROCESSING");
    }

    private ReportResponse awaitReport(UUID id) {
        for (int attempt = 0; attempt < 120; attempt++) {
            ReportResponse row = reports.get(id);
            if (row.status() != ReportStatus.PROCESSING) return row;
            sleep();
        }
        throw new AssertionError("report stayed PROCESSING");
    }

    private void sleep() {
        try { Thread.sleep(25); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private void assertNotFound(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
