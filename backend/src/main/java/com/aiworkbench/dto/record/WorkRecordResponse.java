package com.aiworkbench.dto.record;

import com.aiworkbench.enums.WorkRecordSource;
import java.time.Instant;
import java.util.UUID;

public record WorkRecordResponse(
        UUID id,
        ProjectSummary project,
        String content,
        WorkRecordSource source,
        UUID taskId,
        boolean active,
        String completionResult,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {
    public record ProjectSummary(UUID id, String name, String status) {
    }
}
