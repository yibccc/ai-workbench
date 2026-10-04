package com.aiworkbench.interview;

import com.aiworkbench.WorkbenchApplication;
import com.aiworkbench.ai.InterviewAiGateway;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.mapper.InterviewMapper;
import com.aiworkbench.service.impl.InterviewPersistenceService;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Test-only independent JVM, with a counter proving startup recovery never calls a model. */
public final class InterviewCrashHarness {
    static final AtomicInteger calls=new AtomicInteger();
    @TestConfiguration(proxyBeanMethods=false)
    public static class CounterConfiguration {
        @Bean @Primary InterviewAiGateway countingGateway() {
            return new InterviewAiGateway() {
                public JdResult parseJd(JdInput input) { calls.incrementAndGet(); throw new IllegalStateException("unexpected model call"); }
                public QuestionSet generate(QuestionInput input) { calls.incrementAndGet(); throw new IllegalStateException("unexpected model call"); }
                public GroupScore evaluate(GroupInput input) { calls.incrementAndGet(); throw new IllegalStateException("unexpected model call"); }
            };
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException(); UUID owner=UUID.fromString(args[1]),job=UUID.fromString(args[2]); Path marker=Path.of(args[3]);
        try(var context=new SpringApplicationBuilder(WorkbenchApplication.class,CounterConfiguration.class).run("--spring.profiles.active=test","--server.port=0")) {
            var mapper=context.getBean(InterviewMapper.class);
            if(args[0].equals("claim")) {
                var offered=mapper.job(owner,job).orElseThrow(); var claimed=context.getBean(InterviewPersistenceService.class).claim(offered);
                Files.writeString(marker,claimed.token().toString()); Thread.sleep(600000);
            } else if(args[0].equals("recover")) {
                var row=mapper.job(owner,job).orElseThrow(); Files.writeString(marker,row.status()+":"+calls.get());
            } else throw new IllegalArgumentException();
        }
    }
}
