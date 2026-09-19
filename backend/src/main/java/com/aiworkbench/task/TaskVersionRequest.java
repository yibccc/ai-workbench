package com.aiworkbench.task;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record TaskVersionRequest(
        @NotNull(message = "版本不能为空") @PositiveOrZero(message = "版本不能为负数") Long version) {
}
