package com.aiworkbench.entity.task;

import com.aiworkbench.dto.task.TaskProjectResponse;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskRow(
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
        UUID completionRecordId,
        String completionResult,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
    public TaskResponse toResponse() {
        TaskProjectResponse project = projectId == null ? null
                : new TaskProjectResponse(projectId, projectName, projectStatus);
        return new TaskResponse(id, project, title, notes, priority, status, dueAt, completedAt,
                completionRecordId, completionResult == null ? "" : completionResult,
                version, createdAt, updatedAt);
    }
}
