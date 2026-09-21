package com.aiworkbench.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(
        @NotNull(message = "版本不能为空") @PositiveOrZero(message = "版本不能为负数") Long version,
        @Size(max = 4000, message = "完成结果不能超过 4000 个字符") String result) {
}
