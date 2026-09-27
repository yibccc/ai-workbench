package com.aiworkbench.entity.record;

import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.enums.WorkRecordSource;
import java.time.Instant;
import java.util.UUID;

public record WorkRecordRow(
        UUID id,
        UUID projectId,
        String projectName,
        String projectStatus,
        String content,
        WorkRecordSource source,
        UUID todoId,
        Boolean active,
        String completionResult,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        UUID sessionId,
        java.time.LocalDate businessDate,
        Long focusMs,
        Long breakMs,
        Instant segmentStart,
        Instant segmentEnd,
        String progress) {

    public WorkRecordResponse toResponse() {
        WorkRecordResponse.ProjectSummary project = projectId == null
                ? null
                : new WorkRecordResponse.ProjectSummary(projectId, projectName, projectStatus);
        return new WorkRecordResponse(id, project, content, source, todoId, active,
                completionResult == null ? "" : completionResult, occurredAt, createdAt, updatedAt,
                sessionId, businessDate, focusMs, breakMs, segmentStart, segmentEnd, progress);
    }
}
