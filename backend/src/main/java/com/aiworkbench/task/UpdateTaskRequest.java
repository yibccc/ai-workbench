package com.aiworkbench.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UpdateTaskRequest(
        UUID projectId,
        @NotBlank(message = "待办标题不能为空") @Size(max = 240, message = "待办标题不能超过 240 个字符") String title,
        @Size(max = 4000, message = "备注不能超过 4000 个字符") String notes,
        Instant dueAt,
        @NotNull(message = "优先级不能为空") TaskPriority priority,
        @NotNull(message = "版本不能为空") @PositiveOrZero(message = "版本不能为负数") Long version) {
}
