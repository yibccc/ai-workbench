package com.aiworkbench.input;

import java.time.Instant;
import java.util.UUID;

record GeneratedRecordRow(UUID id, UUID projectId, String projectName, String content, Instant occurredAt) {}
