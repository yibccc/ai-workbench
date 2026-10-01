package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.Start;
import com.aiworkbench.dto.focus.FocusModels.Session;
import com.aiworkbench.dto.focus.FocusModels.SaveRoutine;
import com.aiworkbench.dto.focus.FocusModels.Version;
import com.aiworkbench.dto.focus.FocusModels.Checkpoint;
import com.aiworkbench.dto.focus.FocusModels.Progress;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.mapper.TaskMapper;
import com.aiworkbench.service.FocusService;
import com.aiworkbench.service.TaskService;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.WorkRecordService;
import com.aiworkbench.support.OwnerTestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FocusConcurrencyIntegrationTest {
    @Autowired FocusService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FocusStore store;
    @Autowired TaskMapper tasks;
    @Autowired TaskService taskService;
    @Autowired ProjectService projects;
    @Autowired WorkRecordService records;
    @Autowired WorkbenchEventHub events;
    @Autowired PlatformTransactionManager transactionManager;
    private final java.util.List<UUID> routineIds=new java.util.ArrayList<>();
    private final java.util.List<UUID> taskIds=new java.util.ArrayList<>();
    @BeforeEach void setup(){OwnerTestContext.ensureAccounts(jdbc);OwnerTestContext.use(OwnerTestContext.USER_ID);dropFaultTrigger();clear();}
    @AfterEach void cleanup(){
        dropFaultTrigger();
        clear();
        taskIds.forEach(id->jdbc.update("DELETE FROM work_records WHERE user_id=? AND todo_id=?",OwnerTestContext.USER_ID,id));
        taskIds.forEach(id->jdbc.update("DELETE FROM task_events WHERE todo_id=?",id));
        taskIds.forEach(id->jdbc.update("DELETE FROM todo_items WHERE user_id=? AND id=?",OwnerTestContext.USER_ID,id));
        taskIds.clear();
        routineIds.forEach(id->jdbc.update("DELETE FROM todo_items WHERE user_id=? AND routine_id=?",OwnerTestContext.USER_ID,id));
        routineIds.forEach(id->jdbc.update("DELETE FROM focus_routines WHERE user_id=? AND id=?",OwnerTestContext.USER_ID,id));
        routineIds.clear();
    }
    private void clear(){
        jdbc.update("DELETE FROM work_records WHERE user_id=? AND source='FOCUS_SESSION'",OwnerTestContext.USER_ID);
        jdbc.update("DELETE FROM focus_intervals WHERE user_id=?",OwnerTestContext.USER_ID);
        jdbc.update("DELETE FROM focus_sessions WHERE user_id=?",OwnerTestContext.USER_ID);
    }
    private void dropFaultTrigger(){
        jdbc.execute("DROP TRIGGER IF EXISTS reject_focus_task_completion ON task_events");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_focus_task_completion()");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_focus_independent_settlement ON work_records");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_focus_independent_settlement()");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_focus_second_day_settlement ON work_records");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_focus_second_day_settlement()");
    }
    @Test void completionEventFailureRollsBackProgressTaskAndCompletionRecordAndAllowsRetry(){
        var task=taskService.create(new CreateTaskRequest(null,"专注保存事务回滚","",null,TaskPriority.MEDIUM));
        taskIds.add(task.id());
        var clock=new FocusIntegrationTest.MutableClock(Instant.parse("2052-04-09T04:00:00Z"));
        var focus=new FocusServiceImpl(store,tasks,taskService,projects,records,events,ZoneId.of("Asia/Shanghai"),clock);
        var tx=new TransactionTemplate(transactionManager);
        Session started=tx.execute(status->focus.start(new Start(UUID.randomUUID(),task.title(),task.id(),null,25,10)));
        clock.advance(Duration.ofSeconds(20));
        Session ended=tx.execute(status->focus.end(started.id(),new Version(started.version())));
        jdbc.execute("""
                CREATE FUNCTION reject_focus_task_completion() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.event_type='COMPLETED' AND NEW.payload->>'title'='专注保存事务回滚' THEN
                    RAISE EXCEPTION 'injected focus task completion failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_focus_task_completion BEFORE INSERT ON task_events " +
                "FOR EACH ROW EXECUTE FUNCTION reject_focus_task_completion()");
        assertThatThrownBy(()->tx.execute(status->focus.progress(ended.id(),new Progress(ended.version(),"已完成的实际进展"))))
                .hasMessageContaining("injected focus task completion failure");
        assertThat(focus.get(ended.id()).version()).isEqualTo(ended.version());
        assertThat(focus.get(ended.id()).progress()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT progress FROM work_records WHERE focus_session_id=?",String.class,ended.id())).isNull();
        assertThat(taskService.get(task.id()).status().name()).isEqualTo("PENDING");
        assertThat(taskService.get(task.id()).version()).isEqualTo(task.version());
        assertThat(taskService.events(task.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION'",Integer.class,task.id())).isZero();
        dropFaultTrigger();
        Session saved=tx.execute(status->focus.progress(ended.id(),new Progress(ended.version(),"已完成的实际进展")));
        assertThat(saved.progress()).isEqualTo(taskService.get(task.id()).completionResult());
        assertThat(taskService.get(task.id()).status().name()).isEqualTo("COMPLETED");
    }
    @Test void concurrentStartAllowsOnlyOneOpenSessionAndSameRequestIsIdempotent() throws Exception {
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<Object> first=pool.submit(call(one,ready,go));Future<Object> second=pool.submit(call(two,ready,go));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            Object a=first.get(10,TimeUnit.SECONDS),b=second.get(10,TimeUnit.SECONDS);
            assertThat(a instanceof Session ^ b instanceof Session).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM focus_sessions WHERE user_id=? AND phase<>'ENDED'",Integer.class,
                    OwnerTestContext.USER_ID)).isEqualTo(1);
            Session winner=(Session)(a instanceof Session?a:b);
            assertThat(service.start(new Start(winner.requestId(),"重试",null,null,25,10)).id()).isEqualTo(winner.id());
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentFillTodayCreatesOneOccurrenceAcrossIndependentTransactions() throws Exception {
        int weekday=LocalDate.now(ZoneId.of("Asia/Shanghai")).getDayOfWeek().getValue();
        UUID routineId=service.createRoutine(new SaveRoutine("并发每日任务",null,List.of(weekday),25,null,null)).id();
        routineIds.add(routineId);
        Cookie session=OwnerTestContext.login(mvc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Integer> fill=()->{
                ready.countDown();go.await(5,TimeUnit.SECONDS);
                var response=mvc.perform(OwnerTestContext.authenticated(post("/api/focus/routines/fill-today"),session)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isOk()).andReturn().getResponse();
                return json.readTree(response.getContentAsString()).path("created").size();
            };
            Future<Integer> first=pool.submit(fill),second=pool.submit(fill);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE user_id=? AND routine_id=?",Integer.class,
                    OwnerTestContext.USER_ID,routineId)).isEqualTo(1);
            assertThat(service.fillToday().created()).isEmpty();
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentEndReturnsOneSettlementAndNoDuplicateCredit() throws Exception {
        Session started=service.start(new Start(UUID.randomUUID(),"并发结束",null,null,25,10));
        while(System.currentTimeMillis()-started.startedAt().toEpochMilli()<20) Thread.sleep(2);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Session> finish=()->{
                ready.countDown();go.await(5,TimeUnit.SECONDS);
                return service.end(started.id(),new Version(started.version()));
            };
            Future<Session> first=pool.submit(OwnerTestContext.as(OwnerTestContext.USER_ID,finish));
            Future<Session> second=pool.submit(OwnerTestContext.as(OwnerTestContext.USER_ID,finish));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            Session a=first.get(10,TimeUnit.SECONDS),b=second.get(10,TimeUnit.SECONDS);
            assertThat(a.id()).isEqualTo(started.id());assertThat(b.id()).isEqualTo(started.id());
            assertThat(a.phase()).isEqualTo("ENDED");assertThat(b.phase()).isEqualTo("ENDED");
            assertThat(a.focusMs()).isEqualTo(b.focusMs()).isPositive();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE user_id=? AND focus_session_id=?",Integer.class,
                    OwnerTestContext.USER_ID,started.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT focus_ms FROM work_records WHERE user_id=? AND focus_session_id=?",Long.class,
                    OwnerTestContext.USER_ID,started.id())).isEqualTo(a.focusMs());
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentTargetEndedLeaseTakeoverHasOneWinnerAndKeepsCommittedSettlement() throws Exception {
        var clock=new FocusIntegrationTest.MutableClock(Instant.parse("2052-04-09T04:00:00Z"));
        var focus=new FocusServiceImpl(store,tasks,taskService,projects,records,events,ZoneId.of("Asia/Shanghai"),clock);
        var tx=new TransactionTemplate(transactionManager);
        Session started=tx.execute(status->focus.start(new Start(UUID.randomUUID(),"并发提醒接管",null,null,1,10)));
        UUID original=UUID.randomUUID(),firstTab=UUID.randomUUID(),secondTab=UUID.randomUUID();
        clock.advance(Duration.ofSeconds(60));
        Session ended=tx.execute(status->focus.checkpoint(started.id(),new Checkpoint(started.version(),original,null)));
        var intervals=jdbc.queryForList("SELECT * FROM focus_intervals WHERE session_id=? ORDER BY ordinal",ended.id());
        var settlement=jdbc.queryForList("SELECT * FROM work_records WHERE focus_session_id=?",ended.id());
        clock.advance(FocusServiceImpl.CONTROLLER_LEASE);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            java.util.function.Function<UUID,Callable<Object>> claim=tab->OwnerTestContext.as(OwnerTestContext.USER_ID,()->{
                ready.countDown();go.await(5,TimeUnit.SECONDS);
                try{return tx.execute(status->focus.checkpoint(ended.id(),new Checkpoint(ended.version(),tab,null)));}
                catch(org.springframework.web.server.ResponseStatusException ex){return ex;}
            });
            Future<Object> first=pool.submit(claim.apply(firstTab)),second=pool.submit(claim.apply(secondTab));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            Object a=first.get(10,TimeUnit.SECONDS),b=second.get(10,TimeUnit.SECONDS);
            assertThat(a instanceof Session ^ b instanceof Session).isTrue();
            assertThat((org.springframework.web.server.ResponseStatusException)(a instanceof Session?b:a))
                    .satisfies(ex->assertThat(ex.getStatusCode().value()).isEqualTo(409));
            Session winner=(Session)(a instanceof Session?a:b);
            assertThat(winner.controllerId()).isIn(firstTab,secondTab);
            assertThat(winner.controllerGeneration()).isEqualTo(ended.controllerGeneration()+1);
            assertThat(winner.controllerExpiresAt()).isEqualTo(clock.instant().plus(FocusServiceImpl.CONTROLLER_LEASE));
            assertThat(focus.get(ended.id())).isEqualTo(winner);
            UUID loser=winner.controllerId().equals(firstTab)?secondTab:firstTab;
            Session retry=tx.execute(status->focus.checkpoint(winner.id(),new Checkpoint(winner.version(),loser,null)));
            assertThat(retry).isEqualTo(winner);
            assertThat(jdbc.queryForList("SELECT * FROM focus_intervals WHERE session_id=? ORDER BY ordinal",ended.id())).isEqualTo(intervals);
            assertThat(jdbc.queryForList("SELECT * FROM work_records WHERE focus_session_id=?",ended.id())).isEqualTo(settlement);
        }finally{pool.shutdownNow();}
    }
    @Test void failedSettlementRollsBackIndependentServiceTransactionAndCanRetry() throws Exception {
        Session started=service.start(new Start(UUID.randomUUID(),"独立事务回滚",null,null,25,10));
        while(System.currentTimeMillis()-started.startedAt().toEpochMilli()<20) Thread.sleep(2);
        jdbc.execute("""
                CREATE FUNCTION reject_focus_independent_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.source='FOCUS_SESSION' THEN RAISE EXCEPTION 'injected independent focus failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_focus_independent_settlement BEFORE INSERT ON work_records " +
                "FOR EACH ROW EXECUTE FUNCTION reject_focus_independent_settlement()");
        assertThatThrownBy(()->service.end(started.id(),new Version(started.version())))
                .hasMessageContaining("injected independent focus failure");
        Session afterFailure=service.get(started.id());
        assertThat(afterFailure.phase()).isEqualTo("RUNNING");
        assertThat(afterFailure.version()).isEqualTo(started.version());
        assertThat(afterFailure.focusMs()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM focus_intervals WHERE session_id=?",Integer.class,started.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=?",Integer.class,started.id())).isZero();
        dropFaultTrigger();
        Session settled=service.end(started.id(),new Version(afterFailure.version()));
        assertThat(settled.phase()).isEqualTo("ENDED");
        assertThat(settled.focusMs()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=?",Integer.class,started.id())).isEqualTo(1);
    }
    @Test void secondDayInsertFailureRollsBackFirstDaySliceAndSessionEnd() {
        var clock=new FocusIntegrationTest.MutableClock(Instant.parse("2052-04-09T15:59:50Z"));
        var focus=new FocusServiceImpl(store,tasks,taskService,projects,records,events,ZoneId.of("Asia/Shanghai"),clock);
        var tx=new TransactionTemplate(transactionManager);
        Session started=tx.execute(status->focus.start(new Start(UUID.randomUUID(),"跨日结算回滚",null,null,25,10)));
        clock.advance(Duration.ofSeconds(20));
        Session credited=tx.execute(status->focus.checkpoint(started.id(),new Checkpoint(started.version(),null,null)));
        assertThat(credited.focusMs()).isEqualTo(20_000);
        jdbc.execute("""
                CREATE FUNCTION reject_focus_second_day_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.source='FOCUS_SESSION' AND NEW.business_date=DATE '2052-04-10' THEN
                    RAISE EXCEPTION 'injected second day focus failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_focus_second_day_settlement BEFORE INSERT ON work_records " +
                "FOR EACH ROW EXECUTE FUNCTION reject_focus_second_day_settlement()");
        assertThatThrownBy(()->tx.execute(status->focus.end(started.id(),new Version(credited.version()))))
                .hasMessageContaining("injected second day focus failure");
        Session afterFailure=focus.get(started.id());
        assertThat(afterFailure.phase()).isEqualTo("RUNNING");
        assertThat(afterFailure.version()).isEqualTo(credited.version());
        assertThat(afterFailure.focusMs()).isEqualTo(20_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=?",Integer.class,started.id())).isZero();
        dropFaultTrigger();
        Session settled=tx.execute(status->focus.end(started.id(),new Version(afterFailure.version())));
        assertThat(settled.phase()).isEqualTo("ENDED");
        assertThat(jdbc.queryForList("SELECT business_date,focus_ms FROM work_records WHERE focus_session_id=? ORDER BY business_date",started.id()))
                .hasSize(2).extracting(row->((Number)row.get("focus_ms")).longValue()).containsExactly(10_000L,10_000L);
    }
    private Callable<Object> call(UUID requestId,CountDownLatch ready,CountDownLatch go){
        return OwnerTestContext.as(OwnerTestContext.USER_ID,()->{
            ready.countDown();go.await(5,TimeUnit.SECONDS);
            try{return service.start(new Start(requestId,"并发",null,null,25,10));}
            catch(org.springframework.web.server.ResponseStatusException exception){return exception;}
        });
    }
}
