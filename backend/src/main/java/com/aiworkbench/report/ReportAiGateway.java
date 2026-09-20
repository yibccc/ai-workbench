package com.aiworkbench.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface ReportAiGateway {
    AiReportResult generate(LocalDate date, ZoneId zoneId, List<ReportSourcePrompt> sources);
}
