package com.aiworkbench.service.impl;

import com.aiworkbench.ai.*;
import com.aiworkbench.common.*;
import com.aiworkbench.config.*;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.entity.interview.InterviewRows.*;
import com.aiworkbench.exception.InterviewException;
import com.aiworkbench.mapper.InterviewMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** All domain transitions are short transactions. Lock owner, subject, children/job, receipt. */
@Service
public class InterviewPersistenceService {
    private final InterviewMapper mapper;
    private final ResumePersistenceService resumes;
    private final ObjectMapper json;
    private final InterviewAiProperties execution;
    private final DeepSeekProperties model;
    public InterviewPersistenceService(InterviewMapper mapper,ResumePersistenceService resumes,ObjectMapper json,InterviewAiProperties execution,DeepSeekProperties model) {
        this.mapper=mapper; this.resumes=resumes; this.json=json; this.execution=execution; this.model=model;
    }
    @Transactional
    public Operation create(UUID owner,Create request,String payload) {
        lockOwner(owner); var prior=prior(owner,"CREATE",request.requestId(),payload);
        if(prior!=null) return new Operation(receipt(prior),List.of());
        var snapshot=request.useCurrentResume()?resumes.snapshotForInterview(owner,request.expectedResumeVersion()):null;
        if(snapshot!=null && !snapshot.exists()) throw conflict("当前没有简历，请关闭使用简历或保存简历",0L);
        JdRow jd=null;
        if(request.jdText()!=null) {
            jd=ownedJd(owner,request.jdAnalysisId(),true);
            if(!jd.status().equals("SUCCEEDED") || !jd.direction().equals(request.direction().name()) || !jd.rawHash().equals(hash(request.jdText())))
                throw new InterviewException(HttpStatus.CONFLICT,"JD_ANALYSIS_STALE","JD分析与方向或原文不一致，请明确重新解析");
        }
        UUID id=UUID.randomUUID(); Instant now=now();
        var row=new SessionRow(id,owner,0,request.direction().name(),request.difficulty().name(),request.mainQuestionCount(),"PENDING","NOT_READY","NOT_STARTED",0,0,
                snapshot==null?null:snapshot.markdownText(),snapshot==null?null:snapshot.version(),snapshot==null?null:snapshot.contentSha256(),
                request.jdText(),jd==null?null:jd.resultJson(),payload,model.model(),"interview-question-v1","interview-rubric-v1",false,null,null,null,now,now);
        mapper.insertSession(row); var job=job(owner,"GENERATE",id,null,null,questionInput(row)); mapper.insertJob(job);
        var receipt=new ReceiptRow(owner,"CREATE",request.requestId(),payload,id,null,"SUCCEEDED",0,null,now);
        mapper.insertReceipt(receipt); return new Operation(receipt(receipt),List.of(job));
    }
    @Transactional
    public JdOperation parseJd(UUID owner,ParseJd request,String payload) {
        lockOwner(owner); var prior=prior(owner,"JD_PARSE",request.requestId(),payload);
        if(prior!=null) return new JdOperation(jdResponse(ownedJd(owner,prior.jdId(),false)),List.of());
        UUID id=UUID.randomUUID(); Instant now=now();
        var row=new JdRow(id,owner,0,request.direction().name(),request.jdText(),hash(request.jdText()),"PENDING",null,false,null,now,now);
        mapper.insertJd(row); var job=job(owner,"JD",null,id,null,new JdInput(request.direction(),request.jdText())); mapper.insertJob(job);
        mapper.insertReceipt(new ReceiptRow(owner,"JD_PARSE",request.requestId(),payload,null,id,"SUCCEEDED",0,null,now));
        return new JdOperation(jdResponse(row),List.of(job));
    }
    @Transactional
    public JdOperation retryJd(UUID owner,UUID id,VersionOperation request,String payload) {
        lockOwner(owner); var row=ownedJd(owner,id,true); var prior=prior(owner,"JD_RETRY",request.requestId(),payload);
        if(prior!=null) return new JdOperation(jdResponse(row),List.of());
        expected(row.version(),request.expectedVersion()); if(!row.status().equals("FAILED")) throw conflict("JD分析当前不能重试",row.version());
        var next=new JdRow(id,owner,row.version()+1,row.direction(),row.jdText(),row.rawHash(),"PENDING",null,false,null,row.createdAt(),now());
        mapper.updateJd(next,row.version()); var job=job(owner,"JD",null,id,null,new JdInput(Direction.valueOf(row.direction()),row.jdText())); mapper.insertJob(job);
        mapper.insertReceipt(new ReceiptRow(owner,"JD_RETRY",request.requestId(),payload,null,id,"SUCCEEDED",next.version(),null,now()));
        return new JdOperation(jdResponse(next),List.of(job));
    }
    @Transactional(readOnly=true)
    public JdAnalysis jd(UUID owner,UUID id) { return jdResponse(ownedJd(owner,id,false)); }
    @Transactional
    public void deleteJd(UUID owner,UUID id) {
        lockOwner(owner); var row=mapper.lockJd(owner,id).orElseThrow(InterviewPersistenceService::notFound); if(row.deleted()) return;
        mapper.deleteJdJobs(owner,id);
        mapper.updateJd(new JdRow(id,owner,row.version()+1,row.direction(),null,row.rawHash(),"FAILED",null,true,"DELETED",row.createdAt(),now()),row.version());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Session session(UUID owner,UUID id) {
        var row=ownedSession(owner,id,false); return response(row,mapper.questions(owner,id),mapper.answers(owner,id));
    }
    @Transactional(readOnly=true)
    public PageResponse<Summary> page(UUID owner,int page,int size) { return PageQueries.select(page,size,()->mapper.sessions(owner),this::summary); }
    @Transactional
    public Operation answer(UUID owner,UUID id,int turn,AnswerOperation request,String payload,boolean submit) {
        lockOwner(owner); var row=ownedSession(owner,id,true); String operation=submit?"SUBMIT":"DRAFT";
        var prior=prior(owner,operation,request.requestId(),payload); if(prior!=null) return new Operation(receipt(prior),List.of());
        expected(row.version(),request.expectedVersion());
        if(!row.generationStatus().equals("SUCCEEDED") || row.answerStatus().equals("COMPLETED") || row.currentTurn()!=turn)
            throw conflict("只能保存或提交当前未锁定轮次",row.version());
        var answers=mapper.answers(owner,id); var current=answers.stream().filter(a->a.turnIndex()==turn).findFirst().orElseThrow(InterviewPersistenceService::notFound);
        if(!current.status().equals("DRAFT")) throw conflict("该轮答案已经锁定",row.version());
        mapper.updateAnswer(new AnswerRow(id,owner,turn,submit?"SUBMITTED":"DRAFT",request.answerText(),current.version()+1));
        State state=new State(row); state.answerStatus="IN_PROGRESS";
        List<JobRow> jobs=List.of();
        if(submit) { state.currentTurn++; state.submittedCount++; if(state.currentTurn==row.mainCount()*2) jobs=freeze(state); }
        state.save(); var result=new ReceiptRow(owner,operation,request.requestId(),payload,id,null,"SUCCEEDED",state.version,turn,now()); mapper.insertReceipt(result);
        return new Operation(receipt(result),jobs);
    }
    @Transactional
    public Operation complete(UUID owner,UUID id,Complete request,String payload) {
        lockOwner(owner); var row=ownedSession(owner,id,true); var prior=prior(owner,"COMPLETE",request.requestId(),payload);
        if(prior!=null) return new Operation(receipt(prior),List.of());
        expected(row.version(),request.expectedVersion());
        if(!row.generationStatus().equals("SUCCEEDED") || row.answerStatus().equals("COMPLETED")) throw conflict("面试当前不能重复交卷",row.version());
        State state=new State(row); List<JobRow> jobs=freeze(state); state.save();
        var result=new ReceiptRow(owner,"COMPLETE",request.requestId(),payload,id,null,"SUCCEEDED",state.version,null,now()); mapper.insertReceipt(result);
        return new Operation(receipt(result),jobs);
    }
    @Transactional
    public Operation retry(UUID owner,UUID id,VersionOperation request,String payload,boolean generation) {
        lockOwner(owner); var row=ownedSession(owner,id,true); String operation=generation?"GENERATION_RETRY":"EVALUATION_RETRY";
        var prior=prior(owner,operation,request.requestId(),payload); if(prior!=null) return new Operation(receipt(prior),List.of());
        expected(row.version(),request.expectedVersion()); State state=new State(row); List<JobRow> jobs=new ArrayList<>();
        if(generation) {
            if(!row.generationStatus().equals("FAILED") || !row.answerStatus().equals("NOT_READY")) throw conflict("题单当前不能重试",row.version());
            var job=job(owner,"GENERATE",id,null,null,questionInput(row)); mapper.insertJob(job); jobs.add(job); state.generationStatus="PENDING";
        } else {
            if(!row.answerStatus().equals("COMPLETED") || !row.evaluationStatus().equals("FAILED")) throw conflict("评估当前不能重试",row.version());
            for(var group:mapper.evaluations(owner,id)) if(group.status().equals("FAILED")) {
                var input=groupInput(row,group.mainIndex()); var job=job(owner,"EVALUATE",id,null,group.mainIndex(),input);
                if(!job.inputHash().equals(group.inputHash())) throw conflict("冻结答卷摘要不一致",row.version());
                mapper.updateEvaluation(new EvaluationRow(id,owner,group.mainIndex(),group.inputHash(),"PENDING",null,null)); mapper.insertJob(job); jobs.add(job);
            }
            if(jobs.isEmpty()) throw conflict("没有可重试的失败分组",row.version()); state.evaluationStatus="PENDING";
        }
        state.code=null; state.save(); var result=new ReceiptRow(owner,operation,request.requestId(),payload,id,null,"SUCCEEDED",state.version,null,now()); mapper.insertReceipt(result);
        return new Operation(receipt(result),jobs);
    }
    private List<JobRow> freeze(State state) {
        UUID owner=state.base.userId(),id=state.base.id(); state.answerStatus="COMPLETED"; state.currentTurn=state.base.mainCount()*2;
        for(var answer:mapper.answers(owner,id)) if(answer.status().equals("DRAFT"))
            mapper.updateAnswer(new AnswerRow(id,owner,answer.turnIndex(),"UNANSWERED","",answer.version()+1));
        List<JobRow> jobs=new ArrayList<>();
        for(int main=0;main<state.base.mainCount()*2;main+=2) {
            var input=groupInput(state.base,main); String raw=write(input),hash=hash(raw);
            if(input.answers().stream().noneMatch(a->a.status().equals("SUBMITTED"))) {
                var turns=input.answers().stream().map(a->new ReportTurn(a.turnIndex(),"UNANSWERED",BigDecimal.ZERO,"未作答",List.of())).toList();
                mapper.insertEvaluation(new EvaluationRow(id,owner,main,hash,"SUCCEEDED",write(turns),null));
            } else {
                mapper.insertEvaluation(new EvaluationRow(id,owner,main,hash,"PENDING",null,null));
                var job=job(owner,"EVALUATE",id,null,main,input); mapper.insertJob(job); jobs.add(job);
            }
        }
        state.evaluationStatus=jobs.isEmpty()?"SUCCEEDED":"PENDING";
        if(jobs.isEmpty()) aggregate(state);
        return jobs;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Report report(UUID owner,UUID id) {
        var row=ownedSession(owner,id,false); Map<Integer,ReportTurn> scored=new HashMap<>(); List<ReportGroup> groups=new ArrayList<>();
        for(var answer:mapper.answers(owner,id)) if(answer.status().equals("UNANSWERED"))
            scored.put(answer.turnIndex(),new ReportTurn(answer.turnIndex(),"UNANSWERED",BigDecimal.ZERO,"未作答",List.of()));
        for(var group:mapper.evaluations(owner,id)) {
            groups.add(new ReportGroup(group.mainIndex(),group.status(),group.safeFailureCode()));
            if(group.status().equals("SUCCEEDED")) for(var turn:readTurns(group.resultJson())) scored.put(turn.turnIndex(),turn);
        }
        List<ReportTurn> turns=new ArrayList<>();
        for(int i=0;i<row.mainCount()*2;i++) turns.add(scored.getOrDefault(i,new ReportTurn(i,"NOT_EVALUATED",null,null,List.of())));
        return new Report(id,turns,groups,row.totalScore(),row.overallFeedback());
    }
    @Transactional
    public void delete(UUID owner,UUID id) {
        lockOwner(owner); var row=mapper.lockSession(owner,id).orElseThrow(InterviewPersistenceService::notFound); if(row.deleted()) return;
        mapper.deleteSessionJobs(owner,id); mapper.deleteEvaluations(owner,id); mapper.deleteAnswers(owner,id); mapper.deleteQuestions(owner,id);
        State state=new State(row); state.deleted=true; state.resumeText=null; state.resumeVersion=null; state.resumeHash=null; state.jdText=null; state.jdResult=null;
        state.total=null; state.overall=null; state.code="DELETED"; state.generationStatus="FAILED"; state.answerStatus="NOT_READY"; state.evaluationStatus="FAILED";
        state.currentTurn=0; state.submittedCount=0; state.save();
    }
    @Transactional
    public JobRow claim(JobRow offered) {
        lockOwner(offered.userId()); var subject=subject(offered); var row=mapper.lockJob(offered.userId(),offered.id()).orElseThrow();
        if(!row.status().equals("PENDING")) return null;
        if(subject==null || row.payloadJson()==null || !hash(row.payloadJson()).equals(row.inputHash())) { failLocked(row,"INPUT_INVALID"); return null; }
        UUID token=UUID.randomUUID(); Instant now=now();
        if(mapper.claimJob(row.userId(),row.id(),token,now.plus(execution.leaseDuration()),now)!=1) { failLocked(row,"QUEUE_EXPIRED"); return null; }
        var claimed=mapper.job(row.userId(),row.id()).orElseThrow(); markProcessing(claimed,subject); return claimed;
    }
    @Transactional
    public boolean finish(JobRow attempt,Object output) {
        lockOwner(attempt.userId()); var subject=subject(attempt); var row=mapper.lockJob(attempt.userId(),attempt.id()).orElseThrow();
        if(subject==null || !valid(row,attempt)) return false;
        if(row.kind().equals("JD")) {
            var result=(JdResult)output; InterviewOutput.jd(result); var jd=(JdRow)subject;
            mapper.updateJd(new JdRow(jd.id(),jd.userId(),jd.version()+1,jd.direction(),jd.jdText(),jd.rawHash(),"SUCCEEDED",write(result),false,null,jd.createdAt(),now()),jd.version());
        } else {
            var session=(SessionRow)subject; State state=new State(session);
            if(row.kind().equals("GENERATE")) {
                var set=(QuestionSet)output; InterviewOutput.questions(set,session.mainCount());
                for(var question:set.questions()) {
                    mapper.insertQuestion(new QuestionRow(session.id(),session.userId(),question.turnIndex(),question.type(),question.parentMainIndex(),question.text()));
                    mapper.insertAnswer(new AnswerRow(session.id(),session.userId(),question.turnIndex(),"DRAFT","",0));
                }
                state.generationStatus="SUCCEEDED"; state.answerStatus="READY"; state.code=null;
            } else {
                GroupInput input=read(row.payloadJson(),GroupInput.class); var score=(GroupScore)output; InterviewOutput.scores(score,input);
                Map<Integer,Score> byTurn=new HashMap<>(); score.turns().forEach(s->byTurn.put(s.turnIndex(),s));
                List<ReportTurn> turns=input.answers().stream().map(a->{ var s=byTurn.get(a.turnIndex()); return s==null
                        ?new ReportTurn(a.turnIndex(),"UNANSWERED",BigDecimal.ZERO,"未作答",List.of())
                        :new ReportTurn(a.turnIndex(),"SCORED",s.score(),s.feedback(),s.referencePoints()); }).toList();
                mapper.updateEvaluation(new EvaluationRow(session.id(),session.userId(),row.mainIndex(),row.inputHash(),"SUCCEEDED",write(turns),null)); aggregate(state);
            }
            state.save();
        }
        // DB wall-clock check is last. Losing the lease rolls back every inserted child/state.
        if(mapper.finishJob(row.userId(),row.id(),attempt.token(),attempt.inputHash(),"SUCCEEDED",null)!=1)
            throw new InterviewException(HttpStatus.CONFLICT,"EXECUTION_EXPIRED","面试执行权已过期");
        return true;
    }
    @Transactional
    public boolean fail(JobRow attempt,String code) {
        lockOwner(attempt.userId()); var subject=subject(attempt); var row=mapper.lockJob(attempt.userId(),attempt.id()).orElseThrow();
        if(subject==null || !valid(row,attempt)) return false;
        markFailed(row,subject,code);
        if(mapper.finishJob(row.userId(),row.id(),attempt.token(),attempt.inputHash(),"FAILED",code)!=1)
            throw new InterviewException(HttpStatus.CONFLICT,"EXECUTION_EXPIRED","面试执行权已过期");
        return true;
    }
    @Transactional
    public void rejected(JobRow offered) {
        lockOwner(offered.userId()); subject(offered); var row=mapper.lockJob(offered.userId(),offered.id()).orElseThrow();
        if(row.status().equals("PENDING")) failLocked(row,"QUEUE_UNAVAILABLE");
    }
    @Transactional(readOnly=true)
    public List<JobRow> expired() { return mapper.expiredJobs(); }
    @Transactional
    public void expire(JobRow offered) {
        lockOwner(offered.userId()); subject(offered); var row=mapper.lockJob(offered.userId(),offered.id()).orElseThrow();
        if(row.status().equals("PENDING") && !row.queueDeadline().isAfter(now()) || row.status().equals("PROCESSING") && !row.leaseExpiresAt().isAfter(now()))
            failLocked(row,row.status().equals("PENDING")?"QUEUE_EXPIRED":"EXECUTION_EXPIRED");
    }
    private void failLocked(JobRow row,String code) {
        if(mapper.failJob(row.userId(),row.id(),row.status(),row.token(),code)==1) { var subject=subject(row); if(subject!=null) markFailed(row,subject,code); }
    }
    private Object subject(JobRow job) {
        if(job.kind().equals("JD")) { var row=mapper.lockJd(job.userId(),job.jdId()).orElse(null); return row==null || row.deleted()?null:row; }
        var row=mapper.lockSession(job.userId(),job.sessionId()).orElse(null); return row==null || row.deleted()?null:row;
    }
    private boolean valid(JobRow row,JobRow attempt) {
        return row.status().equals("PROCESSING") && Objects.equals(row.token(),attempt.token()) && row.inputHash().equals(attempt.inputHash())
                && row.payloadJson()!=null && hash(row.payloadJson()).equals(attempt.inputHash()) && row.leaseExpiresAt().isAfter(now());
    }
    private void markProcessing(JobRow job,Object subject) {
        if(subject instanceof JdRow jd) mapper.updateJd(new JdRow(jd.id(),jd.userId(),jd.version()+1,jd.direction(),jd.jdText(),jd.rawHash(),"PROCESSING",null,false,null,jd.createdAt(),now()),jd.version());
        else {
            State state=new State((SessionRow)subject);
            if(job.kind().equals("GENERATE")) state.generationStatus="PROCESSING";
            else { mapper.updateEvaluation(new EvaluationRow(job.sessionId(),job.userId(),job.mainIndex(),job.inputHash(),"PROCESSING",null,null)); aggregate(state); }
            state.save();
        }
    }
    private void markFailed(JobRow job,Object subject,String code) {
        if(subject instanceof JdRow jd) mapper.updateJd(new JdRow(jd.id(),jd.userId(),jd.version()+1,jd.direction(),jd.jdText(),jd.rawHash(),"FAILED",null,false,code,jd.createdAt(),now()),jd.version());
        else {
            State state=new State((SessionRow)subject); state.code=code;
            if(job.kind().equals("GENERATE")) state.generationStatus="FAILED";
            else { mapper.updateEvaluation(new EvaluationRow(job.sessionId(),job.userId(),job.mainIndex(),job.inputHash(),"FAILED",null,code)); aggregate(state); }
            state.save();
        }
    }
    private void aggregate(State state) {
        var groups=mapper.evaluations(state.base.userId(),state.base.id()); state.total=null; state.overall=null;
        if(groups.stream().anyMatch(g->g.status().equals("FAILED"))) { state.evaluationStatus="FAILED"; return; }
        if(groups.size()!=state.base.mainCount() || groups.stream().anyMatch(g->!g.status().equals("SUCCEEDED"))) {
            state.evaluationStatus=groups.stream().anyMatch(g->g.status().equals("PROCESSING"))?"PROCESSING":"PENDING"; return;
        }
        List<ReportTurn> turns=groups.stream().flatMap(g->readTurns(g.resultJson()).stream()).toList();
        if(turns.size()!=state.base.mainCount()*2 || turns.stream().anyMatch(t->t.score()==null)) throw InterviewOutput.invalid();
        BigDecimal sum=turns.stream().map(ReportTurn::score).reduce(BigDecimal.ZERO,BigDecimal::add);
        state.total=sum.divide(BigDecimal.valueOf(turns.size()),2,RoundingMode.HALF_UP); state.evaluationStatus="SUCCEEDED"; state.code=null;
        state.overall=String.join("\n",turns.stream().map(t->"第"+(t.turnIndex()+1)+"轮："+t.feedback()).toList());
    }
    private QuestionInput questionInput(SessionRow row) { return new QuestionInput(Direction.valueOf(row.direction()),Difficulty.valueOf(row.difficulty()),row.mainCount(),row.resumeText(),row.jdText(),readNullable(row.jdResult(),JdResult.class),row.questionVersion(),row.rubricVersion(),row.modelVersion()); }
    private GroupInput groupInput(SessionRow row,int main) {
        return new GroupInput(Direction.valueOf(row.direction()),Difficulty.valueOf(row.difficulty()),row.resumeText(),row.jdText(),readNullable(row.jdResult(),JdResult.class),
                mapper.questions(row.userId(),row.id()).stream().filter(q->q.turnIndex()==main || q.turnIndex()==main+1).map(this::question).toList(),
                mapper.answers(row.userId(),row.id()).stream().filter(a->a.turnIndex()==main || a.turnIndex()==main+1).map(this::answer).toList(),row.rubricVersion(),row.modelVersion());
    }
    private JobRow job(UUID owner,String kind,UUID session,UUID jd,Integer main,Object payload) {
        String raw=write(payload); Instant now=now(); return new JobRow(UUID.randomUUID(),owner,kind,session,jd,main,hash(raw),raw,"PENDING",null,null,now.plus(execution.queueDuration()),null,now,now);
    }
    private ReceiptRow prior(UUID owner,String operation,UUID request,String payload) {
        var prior=mapper.receipt(owner,operation,request).orElse(null);
        if(prior!=null && !prior.payloadHash().equals(payload)) throw new InterviewException(HttpStatus.CONFLICT,"INTERVIEW_REQUEST_CONFLICT","同一请求标识不能用于不同内容"); return prior;
    }
    private SessionRow ownedSession(UUID owner,UUID id,boolean lock) {
        var row=(lock?mapper.lockSession(owner,id):mapper.session(owner,id)).orElseThrow(InterviewPersistenceService::notFound);
        if(row.deleted()) throw notFound(); return row;
    }
    private JdRow ownedJd(UUID owner,UUID id,boolean lock) {
        if(id==null) throw new InterviewException(HttpStatus.CONFLICT,"JD_ANALYSIS_REQUIRED","使用JD需要先成功解析原文");
        var row=(lock?mapper.lockJd(owner,id):mapper.jd(owner,id)).orElseThrow(InterviewPersistenceService::notFound);
        if(row.deleted()) throw notFound(); return row;
    }
    private void lockOwner(UUID owner) { mapper.lockOwner(owner).orElseThrow(InterviewPersistenceService::notFound); }
    private void expected(long current,long expected) { if(current!=expected) throw conflict("面试状态已变化，请刷新后重试",current); }
    private static InterviewException conflict(String message,Long version) { return new InterviewException(HttpStatus.CONFLICT,"VERSION_CONFLICT",message,version); }
    private static InterviewException notFound() { return new InterviewException(HttpStatus.NOT_FOUND,"INTERVIEW_NOT_FOUND","面试或JD分析不可访问"); }
    private Session response(SessionRow r,List<QuestionRow> q,List<AnswerRow> a) {
        return new Session(r.id(),r.version(),Direction.valueOf(r.direction()),Difficulty.valueOf(r.difficulty()),r.mainCount(),r.generationStatus(),r.answerStatus(),r.evaluationStatus(),r.currentTurn(),r.submittedCount(),r.resumeText()!=null,r.jdText()!=null,r.resumeVersion(),r.resumeText(),r.jdText(),r.createdAt(),r.updatedAt(),q.stream().map(this::question).toList(),a.stream().map(this::answer).toList(),r.safeFailureCode());
    }
    private Summary summary(SessionRow r) { return new Summary(r.id(),r.version(),Direction.valueOf(r.direction()),Difficulty.valueOf(r.difficulty()),r.mainCount(),r.generationStatus(),r.answerStatus(),r.evaluationStatus(),r.currentTurn(),r.submittedCount(),r.resumeText()!=null,r.jdText()!=null,r.createdAt(),r.updatedAt(),r.safeFailureCode()); }
    private Question question(QuestionRow r) { return new Question(r.turnIndex(),r.type(),r.parentMainIndex(),r.text()); }
    private Answer answer(AnswerRow r) { return new Answer(r.turnIndex(),r.status(),r.answerText(),r.version()); }
    private JdAnalysis jdResponse(JdRow r) { return new JdAnalysis(r.id(),r.version(),r.status(),Direction.valueOf(r.direction()),r.jdText(),readNullable(r.resultJson(),JdResult.class),r.safeFailureCode()); }
    private Receipt receipt(ReceiptRow r) {
        boolean deleted=r.sessionId()!=null && mapper.session(r.userId(),r.sessionId()).map(SessionRow::deleted).orElse(true);
        return new Receipt(r.requestId(),r.operation(),deleted?"DELETED":r.state(),r.sessionId(),r.resultVersion(),r.turnIndex());
    }
    public String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception failure) { throw new IllegalStateException("Invalid interview serialization",failure); } }
    public <T> T read(String value,Class<T> type) { try { return json.readValue(value,type); } catch(Exception failure) { throw new IllegalStateException("Invalid persisted interview data",failure); } }
    private <T> T readNullable(String value,Class<T> type) { return value==null?null:read(value,type); }
    private List<ReportTurn> readTurns(String value) { try { return json.readValue(value,new TypeReference<List<ReportTurn>>() {}); } catch(Exception failure) { throw new IllegalStateException("Invalid persisted evaluation",failure); } }
    public static String hash(String text) { return ResumeServiceImpl.hash(text); }
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public record Operation(Receipt receipt,List<JobRow> jobs) {}
    public record JdOperation(JdAnalysis analysis,List<JobRow> jobs) {}
    private final class State {
        final SessionRow base; long version; String generationStatus,answerStatus,evaluationStatus,resumeText,resumeHash,jdText,jdResult,overall,code;
        int currentTurn,submittedCount; Long resumeVersion; boolean deleted; BigDecimal total;
        State(SessionRow r) { base=r; version=r.version(); generationStatus=r.generationStatus(); answerStatus=r.answerStatus(); evaluationStatus=r.evaluationStatus();
            currentTurn=r.currentTurn(); submittedCount=r.submittedCount(); resumeText=r.resumeText(); resumeVersion=r.resumeVersion(); resumeHash=r.resumeHash(); jdText=r.jdText(); jdResult=r.jdResult(); deleted=r.deleted(); total=r.totalScore(); overall=r.overallFeedback(); code=r.safeFailureCode(); }
        void save() {
            var next=new SessionRow(base.id(),base.userId(),version+1,base.direction(),base.difficulty(),base.mainCount(),generationStatus,answerStatus,evaluationStatus,currentTurn,submittedCount,
                    resumeText,resumeVersion,resumeHash,jdText,jdResult,base.inputHash(),base.modelVersion(),base.questionVersion(),base.rubricVersion(),deleted,total,overall,code,base.createdAt(),now());
            if(mapper.updateSession(next,version)!=1) throw conflict("面试状态已变化",version); version++;
        }
    }
}
