package com.aiworkbench.input;

import com.aiworkbench.task.TaskPriority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record PreparedCapture(List<RecordItem> records, List<TaskItem> tasks) {
    record RecordItem(UUID projectId, String content, Instant occurredAt) {}
    record TaskItem(UUID projectId, String title, String notes, Instant dueAt, TaskPriority priority) {}
}
