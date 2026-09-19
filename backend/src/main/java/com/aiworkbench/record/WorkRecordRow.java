package com.aiworkbench.record;

import java.time.Instant;
import java.util.UUID;

record WorkRecordRow(
        UUID id,
        UUID projectId,
        String projectName,
        String projectStatus,
        String content,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {

    WorkRecordResponse toResponse() {
        WorkRecordResponse.ProjectSummary project = projectId == null
                ? null
                : new WorkRecordResponse.ProjectSummary(projectId, projectName, projectStatus);
        return new WorkRecordResponse(id, project, content, occurredAt, createdAt, updatedAt);
    }
}
