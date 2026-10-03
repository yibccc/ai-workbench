package com.aiworkbench.entity.community;

import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence projections; reader rows deliberately omit private source selections. */
public final class CommunityRows {
    private CommunityRows() {}
    public record Post(UUID id, UUID ownerId, CommunityPostType type, CommunityPostStatus status, long version,
            UUID currentRevisionId, Instant firstPublishedAt, Instant createdAt, Instant updatedAt) {}
    public record Draft(UUID postId, UUID ownerId, CommunityPostType type, LocalDate businessDate,
            String title, String summary, String bodyMarkdown, String sourceSelection, Instant savedAt) {}
    public record Revision(UUID id, UUID postId, UUID ownerId, int revisionNo, CommunityPostType type,
            LocalDate businessDate, String title, String summary, String bodyMarkdown, String sourceSelection,
            UUID requestId, String requestFingerprint, long resultVersion, Instant publishedAt) {}
    public record PublicPost(UUID id, UUID ownerId, CommunityPostType type, LocalDate businessDate,
            String title, String summary, String bodyMarkdown, Instant firstPublishedAt, Instant publishedAt,
            UUID revisionId, int revisionNo, String nickname, String bio) {}
    public record Profile(UUID ownerId, String nickname, String bio, long version) {}
    public record Author(UUID id, String nickname, String bio) {}
    public record Moderation(String reason, Instant occurredAt) {}
    public record OwnerCard(UUID postId, CommunityPostType type, CommunityPostStatus status, long version,
            LocalDate businessDate, String title, String summary, Instant updatedAt, Instant firstPublishedAt,
            UUID currentRevisionId, Integer currentRevisionNo, boolean hasUnpublishedChanges,
            String moderationReason, Instant moderatedAt) {}
}
