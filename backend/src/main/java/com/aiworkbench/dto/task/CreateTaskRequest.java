package com.aiworkbench.dto.task;

import com.aiworkbench.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        UUID projectId,
        @NotBlank(message = "待办标题不能为空") @Size(max = 240, message = "待办标题不能超过 240 个字符") String title,
        @Size(max = 4000, message = "备注不能超过 4000 个字符") String notes,
        Instant dueAt,
        TaskPriority priority) {
}
