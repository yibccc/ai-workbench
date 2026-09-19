package com.aiworkbench.task;

import java.time.Instant;
import java.util.UUID;

record TaskEventRow(
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
    TaskEventResponse toResponse() {
        return new TaskEventResponse(id, todoId, eventType, title, projectId, fromStatus, toStatus,
                version, result == null ? "" : result, occurredAt);
    }
}
