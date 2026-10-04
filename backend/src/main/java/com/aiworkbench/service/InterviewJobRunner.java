package com.aiworkbench.service;

import com.aiworkbench.ai.*;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.entity.interview.InterviewRows.JobRow;
import com.aiworkbench.service.impl.InterviewPersistenceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Durable jobs are claimed when an executor worker starts, never while enqueueing. */
@Service
public class InterviewJobRunner implements ApplicationRunner {
    private final InterviewPersistenceService persistence;
    private final InterviewAiGateway gateway;
    private final TaskExecutor executor;
    public InterviewJobRunner(InterviewPersistenceService persistence,InterviewAiGateway gateway,@Qualifier("interviewTaskExecutor") TaskExecutor executor) {
        this.persistence=persistence; this.gateway=gateway; this.executor=executor;
    }
    public void dispatch(List<JobRow> jobs) {
        for(var job:jobs) {
            try { executor.execute(()->process(job)); }
            catch(RuntimeException rejection) { persistence.rejected(job); }
        }
    }
    public void process(JobRow offered) {
        JobRow claimed=persistence.claim(offered); if(claimed==null) return;
        try {
            Object result=switch(claimed.kind()) {
                case "JD" -> gateway.parseJd(persistence.read(claimed.payloadJson(),JdInput.class));
                case "GENERATE" -> gateway.generate(persistence.read(claimed.payloadJson(),QuestionInput.class));
                case "EVALUATE" -> gateway.evaluate(persistence.read(claimed.payloadJson(),GroupInput.class));
                default -> throw InterviewOutput.invalid();
            };
            persistence.finish(claimed,result);
        } catch(InterviewModelException safe) { persistence.fail(claimed,safe.code()); }
        catch(RuntimeException unavailable) {
            try { persistence.fail(claimed,"MODEL_UNAVAILABLE"); }
            catch(RuntimeException databaseUnavailable) { /* Durable processing lease is scanned later. */ }
        }
    }
    @Override public void run(ApplicationArguments args) { recover(); }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void recover() { for(var row:persistence.expired()) persistence.expire(row); }
}
