package com.aiworkbench.interview;

import com.aiworkbench.ai.*;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.dto.resume.ResumeModels.Mode;
import com.aiworkbench.entity.interview.InterviewRows.JobRow;
import com.aiworkbench.exception.InterviewException;
import com.aiworkbench.mapper.InterviewMapper;
import com.aiworkbench.service.*;
import com.aiworkbench.service.impl.InterviewPersistenceService;
import com.aiworkbench.support.OwnerTestContext;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real PG locks/receipts/terminal fencing; controlled dispatch preserves observable queued state. */
@SpringBootTest
class InterviewPersistenceIntegrationTest {
    @Autowired InterviewService service;
    @Autowired ResumeService resumes;
    @Autowired InterviewPersistenceService persistence;
    @Autowired InterviewMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean InterviewJobRunner runner;
    @MockitoSpyBean InterviewAiGateway gateway;
    UUID owner,other;
    @TempDir Path scratch;
    @BeforeEach void setup() {
        owner=account(); other=account(); OwnerTestContext.use(owner); doNothing().when(runner).dispatch(anyList());
        doAnswer(call->{ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return call.callRealMethod(); }).when(gateway).generate(any());
        doAnswer(call->{ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return call.callRealMethod(); }).when(gateway).evaluate(any());
    }
    UUID account() { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES(?,?,?,'USER')",id,"interview-"+id,"synthetic hash"); return id; }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS interview_slow_question ON interview_questions");
        jdbc.execute("DROP FUNCTION IF EXISTS interview_slow_question()");
        reset(runner,gateway);
        for(UUID id:List.of(owner,other)) {
            for(String table:List.of("interview_operation_receipts","interview_ai_jobs","interview_evaluations","interview_answers","interview_questions","interview_sessions","interview_jd_analyses","resume_write_receipts","user_resumes","resume_objects")) jdbc.update("DELETE FROM "+table+" WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
        }
        SecurityContextHolder.clearContext();
    }
    Create request(UUID id,int count,boolean resume,long version,String jd,UUID analysis) { return new Create(id,Direction.JAVA_BACKEND,Difficulty.MID,count,resume,resume?version:null,jd,analysis); }
    Receipt create(int count) { return service.create(request(UUID.randomUUID(),count,false,0,null,null)); }
    void drive(UUID session) {
        for(UUID id:jdbc.queryForList("SELECT id FROM interview_ai_jobs WHERE user_id=? AND session_id=? AND status='PENDING' ORDER BY created_at,id",UUID.class,owner,session)) runner.process(mapper.job(owner,id).orElseThrow());
    }
    void driveJd(UUID analysis) {
        for(UUID id:jdbc.queryForList("SELECT id FROM interview_ai_jobs WHERE user_id=? AND jd_id=? AND status='PENDING'",UUID.class,owner,analysis)) runner.process(mapper.job(owner,id).orElseThrow());
    }
    JobRow generation(UUID id) { UUID job=jdbc.queryForObject("SELECT id FROM interview_ai_jobs WHERE user_id=? AND session_id=? AND kind='GENERATE' ORDER BY created_at DESC LIMIT 1",UUID.class,owner,id); return mapper.job(owner,job).orElseThrow(); }
    Receipt submit(UUID id,String text) { var session=service.session(id); return service.submit(id,session.currentTurn(),new AnswerOperation(session.version(),UUID.randomUUID(),text,null)); }
    @Test void generationAndSnapshotAreFixedCreateReplayDoesNotRecreateOrCallOnReadAndDeletedReceiptDoesNotResurrect() {
        resumes.save(new com.aiworkbench.dto.resume.ResumeModels.Save(Mode.PASTE,"resume A",0L,UUID.randomUUID()));
        var input=request(UUID.randomUUID(),3,true,1,null,null); var created=service.create(input);
        assertThat(service.session(created.sessionId()).generationStatus()).isEqualTo("PENDING");
        assertThat(generation(created.sessionId()).leaseExpiresAt()).isNull();
        resumes.save(new com.aiworkbench.dto.resume.ResumeModels.Save(Mode.PASTE,"resume B",1L,UUID.randomUUID()));
        assertThat(service.create(input)).isEqualTo(created); drive(created.sessionId());
        var stored=service.session(created.sessionId()); assertThat(stored.resumeSnapshot()).isEqualTo("resume A"); assertThat(stored.questions()).hasSize(6);
        for(int i=0;i<3;i++) { service.session(created.sessionId()); service.page(0,5); service.report(created.sessionId()); }
        verify(gateway,times(1)).generate(any()); verify(gateway,never()).evaluate(any());
        service.delete(created.sessionId()); assertThat(service.create(input).state()).isEqualTo("DELETED");
        assertThatThrownBy(()->service.session(created.sessionId())).isInstanceOf(InterviewException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_questions WHERE session_id=?",Integer.class,created.sessionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT resume_text FROM interview_sessions WHERE id=?",String.class,created.sessionId())).isNull();
        assertThat(resumes.current().markdownText()).isEqualTo("resume B");
    }
    @Test void jdExactHashDirectionOwnerAndSuccessAreBoundButSuccessAnalysisCanBeCopiedToMultipleSessions() {
        var parsed=service.parseJd(new ParseJd(Direction.JAVA_BACKEND,"原始JD\r\n",UUID.randomUUID())); driveJd(parsed.id());
        assertThat(service.jd(parsed.id()).status()).isEqualTo("SUCCEEDED");
        var first=service.create(request(UUID.randomUUID(),3,false,0,"原始JD\r\n",parsed.id()));
        var second=service.create(request(UUID.randomUUID(),5,false,0,"原始JD\r\n",parsed.id()));
        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThatThrownBy(()->service.create(request(UUID.randomUUID(),3,false,0,"原始JD\n",parsed.id()))).isInstanceOf(InterviewException.class);
        assertThatThrownBy(()->service.create(new Create(UUID.randomUUID(),Direction.REACT_FRONTEND,Difficulty.MID,3,false,null,"原始JD\r\n",parsed.id()))).isInstanceOf(InterviewException.class);
        OwnerTestContext.use(other); assertThatThrownBy(()->service.jd(parsed.id())).isInstanceOf(InterviewException.class); OwnerTestContext.use(owner);
        service.deleteJd(parsed.id()); assertThat(service.session(first.sessionId()).jdText()).isEqualTo("原始JD\r\n");
        assertThat(jdbc.queryForObject("SELECT jd_text FROM interview_jd_analyses WHERE id=?",String.class,parsed.id())).isNull();
    }
    @Test void operationReceiptsBindCanonicalPayloadAndOwnerAndCreateReplaySurvivesJdRemoval() {
        var jd=service.parseJd(new ParseJd(Direction.JAVA_BACKEND,"exact JD",UUID.randomUUID())); driveJd(jd.id());
        UUID createRequest=UUID.randomUUID(); var input=request(createRequest,3,false,0,"exact JD",jd.id());
        var created=service.create(input);
        assertThatThrownBy(()->service.create(request(createRequest,5,false,0,"exact JD",jd.id())))
                .isInstanceOfSatisfying(InterviewException.class,e->assertThat(e.code()).isEqualTo("INTERVIEW_REQUEST_CONFLICT"));
        OwnerTestContext.use(other);
        var independentlyOwned=service.create(request(createRequest,3,false,0,null,null));
        assertThat(independentlyOwned.sessionId()).isNotEqualTo(created.sessionId()); OwnerTestContext.use(owner);
        service.deleteJd(jd.id()); assertThat(service.create(input)).isEqualTo(created); drive(created.sessionId());
        var current=service.session(created.sessionId()); UUID answerRequest=UUID.randomUUID();
        var submittedInput=new AnswerOperation(current.version(),answerRequest,"locked answer",null);
        var submitted=service.submit(created.sessionId(),0,submittedInput);
        assertThat(service.submit(created.sessionId(),0,submittedInput)).isEqualTo(submitted);
        assertThatThrownBy(()->service.submit(created.sessionId(),0,new AnswerOperation(current.version(),answerRequest,"different answer",null)))
                .isInstanceOfSatisfying(InterviewException.class,e->assertThat(e.code()).isEqualTo("INTERVIEW_REQUEST_CONFLICT"));
        assertThat(service.session(created.sessionId()).currentTurn()).isEqualTo(1);
        assertThat(service.session(created.sessionId()).answers().get(0).answerText()).isEqualTo("locked answer");
        var completeInput=new Complete(service.session(created.sessionId()).version(),UUID.randomUUID(),true);
        var completed=service.complete(created.sessionId(),completeInput); drive(created.sessionId());
        assertThat(service.complete(created.sessionId(),completeInput)).isEqualTo(completed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_ai_jobs WHERE session_id=? AND kind='EVALUATE'",Integer.class,created.sessionId())).isEqualTo(1);
        verify(gateway,times(1)).evaluate(any());
    }
    @Test void draftDoesNotAdvanceConcurrentSubmitAndLostAckAdvanceExactlyOnce() throws Exception {
        var id=create(3).sessionId(); drive(id); var before=service.session(id);
        service.draft(id,new AnswerOperation(before.version(),UUID.randomUUID(),"draft",0));
        assertThat(service.session(id).currentTurn()).isZero(); assertThat(service.session(id).submittedCount()).isZero();
        long expected=service.session(id).version(); var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
        var requests=List.of(new AnswerOperation(expected,UUID.randomUUID(),"one",null),new AnswerOperation(expected,UUID.randomUUID(),"two",null));
        try {
            List<Future<Receipt>> outcomes=new ArrayList<>();
            for(var request:requests) outcomes.add(pool.submit(OwnerTestContext.as(owner,()->{ barrier.await(); try { return service.submit(id,0,request); } catch(InterviewException conflict) { assertThat(conflict.getStatusCode().value()).isEqualTo(409); return null; } })));
            var one=outcomes.get(0).get(10,TimeUnit.SECONDS); var two=outcomes.get(1).get(10,TimeUnit.SECONDS);
            assertThat(Arrays.asList(one,two).stream().filter(Objects::nonNull).count()).isEqualTo(1);
            int winner=one==null?1:0; assertThat(service.submit(id,0,requests.get(winner))).isEqualTo(winner==0?one:two);
            assertThat(service.session(id).currentTurn()).isEqualTo(1); assertThat(service.session(id).submittedCount()).isEqualTo(1);
            assertThatThrownBy(()->service.draft(id,new AnswerOperation(service.session(id).version(),UUID.randomUUID(),"late",0))).isInstanceOf(InterviewException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=? AND status='SUBMITTED'",Integer.class,id)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void emptySubmittedIsScoredUnsubmittedBecomesZeroAndBackendRoundsEqualMeanTwoPlaces() {
        var id=create(3).sessionId(); drive(id); submit(id,""); submit(id,"answered");
        var before=service.session(id); service.complete(id,new Complete(before.version(),UUID.randomUUID(),true));
        assertThat(service.report(id).totalScore()).isNull(); assertThat(service.session(id).answers()).extracting(Answer::status).containsExactly("SUBMITTED","SUBMITTED","UNANSWERED","UNANSWERED","UNANSWERED","UNANSWERED");
        drive(id); var report=service.report(id);
        assertThat(report.totalScore()).isEqualByComparingTo("13.35"); assertThat(report.turns().get(0).status()).isEqualTo("SCORED");
        assertThat(report.turns().get(0).score()).isZero(); assertThat(report.turns().get(2).status()).isEqualTo("UNANSWERED");
        verify(gateway,times(1)).evaluate(any());
    }
    @Test void allUnansweredUsesNoModelAndFinalSubmitVersusEarlyCompleteFreezesExactlyOneEvaluationSet() throws Exception {
        var empty=create(3).sessionId(); drive(empty); var current=service.session(empty); service.complete(empty,new Complete(current.version(),UUID.randomUUID(),true));
        assertThat(service.report(empty).totalScore()).isEqualByComparingTo("0.00"); verify(gateway,never()).evaluate(any());
        var id=create(3).sessionId(); drive(id); for(int i=0;i<5;i++) submit(id,"text");
        long version=service.session(id).version(); var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var last=pool.submit(OwnerTestContext.as(owner,()->{barrier.await(); try { service.submit(id,5,new AnswerOperation(version,UUID.randomUUID(),"last",null)); return true; } catch(InterviewException conflict) { return false; }}));
            var early=pool.submit(OwnerTestContext.as(owner,()->{barrier.await(); try { service.complete(id,new Complete(version,UUID.randomUUID(),true)); return true; } catch(InterviewException conflict) { return false; }}));
            assertThat(List.of(last.get(10,TimeUnit.SECONDS),early.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_evaluations WHERE session_id=?",Integer.class,id)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_ai_jobs WHERE session_id=? AND kind='EVALUATE'",Integer.class,id)).isEqualTo(3);
        } finally { pool.shutdownNow(); }
    }
    @Test void failureHasNullTotalManualRetryOnlyRunsFailedGroupsAndKeepsSuccessfulResults() {
        var id=create(3).sessionId(); drive(id); for(int i=0;i<4;i++) submit(id,"text"); var s=service.session(id); service.complete(id,new Complete(s.version(),UUID.randomUUID(),true));
        doAnswer(call->{ var input=call.getArgument(0,GroupInput.class); if(input.answers().get(0).turnIndex()==2) throw new InterviewModelException("MODEL_OUTPUT_INVALID"); return call.callRealMethod(); }).when(gateway).evaluate(any());
        drive(id); assertThat(service.report(id).totalScore()).isNull(); assertThat(service.report(id).turns().get(2).score()).isNull();
        String fixed=jdbc.queryForObject("SELECT result_json FROM interview_evaluations WHERE session_id=? AND main_index=0",String.class,id);
        doAnswer(call->call.callRealMethod()).when(gateway).evaluate(any());
        service.retry(id,new VersionOperation(service.session(id).version(),UUID.randomUUID()),false); drive(id);
        assertThat(service.report(id).totalScore()).isEqualByComparingTo("53.42");
        assertThat(jdbc.queryForObject("SELECT result_json FROM interview_evaluations WHERE session_id=? AND main_index=0",String.class,id)).isEqualTo(fixed);
        verify(gateway,times(3)).evaluate(any());
    }
    @Test void unansweredTurnInMixedGroupRemainsZeroWhileSubmittedTurnWaitsFailsAndRetries() {
        var id=create(3).sessionId(); drive(id); submit(id,"text");
        service.complete(id,new Complete(service.session(id).version(),UUID.randomUUID(),true));
        var pending=service.report(id);
        assertThat(pending.totalScore()).isNull();
        assertThat(pending.turns().get(0).status()).isEqualTo("NOT_EVALUATED");
        assertThat(pending.turns().get(0).score()).isNull();
        assertThat(pending.turns().subList(1,6)).allSatisfy(turn->{
            assertThat(turn.status()).isEqualTo("UNANSWERED"); assertThat(turn.score()).isZero();
        });
        doThrow(new InterviewModelException("MODEL_OUTPUT_INVALID")).when(gateway).evaluate(any());
        drive(id); var failed=service.report(id);
        assertThat(failed.groups().get(0).status()).isEqualTo("FAILED");
        assertThat(failed.totalScore()).isNull();
        assertThat(failed.turns().get(0).status()).isEqualTo("NOT_EVALUATED");
        assertThat(failed.turns().get(0).score()).isNull();
        assertThat(failed.turns().get(1).status()).isEqualTo("UNANSWERED");
        assertThat(failed.turns().get(1).score()).isZero();
        for(int i=0;i<3;i++) service.report(id);
        verify(gateway,times(1)).evaluate(any());
        doAnswer(call->call.callRealMethod()).when(gateway).evaluate(any());
        service.retry(id,new VersionOperation(service.session(id).version(),UUID.randomUUID()),false); drive(id);
        var succeeded=service.report(id);
        assertThat(succeeded.totalScore()).isEqualByComparingTo("13.35");
        assertThat(succeeded.turns().get(0).status()).isEqualTo("SCORED");
        assertThat(succeeded.turns().get(1)).isEqualTo(pending.turns().get(1));
        verify(gateway,times(2)).evaluate(any());
    }
    @Test void expiredQueuedAndRunningJobsFailWithoutModelRetryAndOldTokenCannotInsertQuestionRows() {
        var queued=create(3).sessionId(); var pending=generation(queued);
        jdbc.update("UPDATE interview_ai_jobs SET queue_deadline=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),pending.id()); runner.recover();
        assertThat(service.session(queued).generationStatus()).isEqualTo("FAILED"); verify(gateway,never()).generate(any());
        var running=create(3).sessionId(); var attempt=persistence.claim(generation(running)); assertThat(attempt.token()).isNotNull();
        assertThat(persistence.expired()).noneMatch(j->j.id().equals(attempt.id()));
        jdbc.update("UPDATE interview_ai_jobs SET lease_expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),attempt.id()); runner.recover();
        service.retry(running,new VersionOperation(service.session(running).version(),UUID.randomUUID()),true);
        var newer=persistence.claim(generation(running)); var output=gateway.generate(persistence.read(newer.payloadJson(),QuestionInput.class));
        assertThat(persistence.finish(attempt,output)).isFalse(); assertThat(persistence.fail(attempt,"MODEL_UNAVAILABLE")).isFalse();
        assertThat(persistence.finish(newer,output)).isTrue(); assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_questions WHERE session_id=?",Integer.class,running)).isEqualTo(6);
    }
    @Test void deleteDuringRunningGenerationOrEvaluationErasesDataAndFencesLateCompletionWithoutAffectingOtherSession() {
        var gone=create(3).sessionId(); var attempt=persistence.claim(generation(gone)); var otherSession=create(3).sessionId();
        service.delete(gone); var output=gateway.generate(persistence.read(attempt.payloadJson(),QuestionInput.class)); assertThat(persistence.finish(attempt,output)).isFalse();
        assertThat(service.session(otherSession).generationStatus()).isEqualTo("PENDING"); drive(otherSession); submit(otherSession,"text");
        service.complete(otherSession,new Complete(service.session(otherSession).version(),UUID.randomUUID(),true));
        UUID jobId=jdbc.queryForObject("SELECT id FROM interview_ai_jobs WHERE session_id=? AND kind='EVALUATE'",UUID.class,otherSession);
        var evaluation=persistence.claim(mapper.job(owner,jobId).orElseThrow()); service.delete(otherSession);
        var result=gateway.evaluate(persistence.read(evaluation.payloadJson(),GroupInput.class)); assertThat(persistence.finish(evaluation,result)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_ai_jobs WHERE user_id=? AND payload_json IS NOT NULL",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_answers WHERE user_id=?",Integer.class,owner)).isZero();
    }
    @Test void maximumTwentyMainResumeJdAndFortyFiveThousandPointAnswersDoNotTruncate() {
        String resume="😀".repeat(20000),jd="😀".repeat(10000),answer="😀".repeat(5000);
        resumes.save(new com.aiworkbench.dto.resume.ResumeModels.Save(Mode.PASTE,resume,0L,UUID.randomUUID()));
        var analysis=service.parseJd(new ParseJd(Direction.JAVA_BACKEND,jd,UUID.randomUUID())); driveJd(analysis.id());
        var id=service.create(request(UUID.randomUUID(),20,true,1,jd,analysis.id())).sessionId(); drive(id);
        for(int i=0;i<40;i++) submit(id,answer); drive(id);
        assertThat(service.session(id).questions()).hasSize(40); assertThat(service.session(id).answers()).hasSize(40).allSatisfy(a->assertThat(a.answerText()).isEqualTo(answer));
        assertThat(service.session(id).resumeSnapshot()).isEqualTo(resume); assertThat(service.session(id).jdText()).isEqualTo(jd);
        assertThat(service.report(id).totalScore()).isEqualByComparingTo("80.13"); verify(gateway,times(20)).evaluate(any());
    }
    @Test void queueRejectionBadQuestionSetAndCancelledJdAreDurableFailuresWithoutPartialRows() {
        var rejected=create(3).sessionId(); persistence.rejected(generation(rejected));
        assertThat(service.session(rejected).safeFailureCode()).isEqualTo("QUEUE_UNAVAILABLE");
        var malformed=create(3).sessionId(); doReturn(new QuestionSet(List.of())).when(gateway).generate(any()); drive(malformed);
        assertThat(service.session(malformed).generationStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_questions WHERE session_id=?",Integer.class,malformed)).isZero();
        var jd=service.parseJd(new ParseJd(Direction.AGENT_DEVELOPMENT,"private raw",UUID.randomUUID()));
        UUID job=jdbc.queryForObject("SELECT id FROM interview_ai_jobs WHERE jd_id=?",UUID.class,jd.id());
        var attempt=persistence.claim(mapper.job(owner,job).orElseThrow()); service.deleteJd(jd.id());
        assertThat(persistence.finish(attempt,new JdResult(true,"late summary",List.of("point")))).isFalse();
        assertThat(jdbc.queryForObject("SELECT jd_text FROM interview_jd_analyses WHERE id=?",String.class,jd.id())).isNull();
    }
    @Test void leaseExpiresDuringChildInsertsLastDatabaseWallClockFenceRollsBackWholeResult() {
        var id=create(3).sessionId(); var attempt=persistence.claim(generation(id));
        var output=gateway.generate(persistence.read(attempt.payloadJson(),QuestionInput.class));
        jdbc.execute("CREATE FUNCTION interview_slow_question() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.075); RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER interview_slow_question BEFORE INSERT ON interview_questions FOR EACH ROW EXECUTE FUNCTION interview_slow_question()");
        jdbc.update("UPDATE interview_ai_jobs SET lease_expires_at=? WHERE id=?",Timestamp.from(Instant.now().plusMillis(300)),attempt.id());
        assertThatThrownBy(()->persistence.finish(attempt,output)).isInstanceOfSatisfying(InterviewException.class,e->assertThat(e.code()).isEqualTo("EXECUTION_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_questions WHERE session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=?",Integer.class,id)).isZero();
        assertThat(service.session(id).generationStatus()).isEqualTo("PROCESSING"); runner.recover(); assertThat(service.session(id).generationStatus()).isEqualTo("FAILED");
    }
    @Test void actualKilledProcessingJvmNewJvmRecoversFailedWithZeroModelCallsAndManualRetryCanProceed() throws Exception {
        var id=create(3).sessionId(); var offered=generation(id); Path claimed=scratch.resolve("claimed.txt");
        Process process=harness("claim",offered.id(),claimed);
        try {
            await(process,claimed); assertThat(jdbc.queryForObject("SELECT status FROM interview_ai_jobs WHERE id=?",String.class,offered.id())).isEqualTo("PROCESSING");
        } finally { process.destroyForcibly(); assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue(); }
        jdbc.update("UPDATE interview_ai_jobs SET lease_expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),offered.id());
        Path recovered=scratch.resolve("recovered.txt"); Process restarted=harness("recover",offered.id(),recovered);
        try { await(restarted,recovered); assertThat(restarted.waitFor(20,TimeUnit.SECONDS)).isTrue(); assertThat(restarted.exitValue()).isZero(); }
        finally { if(restarted.isAlive()) { restarted.destroyForcibly(); restarted.waitFor(15,TimeUnit.SECONDS); } }
        assertThat(Files.readString(recovered)).isEqualTo("FAILED:0"); assertThat(service.session(id).generationStatus()).isEqualTo("FAILED");
        verify(gateway,never()).generate(any()); service.retry(id,new VersionOperation(service.session(id).version(),UUID.randomUUID()),true); drive(id);
        assertThat(service.session(id).questions()).hasSize(6); verify(gateway,times(1)).generate(any());
    }
    Process harness(String operation,UUID job,Path marker) throws IOException {
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(executable,"-Xms32m","-Xmx192m","-XX:MaxMetaspaceSize=160m","-XX:ActiveProcessorCount=2","-XX:+UseSerialGC","-XX:TieredStopAtLevel=1","-XX:ReservedCodeCacheSize=48m",
                "-cp",classpath,InterviewCrashHarness.class.getName(),operation,owner.toString(),job.toString(),marker.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(key->key.matches("(?i)^(RUSTFS_|AWS_|DEEPSEEK_).*$")); builder.environment().put("DEEPSEEK_API_KEY",""); return builder.start();
    }
    void await(Process process,Path marker) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(!Files.exists(marker) && process.isAlive() && System.nanoTime()<deadline) Thread.sleep(100);
        assertThat(Files.exists(marker)).as("real processing child JVM reached synchronization point").isTrue();
    }
}
