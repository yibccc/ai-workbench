package com.aiworkbench.dto.resume;

import com.aiworkbench.config.ResumeJson;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.UUID;
import com.aiworkbench.storage.ObjectStorage.StoredObject;

public final class ResumeModels {
    private ResumeModels() {}
    public enum Mode { EDIT_CURRENT, PASTE }
    public record Save(@JsonDeserialize(using=ResumeJson.ExactMode.class) Mode mode, String markdownText,
            @JsonDeserialize(using=ResumeJson.ExactVersion.class) Long expectedVersion, UUID requestId) {}
    public record OriginalFile(UUID id, String fileName, long size, String sha256) {}
    public record Current(boolean exists, long version, String markdownText, String sourceKind, OriginalFile originalFile) {}
    public record Snapshot(boolean exists, long version, String markdownText, String contentSha256) {}
    /** Immutable outcome, never an authorization to reread a replaced original. */
    public record Receipt(UUID requestId, String operation, String state, Long resultVersion, UUID objectId, String safeFailureCode) {}
    public record Result(UUID objectId, String state, String safeFailureCode) {}
    public record Maintenance(long version, List<Result> results) {}
    public record Download(OriginalFile file, StoredObject object) {}
}
