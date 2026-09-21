package com.aiworkbench.dto.input;

import com.aiworkbench.enums.TaskPriority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PreparedCapture(List<RecordItem> records, List<TaskItem> tasks) {
    public record RecordItem(UUID projectId, String content, Instant occurredAt) {}
    public record TaskItem(UUID projectId, String title, String notes, Instant dueAt, TaskPriority priority) {}
}
