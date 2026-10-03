package com.aiworkbench.entity.community;

import java.time.Instant;
import java.util.UUID;
import com.aiworkbench.dto.community.CommunityModels.AttachmentInfo;

public record AttachmentRow(UUID id, UUID postId, UUID ownerId, UUID requestId, String objectKey,
        String originalFilename, String verifiedContentType, long actualSize, String sha256, String state,
        UUID attemptToken, Instant reservationExpiresAt, long resultVersion, String safeFailureCode,
        Instant createdAt, Instant updatedAt) {
    public AttachmentInfo info() {
        String kind = verifiedContentType.startsWith("image/") ? "IMAGE"
                : verifiedContentType.equals("application/pdf") ? "PDF" : "MD";
        String displayFailure=state.equals("DELETE_FAILED")?"STORAGE_UNAVAILABLE":state.equals("FAILED")?safeFailureCode:null;
        return new AttachmentInfo(id, kind, originalFilename, verifiedContentType, actualSize, state, displayFailure);
    }
}
