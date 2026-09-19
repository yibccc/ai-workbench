package com.aiworkbench.task;

import java.time.Instant;
import java.util.UUID;

record TaskRow(
        UUID id,
        UUID projectId,
        String projectName,
        String projectStatus,
        String title,
        String notes,
        TaskPriority priority,
        TaskStatus status,
        Instant dueAt,
        Instant completedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
    TaskResponse toResponse() {
        TaskProjectResponse project = projectId == null ? null
                : new TaskProjectResponse(projectId, projectName, projectStatus);
        return new TaskResponse(id, project, title, notes, priority, status, dueAt, completedAt,
                version, createdAt, updatedAt);
    }
}
