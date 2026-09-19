package com.aiworkbench.project;

import java.time.Instant;
import java.util.UUID;

record ProjectRow(UUID id, String name, String status, Instant archivedAt, Instant createdAt, Instant updatedAt) {
    ProjectResponse toResponse() {
        return new ProjectResponse(id, name, status, archivedAt, createdAt, updatedAt);
    }
}
