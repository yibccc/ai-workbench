package com.aiworkbench.dto.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateManualAdditionsRequest(
        @NotNull @Size(max = 20000) String manualAdditions,
        @NotNull Long version) {}
