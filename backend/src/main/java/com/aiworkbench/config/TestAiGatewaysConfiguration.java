package com.aiworkbench.config;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.AiReportResult;
import com.aiworkbench.ai.DeepSeekGateway;
import com.aiworkbench.ai.ReportAiGateway;
import com.aiworkbench.ai.ReportSourcePrompt;
import com.aiworkbench.ai.WorkbenchAiGateway;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** No-network defaults for tests that do not replace a particular AI gateway. */
@Configuration
@Profile("test")
public class TestAiGatewaysConfiguration {
    @Bean
    WorkbenchAiGateway testWorkbenchAiGateway() {
        return (content, referenceAt, zoneId, projects) -> new AiCaptureResult(List.of(), List.of());
    }

    @Bean
    DeepSeekGateway testDeepSeekGateway() {
        return () -> "AI_WORKBENCH_TEST_OK";
    }

    @Bean
    ReportAiGateway testReportAiGateway() {
        return new ReportAiGateway() {
            @Override
            public AiReportResult generate(LocalDate date, java.time.ZoneId zoneId,
                                           List<ReportSourcePrompt> sources) {
                return new AiReportResult(List.of());
            }

            @Override
            public AiReportResult generateWeekly(LocalDate periodStart, LocalDate periodEnd,
                                                 java.time.ZoneId zoneId,
                                                 List<ReportSourcePrompt> sources) {
                return new AiReportResult(List.of());
            }
        };
    }
}
