package com.aiworkbench.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("workbench.interview")
public record InterviewAiProperties(@DefaultValue("PT4M") Duration timeout,@DefaultValue("PT5M") Duration leaseDuration,
        @DefaultValue("PT45M") Duration queueDuration) {
    public InterviewAiProperties {
        if(timeout==null || timeout.isZero() || timeout.isNegative() || leaseDuration==null || leaseDuration.compareTo(timeout)<=0
                || queueDuration==null || queueDuration.isZero() || queueDuration.isNegative()) throw new IllegalArgumentException("Invalid interview deadlines");
    }
}
