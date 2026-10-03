package com.aiworkbench.dto.publishing;

import com.aiworkbench.dto.community.CommunityModels.PostDetail;
import com.aiworkbench.dto.community.CommunityModels.AttachmentInfo;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.enums.WorkRecordSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PublishingModels {
    private PublishingModels() {}

    public record CreatePost(@NotNull CommunityPostType type, LocalDate businessDate,
            @Size(max = 200) String title, @Size(max = 500) String summary,
            @Size(max = 100000) String bodyMarkdown) {}
    public record SaveDraft(@NotNull @PositiveOrZero Long version, @NotNull CommunityPostType type,
            LocalDate businessDate, @Size(max = 200) String title, @Size(max = 500) String summary,
            @Size(max = 100000) String bodyMarkdown, List<@NotNull UUID> attachmentIds) {}
    public record PublishPost(@NotNull UUID requestId, @NotNull @PositiveOrZero Long version,
            @NotNull String visibility, @NotNull CommunityPostType type, LocalDate businessDate,
            @Size(max = 200) String title, @Size(max = 500) String summary,
            @Size(max = 100000) String bodyMarkdown, List<@NotNull UUID> attachmentIds) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public record HidePost(@NotNull UUID expectedRevisionId, @NotBlank @Size(max = 1000) String reason) {}
    public record Draft(CommunityPostType type, LocalDate businessDate, String title, String summary,
            String bodyMarkdown, Instant savedAt, List<UUID> attachmentIds, List<AttachmentInfo> attachments) {}
    public record Moderation(String reason, Instant occurredAt) {}
    public record OwnerPost(UUID postId, CommunityPostType type, CommunityPostStatus status, long version,
            Draft draft, PostDetail currentPublished, Moderation moderation, List<AttachmentInfo> attachments) {}
    public record OwnerPostCard(UUID postId, CommunityPostType type, CommunityPostStatus status, long version,
            LocalDate businessDate, String title, String summary, Instant updatedAt, Instant firstPublishedAt,
            UUID currentRevisionId, Integer currentRevisionNo, boolean hasUnpublishedChanges, Moderation moderation) {}
    public record Saved(UUID postId, long version, Instant savedAt) {}
    public record Published(UUID postId, long version, UUID revisionId, int revisionNo,
            Instant publishedAt, Instant firstPublishedAt) {}
    public record State(UUID postId, CommunityPostStatus status, long version) {}
    public record Hidden(UUID postId, CommunityPostStatus status) {}
    public enum MaterialField { CONTENT, COMPLETION_RESULT, PROGRESS }
    public record Selection(@NotNull UUID recordId, @NotEmpty List<@NotNull MaterialField> fields) {}
    public record ShareDraft(@NotNull LocalDate date, @NotEmpty List<@Valid Selection> selections,
            boolean includeFocus) {}
    public record Material(UUID id, WorkRecordSource source, String content, String completionResult,
            String progress, Long focusMs, String projectName, Instant occurredAt) {}
}
