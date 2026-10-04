package com.aiworkbench.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(InterviewAiProperties.class)
public class InterviewExecutionConfiguration {
    @Bean("interviewTaskExecutor")
    public ThreadPoolTaskExecutor interviewTaskExecutor() {
        var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(2); executor.setMaxPoolSize(2);
        executor.setQueueCapacity(40); executor.setThreadNamePrefix("interview-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(false); return executor;
    }
}
