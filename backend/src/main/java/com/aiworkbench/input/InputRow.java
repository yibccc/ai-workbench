package com.aiworkbench.input;

import java.time.Instant;
import java.util.UUID;

record InputRow(UUID id, String clientRequestId, String content, Instant referenceAt, String zoneId,
                InputStatus status, String errorMessage, Integer attemptCount, Instant completedAt,
                Instant createdAt, Instant updatedAt) {}
