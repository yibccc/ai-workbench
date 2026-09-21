package com.aiworkbench.entity.input;

import java.time.Instant;
import java.util.UUID;

public record GeneratedRecordRow(UUID id, UUID projectId, String projectName, String content, Instant occurredAt) {}
