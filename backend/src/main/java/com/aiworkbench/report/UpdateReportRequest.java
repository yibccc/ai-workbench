package com.aiworkbench.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateReportRequest(
        @NotBlank @Size(max = 20000) String content,
        @NotNull @PositiveOrZero Long version) {}
