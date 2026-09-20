package com.aiworkbench.input;

import com.aiworkbench.task.TaskPriority;
import java.time.Instant;
import java.util.UUID;

record GeneratedTaskRow(UUID id, UUID projectId, String projectName, String title, String notes,
                        Instant dueAt, TaskPriority priority, Long version) {}
