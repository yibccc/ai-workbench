package com.aiworkbench.entity.input;

import com.aiworkbench.enums.TaskPriority;
import java.time.Instant;
import java.util.UUID;

public record GeneratedTaskRow(UUID id, UUID projectId, String projectName, String title, String notes,
                        Instant dueAt, TaskPriority priority, Long version) {}
