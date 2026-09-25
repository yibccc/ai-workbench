package com.aiworkbench.entity.input;

import com.aiworkbench.enums.InputStatus;
import java.time.Instant;
import java.util.UUID;

public record InputRow(UUID id, UUID userId, String clientRequestId, String content, Instant referenceAt, String zoneId,
                InputStatus status, String errorMessage, Integer attemptCount, Instant completedAt,
                Instant createdAt, Instant updatedAt, Boolean revertible) {}
