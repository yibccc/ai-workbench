package com.aiworkbench.dto.task;

import com.aiworkbench.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskEventResponse(
        UUID id,
        UUID taskId,
        String eventType,
        String title,
        UUID projectId,
        TaskStatus fromStatus,
        TaskStatus toStatus,
        long version,
        String result,
        Instant occurredAt) {
}
