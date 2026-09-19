package com.aiworkbench.task;

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
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
