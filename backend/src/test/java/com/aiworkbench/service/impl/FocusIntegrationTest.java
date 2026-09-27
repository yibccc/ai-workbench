package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.ai.AiReportResult;
import com.aiworkbench.mapper.TaskMapper;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.TaskService;
import com.aiworkbench.service.WorkRecordService;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskVersionRequest;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.support.OwnerTestContext;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FocusIntegrationTest {
    @Autowired FocusStore store;
    @Autowired TaskMapper tasks;
    @Autowired ProjectService projects;
    @Autowired TaskService taskService;
    @Autowired WorkRecordService records;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkbenchEventHub events;
    @Autowired ReportMapper reportMapper;
    @Autowired ReportServiceImpl reports;
    private MutableClock clock;
    private FocusServiceImpl service;
    @BeforeEach void setup(){
        OwnerTestContext.ensureAccounts(jdbc);OwnerTestContext.use(OwnerTestContext.USER_ID);
        clock=new MutableClock(Instant.parse("2052-04-09T04:00:00Z"));
        service=new FocusServiceImpl(store,tasks,projects,records,events,ZoneId.of("Asia/Shanghai"),clock);
    }
    @Test void routineIsIdempotentEvenAfterSoftDeleteAndWeekdaysAreCanonical(){
        int weekday=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).getDayOfWeek().getValue();
        var routine=service.createRoutine(new SaveRoutine("每日复盘",null,List.of(weekday,weekday),25,null,null));
        assertThat(routine.weekdays()).containsExactly(weekday);
        var first=service.fillToday();assertThat(first.created()).hasSize(1);
        UUID taskId=first.created().get(0).id();
        jdbc.update("UPDATE todo_items SET deleted_at=? WHERE id=?",java.sql.Timestamp.from(clock.instant()),taskId);
        assertThat(service.fillToday().created()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE routine_id=?",Integer.class,routine.id())).isEqualTo(1);
    }
    @Test void archivedRoutineProjectIsBlockedButExistingSessionKeepsArchivedAssociation(){
        UUID archived=projects.create(new CreateProjectRequest("已归档模板项目")).id();
        UUID active=projects.create(new CreateProjectRequest("历史会话项目")).id();
        int weekday=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).getDayOfWeek().getValue();
        Routine blocked=service.createRoutine(new SaveRoutine("归档后不能生成",archived,List.of(weekday),25,null,null));
        Routine allowed=service.createRoutine(new SaveRoutine("正常生成",active,List.of(weekday),25,null,null));
        projects.archive(archived);
        FillToday fill=service.fillToday();
        assertThat(fill.blocked()).hasSize(1).first().satisfies(item->{
            assertThat(item.routineId()).isEqualTo(blocked.id());
            assertThat(item.reason()).contains("归档");
        });
        assertThat(fill.created()).hasSize(1).first().satisfies(item->{
            assertThat(item.routineId()).isEqualTo(allowed.id());
            assertThat(item.project().id()).isEqualTo(active);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE routine_id=?",Integer.class,blocked.id())).isZero();
        Session s=service.start(new Start(UUID.randomUUID(),"项目归档前已开工",null,active,25,10));
        projects.archive(active);
        clock.advance(Duration.ofSeconds(20));
        s=service.end(s.id(),new Version(s.version()));
        assertThat(s.phase()).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT project_id FROM work_records WHERE focus_session_id=?",UUID.class,s.id()))
                .isEqualTo(active);
        UUID sessionId=s.id();
        assertThat(records.list(LocalDate.of(2052,4,9))).filteredOn(item->sessionId.equals(item.sessionId()))
                .singleElement().satisfies(item->assertThat(item.project().id()).isEqualTo(active));
    }
    @Test void taskCompletionCycleDoesNotInvalidateFocusInvestment(){
        var task=taskService.create(new CreateTaskRequest(null,"伴随专注的待办","",null,TaskPriority.MEDIUM));
        Session s=service.start(new Start(UUID.randomUUID(),"推进待办",task.id(),null,25,10));
        clock.advance(Duration.ofSeconds(20));s=service.end(s.id(),new Version(s.version()));
        UUID sessionId=s.id();
        assertThat(taskService.get(task.id()).status().name()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION' AND is_active",Integer.class,task.id())).isZero();
        var completed=taskService.complete(task.id(),new CompleteTaskRequest(task.version(),"实际完成"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION' AND is_active",Integer.class,task.id())).isEqualTo(1);
        assertThat(taskService.complete(task.id(),new CompleteTaskRequest(completed.version(),"重复完成")).version()).isEqualTo(completed.version());
        var reopened=taskService.reopen(task.id(),new TaskVersionRequest(completed.version()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION' AND is_active",Integer.class,task.id())).isZero();
        completed=taskService.complete(task.id(),new CompleteTaskRequest(reopened.version(),"再次完成"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION' AND is_active",Integer.class,task.id())).isEqualTo(1);
        taskService.delete(task.id(),completed.version());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE todo_id=? AND source='TASK_COMPLETION' AND is_active",Integer.class,task.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=? AND source='FOCUS_SESSION' AND is_active",Integer.class,sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT focus_ms FROM work_records WHERE focus_session_id=?",Long.class,sessionId)).isEqualTo(20_000L);
        assertThat(taskService.events(task.id())).extracting(item->item.eventType())
                .contains("COMPLETED","REOPENED","DELETED");
    }
    @Test void weekendSkipMissingDaysAndRuleChangesKeepOccurrenceSnapshots(){
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalDate saturday=LocalDate.of(2052,4,9).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        clock.value=saturday.atTime(12,0).atZone(zone).toInstant();
        Routine routine=service.createRoutine(new SaveRoutine("旧标题",null,List.of(1),25,null,null));
        assertThat(service.fillToday().created()).isEmpty();
        LocalDate monday=saturday.plusDays(2);
        clock.value=monday.atTime(12,0).atZone(zone).toInstant();
        var old=service.fillToday().created().get(0);
        assertThat(old.occurrenceDate()).isEqualTo(monday);
        assertThat(old.title()).isEqualTo("旧标题");
        assertThat(old.defaultFocusDurationMinutes()).isEqualTo(25);
        routine=service.updateRoutine(routine.id(),new SaveRoutine("新标题",null,List.of(2),40,routine.version(),true));
        // Skip the next matching Tuesday entirely: entry is explicit and never catches it up.
        LocalDate laterTuesday=monday.plusDays(8);
        clock.value=laterTuesday.atTime(12,0).atZone(zone).toInstant();
        var fresh=service.fillToday().created().get(0);
        assertThat(fresh.occurrenceDate()).isEqualTo(laterTuesday);
        assertThat(fresh.title()).isEqualTo("新标题");
        assertThat(fresh.defaultFocusDurationMinutes()).isEqualTo(40);
        assertThat(tasks.findById(OwnerTestContext.USER_ID,old.id()).orElseThrow().toResponse())
                .satisfies(snapshot->{assertThat(snapshot.title()).isEqualTo("旧标题");
                    assertThat(snapshot.defaultFocusDurationMinutes()).isEqualTo(25);
                    assertThat(snapshot.occurrenceDate()).isEqualTo(monday);});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE routine_id=?",Integer.class,routine.id())).isEqualTo(2);
        routine=service.enableRoutine(routine.id(),new Version(routine.version()),false);
        clock.value=laterTuesday.plusWeeks(1).atTime(12,0).atZone(zone).toInstant();
        assertThat(service.fillToday().created()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM todo_items WHERE routine_id=?",Integer.class,routine.id())).isEqualTo(2);
    }
    @Test void exactTwentyFiveThirtyAndIdempotentSettlement(){
        Session current=service.start(new Start(UUID.randomUUID(),"写方案",null,null,25,10));
        while(!current.phase().equals("ENDED")){
            clock.advance(Duration.ofSeconds(15));current=visibleCheckpoint(current);
        }
        assertThat(current.focusMs()).isEqualTo(1_500_000);
        assertThat(current.breakMs()).isEqualTo(30_000);
        assertThat(Duration.between(current.startedAt(),current.endedAt()).toMillis()).isEqualTo(1_530_000);
        assertThat(service.end(current.id(),new Version(0L)).id()).isEqualTo(current.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=?",Integer.class,current.id())).isEqualTo(1);
        var record=records.list(LocalDate.of(2052,4,9)).stream().filter(r->r.sessionId()!=null).findFirst().orElseThrow();
        assertThat(record.focusMs()).isEqualTo(1_500_000);
    }
    @Test void backgroundGapContinuesFocusWithoutConfirmation(){
        Session s=service.start(new Start(UUID.randomUUID(),"分析",null,null,25,10));
        clock.advance(Duration.ofSeconds(61));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("RUNNING");assertThat(s.focusMs()).isEqualTo(61_000);
        clock.advance(Duration.ofSeconds(20));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.focusMs()).isEqualTo(81_000);
    }
    @Test void crossingMidnightSplitsNetTimeAndReportTreatsFocusAsProgress(){
        clock.value=Instant.parse("2052-04-09T15:59:50Z");
        Session s=service.start(new Start(UUID.randomUUID(),"跨日调查",null,null,25,10));
        clock.advance(Duration.ofSeconds(20));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        s=service.end(s.id(),new Version(s.version()));
        assertThat(s.focusMs()).isEqualTo(20_000);
        assertThat(jdbc.queryForList("SELECT business_date,focus_ms FROM work_records WHERE focus_session_id=? ORDER BY business_date",s.id()))
                .hasSize(2).extracting(row->((Number)row.get("focus_ms")).longValue()).containsExactly(10_000L,10_000L);
        var sources=reportMapper.findCandidateRecords(OwnerTestContext.USER_ID,
                LocalDate.of(2052,4,10).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                LocalDate.of(2052,4,11).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).sessionId()).isEqualTo(s.id());
        assertThat(sources.get(0).focusMs()).isEqualTo(10_000L);
        var invalid=new AiReportResult(List.of(new AiReportResult.Section("ACHIEVEMENTS",
                List.of(new AiReportResult.Bullet("完成调查",List.of(sources.get(0).id()))))));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->reports.validate(invalid,sources))
                .hasMessageContaining("事实边界");
        var valid=new AiReportResult(List.of(new AiReportResult.Section("PROGRESS",
                List.of(new AiReportResult.Bullet("投入调查",List.of(sources.get(0).id()))))));
        assertThat(reports.validate(valid,sources)).containsKey(com.aiworkbench.enums.ReportSectionType.PROGRESS);
    }
    @Test void targetWinsOverPauseAndSleepKeepsTrueEndInstant(){
        Session a=service.start(new Start(UUID.randomUUID(),"目标优先",null,null,1,10));
        clock.advance(Duration.ofSeconds(60));
        a=service.transition(a.id(),new Transition(a.version(),Action.PAUSE));
        assertThat(a.phase()).isEqualTo("ENDED");
        assertThat(a.endedAt()).isEqualTo(clock.instant());

        Session b=service.start(new Start(UUID.randomUUID(),"睡眠目标",null,null,1,10));
        Instant expected=b.startedAt().plusSeconds(60);
        clock.advance(Duration.ofSeconds(61));
        b=service.checkpoint(b.id(),new Checkpoint(b.version(),null,null));
        assertThat(b.phase()).isEqualTo("ENDED");
        assertThat(b.endedAt()).isEqualTo(expected);
        assertThat(b.focusMs()).isEqualTo(60_000);
    }
    @Test void fractionalCheckpointsDoNotLoseWholeMilliseconds(){
        Session s=service.start(new Start(UUID.randomUUID(),"毫秒守恒",null,null,1,10));
        for(int i=0;i<100;i++){
            clock.advance(Duration.ofNanos(1_250_000));
            s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        }
        assertThat(s.focusMs()).isEqualTo(125);
    }
    @Test void accountAndAdministratorCannotReadOrMutateAnotherOwnersSession(){
        Session s=service.start(new Start(UUID.randomUUID(),"私有会话",null,null,25,10));
        for(UUID other:List.of(OwnerTestContext.OTHER_ID,OwnerTestContext.ADMIN_ID)){
            OwnerTestContext.use(other);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.get(s.id()))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("404");
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.checkpoint(s.id(),new Checkpoint(s.version(),null,null)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("404");
        }
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        assertThat(service.get(s.id()).id()).isEqualTo(s.id());
    }
    @Test void elapsedBeyondSixtySecondsStillAccrues(){
        Session a=service.start(new Start(UUID.randomUUID(),"阈值",null,null,25,10));
        clock.advance(Duration.ofSeconds(60));
        a=service.checkpoint(a.id(),new Checkpoint(a.version(),null,null));
        assertThat(a.phase()).isEqualTo("RUNNING");assertThat(a.focusMs()).isEqualTo(60_000);
        clock.advance(Duration.ofMillis(60_001));
        a=service.checkpoint(a.id(),new Checkpoint(a.version(),null,null));
        assertThat(a.phase()).isEqualTo("RUNNING");
        assertThat(a.focusMs()).isEqualTo(120_001);
    }
    @Test void exactCrossDayTwentyFiveThirtyConservesDailyNetAndBreaks(){
        clock.value=Instant.parse("2052-04-09T15:50:00Z");
        Session s=service.start(new Start(UUID.randomUUID(),"跨夜方案",null,null,25,10));
        while(!s.phase().equals("ENDED")){
            clock.advance(Duration.ofSeconds(15));s=visibleCheckpoint(s);
        }
        var days=jdbc.queryForList("SELECT business_date,focus_ms,break_ms FROM work_records WHERE focus_session_id=? ORDER BY business_date",s.id());
        assertThat(days).hasSize(2);
        assertThat(((Number)days.get(0).get("focus_ms")).longValue()).isEqualTo(600_000);
        assertThat(((Number)days.get(1).get("focus_ms")).longValue()).isEqualTo(900_000);
        assertThat(((Number)days.get(0).get("break_ms")).longValue()).isZero();
        assertThat(((Number)days.get(1).get("break_ms")).longValue()).isEqualTo(30_000);
        assertThat(Duration.between(s.startedAt(),s.endedAt())).isEqualTo(Duration.ofSeconds(1530));
    }
    @Test void pausePreservesRemainingMicrobreakAndSkipCountsOnlyElapsedRest(){
        Session s=service.start(new Start(UUID.randomUUID(),"休息暂停",null,null,25,10));
        for(int i=0;i<10;i++){
            clock.advance(Duration.ofSeconds(60));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        }
        assertThat(s.phase()).isEqualTo("RUNNING");
        s=service.transition(s.id(),new Transition(s.version(),Action.BREAK_DUE));
        assertThat(s.phase()).isEqualTo("MICRO_BREAK");
        clock.advance(Duration.ofSeconds(5));s=service.transition(s.id(),new Transition(s.version(),Action.PAUSE));
        assertThat(s.breakRemainingMs()).isEqualTo(10_000);assertThat(s.breakMs()).isEqualTo(5_000);
        clock.advance(Duration.ofSeconds(30));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("PAUSED");assertThat(s.breakMs()).isEqualTo(5_000);
        s=service.transition(s.id(),new Transition(s.version(),Action.RESUME));
        assertThat(s.phase()).isEqualTo("MICRO_BREAK");assertThat(s.breakRemainingMs()).isEqualTo(10_000);
        s=service.transition(s.id(),new Transition(s.version(),Action.SKIP_BREAK));
        assertThat(s.phase()).isEqualTo("RUNNING");assertThat(s.breakMs()).isEqualTo(5_000);
        s=service.transition(s.id(),new Transition(s.version(),Action.DISMISS_REMINDERS));
        clock.advance(Duration.ofSeconds(60));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("RUNNING");assertThat(s.remindersDismissed()).isTrue();
    }
    @Test void hiddenLongGapAccruesFocusAndOneVisibleBreakWithoutReplay(){
        Session s=service.start(new Start(UUID.randomUUID(),"切出后工作",null,null,25,10));
        clock.advance(Duration.ofMinutes(21));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.focusMs()).isEqualTo(1_260_000);
        assertThat(s.breakMs()).isZero();assertThat(s.reminderOrdinal()).isZero();
        assertThat(s.phase()).isEqualTo("RUNNING");assertThat(s.nextBreakAtMs()).isEqualTo(600_000);
        s=service.transition(s.id(),new Transition(s.version(),Action.BREAK_DUE));
        assertThat(s.phase()).isEqualTo("MICRO_BREAK");assertThat(s.reminderOrdinal()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(15));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("RUNNING");assertThat(s.breakMs()).isEqualTo(15_000);
        assertThat(s.nextBreakAtMs()).isEqualTo(1_800_000);
        // A hidden gap that reaches the target ends without a late reminder.
        clock.advance(Duration.ofMinutes(5));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("ENDED");assertThat(s.focusMs()).isEqualTo(1_500_000);
        assertThat(s.reminderOrdinal()).isEqualTo(1);
    }
    @Test void sleepingThroughStartedBreakCountsOnlyFifteenSecondsThenFocus(){
        Session s=service.start(new Start(UUID.randomUUID(),"休息后睡眠",null,null,25,10));
        clock.advance(Duration.ofMinutes(10));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        s=service.transition(s.id(),new Transition(s.version(),Action.BREAK_DUE));
        clock.advance(Duration.ofMinutes(2));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("RUNNING");
        assertThat(s.breakMs()).isEqualTo(15_000);
        assertThat(s.focusMs()).isEqualTo(705_000);
        assertThat(s.nextBreakAtMs()).isEqualTo(1_200_000);
    }
    @Test void serverClockRollbackKeepsAnchorAndDoesNotDoubleCount(){
        Session s=service.start(new Start(UUID.randomUUID(),"时钟回拨",null,null,25,10));
        clock.advance(Duration.ofSeconds(20));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        Instant anchor=s.anchorAt();
        clock.advance(Duration.ofSeconds(-15));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.anchorAt()).isEqualTo(anchor);assertThat(s.focusMs()).isEqualTo(20_000);
        clock.advance(Duration.ofSeconds(25));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.focusMs()).isEqualTo(30_000);
        clock.advance(Duration.ofSeconds(-10));s=service.end(s.id(),new Version(s.version()));
        assertThat(s.endedAt()).isEqualTo(anchor.plusSeconds(10));
        assertThat(s.focusMs()).isEqualTo(30_000);
    }
    @Test void lateCheckpointStartsVisibleBreakWithoutClaimingUnseenRest(){
        Session s=service.start(new Start(UUID.randomUUID(),"迟到检查点",null,null,25,10));
        for(int i=0;i<9;i++){
            clock.advance(Duration.ofSeconds(60));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        }
        clock.advance(Duration.ofSeconds(50));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        clock.advance(Duration.ofSeconds(30));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        assertThat(s.phase()).isEqualTo("RUNNING");
        assertThat(s.focusMs()).isEqualTo(620_000);
        assertThat(s.breakMs()).isZero();assertThat(s.reminderOrdinal()).isZero();
        s=service.transition(s.id(),new Transition(s.version(),Action.BREAK_DUE));
        assertThat(s.phase()).isEqualTo("MICRO_BREAK");assertThat(s.breakRemainingMs()).isEqualTo(15_000);
    }
    @Test void settlementFailureRollsBackSessionAndRecordsAtomically(){
        Session s=service.start(new Start(UUID.randomUUID(),"故障回滚",null,null,25,10));
        clock.advance(Duration.ofSeconds(20));s=service.checkpoint(s.id(),new Checkpoint(s.version(),null,null));
        jdbc.execute("""
                CREATE FUNCTION reject_focus_test_record() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.source='FOCUS_SESSION' THEN RAISE EXCEPTION 'injected focus settlement failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_focus_test_record BEFORE INSERT ON work_records " +
                "FOR EACH ROW EXECUTE FUNCTION reject_focus_test_record()");
        jdbc.execute("SAVEPOINT before_focus_end");
        Session before=s;
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.end(before.id(),new Version(before.version())))
                .hasMessageContaining("injected focus settlement failure");
        jdbc.execute("ROLLBACK TO SAVEPOINT before_focus_end");
        assertThat(service.get(s.id()).phase()).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_records WHERE focus_session_id=?",Integer.class,s.id())).isZero();
    }
    private Session visibleCheckpoint(Session session){
        Session current=service.checkpoint(session.id(),new Checkpoint(session.version(),null,null));
        if(current.phase().equals("RUNNING")&&!current.remindersDismissed()
                &&current.focusMs()>=current.nextBreakAtMs()&&current.focusMs()<current.targetMs()){
            return service.transition(current.id(),new Transition(current.version(),Action.BREAK_DUE));
        }
        return current;
    }
    static final class MutableClock extends Clock {
        private Instant value;MutableClock(Instant value){this.value=value;}
        void advance(Duration duration){value=value.plus(duration);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
        @Override public Instant instant(){return value;}
    }
}
