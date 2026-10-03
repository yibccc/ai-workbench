package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.exception.InterviewException;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.*;
import com.aiworkbench.storage.MarkdownText;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validation and authorized scheduling; no transaction spans the SDK/executor. */
@Service
public class InterviewServiceImpl implements InterviewService {
    private final InterviewPersistenceService persistence;
    private final InterviewJobRunner jobs;
    public InterviewServiceImpl(InterviewPersistenceService persistence,InterviewJobRunner jobs) { this.persistence=persistence; this.jobs=jobs; }
    @Override public Receipt create(Create request) {
        UUID owner=CurrentUser.requireId();
        if(request==null || request.requestId()==null || request.direction()==null || request.difficulty()==null || request.useCurrentResume()==null) throw invalid();
        int count=request.mainQuestionCount()==null?5:request.mainQuestionCount(); if(count<3 || count>20) throw invalid();
        if(request.useCurrentResume() && (request.expectedResumeVersion()==null || request.expectedResumeVersion()<0)
                || !request.useCurrentResume() && request.expectedResumeVersion()!=null) throw invalid();
        if((request.jdText()==null)!=(request.jdAnalysisId()==null)) throw invalid(); if(request.jdText()!=null) text(request.jdText(),10000);
        var normalized=new Create(request.requestId(),request.direction(),request.difficulty(),count,request.useCurrentResume(),request.expectedResumeVersion(),request.jdText(),request.jdAnalysisId());
        var result=persistence.create(owner,normalized,payload("CREATE",normalized)); jobs.dispatch(result.jobs()); return result.receipt();
    }
    @Override public Session session(UUID id) { return persistence.session(CurrentUser.requireId(),id); }
    @Override public PageResponse<Summary> page(int page,int size) { return persistence.page(CurrentUser.requireId(),page,size); }
    @Override public JdAnalysis parseJd(ParseJd request) {
        UUID owner=CurrentUser.requireId(); if(request==null || request.direction()==null || request.requestId()==null) throw invalid(); text(request.jdText(),10000);
        var result=persistence.parseJd(owner,request,payload("JD_PARSE",request)); jobs.dispatch(result.jobs()); return result.analysis();
    }
    @Override public JdAnalysis jd(UUID id) { return persistence.jd(CurrentUser.requireId(),id); }
    @Override public JdAnalysis retryJd(UUID id,VersionOperation request) {
        UUID owner=CurrentUser.requireId(); version(request); var result=persistence.retryJd(owner,id,request,payload("JD_RETRY",id,request)); jobs.dispatch(result.jobs()); return result.analysis();
    }
    @Override public void deleteJd(UUID id) { persistence.deleteJd(CurrentUser.requireId(),id); }
    @Override public Receipt draft(UUID id,AnswerOperation request) {
        UUID owner=CurrentUser.requireId(); answer(request); if(request.turnIndex()==null || request.turnIndex()<0 || request.turnIndex()>39) throw invalid();
        var result=persistence.answer(owner,id,request.turnIndex(),request,payload("DRAFT",id,request),false); jobs.dispatch(result.jobs()); return result.receipt();
    }
    @Override public Receipt submit(UUID id,int turn,AnswerOperation request) {
        UUID owner=CurrentUser.requireId(); answer(request); if(turn<0 || turn>39 || request.turnIndex()!=null && request.turnIndex()!=turn) throw invalid();
        var result=persistence.answer(owner,id,turn,request,payload("SUBMIT",id,turn,request),true); jobs.dispatch(result.jobs()); return result.receipt();
    }
    @Override public Receipt complete(UUID id,Complete request) {
        UUID owner=CurrentUser.requireId(); if(request==null || !Boolean.TRUE.equals(request.early())) throw invalid(); version(new VersionOperation(request.expectedVersion(),request.requestId()));
        var result=persistence.complete(owner,id,request,payload("COMPLETE",id,request)); jobs.dispatch(result.jobs()); return result.receipt();
    }
    @Override public Receipt retry(UUID id,VersionOperation request,boolean generation) {
        UUID owner=CurrentUser.requireId(); version(request); var result=persistence.retry(owner,id,request,payload(generation?"GENERATION_RETRY":"EVALUATION_RETRY",id,request),generation); jobs.dispatch(result.jobs()); return result.receipt();
    }
    @Override public Report report(UUID id) { return persistence.report(CurrentUser.requireId(),id); }
    @Override public void delete(UUID id) { persistence.delete(CurrentUser.requireId(),id); }
    private String payload(Object... parts) { return InterviewPersistenceService.hash(persistence.write(parts)); }
    private void answer(AnswerOperation request) { if(request==null) throw invalid(); version(new VersionOperation(request.expectedVersion(),request.requestId())); text(request.answerText(),5000); }
    private void version(VersionOperation request) { if(request==null || request.expectedVersion()==null || request.expectedVersion()<0 || request.requestId()==null) throw invalid(); }
    public static void text(String value,int limit) {
        try { MarkdownText.validate(value); } catch(java.io.IOException failure) { throw new InterviewException(HttpStatus.BAD_REQUEST,"INTERVIEW_TEXT_INVALID","资料须为有效文本且不含二进制控制字符"); }
        if(value.codePointCount(0,value.length())>limit) throw new InterviewException(HttpStatus.BAD_REQUEST,"INTERVIEW_TEXT_TOO_LONG","资料超过允许的字符数");
    }
    private static InterviewException invalid() { return new InterviewException(HttpStatus.BAD_REQUEST,"INTERVIEW_REQUEST_INVALID","面试请求参数无效"); }
}
