package com.aiworkbench.entity.task;

import com.aiworkbench.dto.task.TaskEventResponse;
import com.aiworkbench.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskEventRow(
        UUID id,
        UUID todoId,
        String eventType,
        String title,
        UUID projectId,
        TaskStatus fromStatus,
        TaskStatus toStatus,
        Long version,
        String result,
        Instant occurredAt) {
    public TaskEventResponse toResponse() {
        return new TaskEventResponse(id, todoId, eventType, title, projectId, fromStatus, toStatus,
                version, result == null ? "" : result, occurredAt);
    }
}
