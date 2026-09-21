package com.aiworkbench.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskVersionRequest(
        @NotNull(message = "版本不能为空") @PositiveOrZero(message = "版本不能为负数") Long version) {
}
