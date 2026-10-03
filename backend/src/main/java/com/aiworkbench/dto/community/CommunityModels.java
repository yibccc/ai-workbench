package com.aiworkbench.dto.community;

import com.aiworkbench.enums.CommunityPostType;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CommunityModels {
    private CommunityModels() {}

    public record Author(UUID id, String nickname, String bio) {}
    public record Profile(String nickname, String bio, long version) {}
    public record UpdateProfile(@Size(max = 40) String nickname, @Size(max = 500) String bio,
            @NotNull @PositiveOrZero Long version) {}
    public record AttachmentInfo(UUID id, String kind, String fileName, String contentType, long size,
            String state, String safeFailureCode) {}
    public record PostCard(UUID id, CommunityPostType type, LocalDate businessDate, String title, String summary,
            Instant firstPublishedAt, Instant publishedAt, UUID revisionId, int revisionNo,
            Author author, List<AttachmentInfo> attachments) {}
    public record PostDetail(UUID id, CommunityPostType type, LocalDate businessDate, String title, String summary,
            String bodyMarkdown, Instant firstPublishedAt, Instant publishedAt, UUID revisionId, int revisionNo,
            Author author, List<AttachmentInfo> attachments) {}
}
