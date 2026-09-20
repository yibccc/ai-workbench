package com.aiworkbench.ai;

import java.util.List;

public record AiCaptureResult(List<RecordItem> records, List<TaskItem> tasks) {
    public record RecordItem(String content, String projectName, String occurredAt) {}
    public record TaskItem(String title, String notes, String projectName, String dueAt, String priority) {}
}
