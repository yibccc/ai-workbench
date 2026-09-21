package com.aiworkbench.entity.project;

import com.aiworkbench.dto.project.ProjectResponse;
import java.time.Instant;
import java.util.UUID;

public record ProjectRow(UUID id, String name, String status, Instant archivedAt, Instant createdAt, Instant updatedAt) {
    public ProjectResponse toResponse() {
        return new ProjectResponse(id, name, status, archivedAt, createdAt, updatedAt);
    }
}
