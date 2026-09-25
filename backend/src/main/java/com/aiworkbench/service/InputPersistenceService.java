package com.aiworkbench.service;

import com.aiworkbench.dto.input.PreparedCapture;
import com.aiworkbench.dto.input.ProcessingClaim;
import com.aiworkbench.entity.input.InputRow;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public interface InputPersistenceService {
    ProcessingClaim createOrGet(String requestId, String content, Instant referenceAt, ZoneId zoneId);
    ProcessingClaim beginRetry(UUID id, Instant now);
    void succeed(UUID inputId, UUID token, PreparedCapture capture, Instant completedAt);
    void fail(UUID inputId, UUID token, String message, Instant completedAt);
    int recoverExpiredProcessing(Instant now);
    InputRow revert(UUID id, Instant now);
    InputRow require(UUID id);
    InputRow requireOwned(UUID userId, UUID id);
}
