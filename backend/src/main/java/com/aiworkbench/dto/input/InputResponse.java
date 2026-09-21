package com.aiworkbench.dto.input;

import com.aiworkbench.enums.InputStatus;
import com.aiworkbench.enums.TaskPriority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InputResponse(UUID id, String requestId, String content, Instant referenceAt, String zoneId,
                            InputStatus status, String errorMessage, int attemptCount, Instant completedAt,
                            Instant createdAt, Instant updatedAt, List<GeneratedRecord> records,
                            List<GeneratedTask> tasks) {
    public record GeneratedRecord(UUID id, UUID projectId, String projectName, String content, Instant occurredAt) {}
    public record GeneratedTask(UUID id, UUID projectId, String projectName, String title, String notes,
                                Instant dueAt, TaskPriority priority, long version) {}
}
