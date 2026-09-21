package com.aiworkbench.dto.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {
}
