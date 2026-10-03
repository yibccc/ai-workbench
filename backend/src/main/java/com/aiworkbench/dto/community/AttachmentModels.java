package com.aiworkbench.dto.community;

import java.util.List;
import java.util.UUID;
import com.aiworkbench.dto.community.CommunityModels.AttachmentInfo;

public final class AttachmentModels {
    private AttachmentModels() {}
    public record Upload(AttachmentInfo attachment, long version) {}
    public record Result(UUID attachmentId, String state, String safeFailureCode) {}
    public record Maintenance(long version, List<Result> results) {}
}
