package com.aiworkbench.entity.resume;

import java.time.Instant;
import java.util.UUID;

public final class ResumeRows {
    private ResumeRows() {}
    public record Current(UUID userId, long version, String markdownText, String sourceKind,
            UUID currentObjectId, String contentSha256, Instant updatedAt) {}
    public record ObjectRow(UUID id, UUID userId, UUID requestId, String storageKey, String originalFilename,
            String contentType, long byteSize, String sha256, String status, UUID uploadToken,
            UUID operationToken, Instant leaseExpiresAt, Instant cleanupAfter, boolean ioUncertain,
            String safeFailureCode, Instant createdAt, Instant updatedAt) {}
    public record ReceiptRow(UUID userId, String operation, UUID requestId, String payloadHash, String state,
            Long resultVersion, UUID objectId, String safeFailureCode, Instant createdAt) {}
}
