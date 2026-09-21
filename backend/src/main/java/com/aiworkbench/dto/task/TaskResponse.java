package com.aiworkbench.dto.task;

import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        TaskProjectResponse project,
        String title,
        String notes,
        TaskPriority priority,
        TaskStatus status,
        Instant dueAt,
        Instant completedAt,
        UUID completionRecordId,
        String completionResult,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
