package com.aiworkbench;

import com.aiworkbench.config.DeepSeekProperties;
import com.aiworkbench.report.ReportAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DeepSeekProperties.class, ReportAiProperties.class})
public class WorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}
