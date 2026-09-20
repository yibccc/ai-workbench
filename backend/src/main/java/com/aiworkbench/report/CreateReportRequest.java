package com.aiworkbench.report;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateReportRequest(@NotNull UUID requestId, String reportType, LocalDate date) {}
