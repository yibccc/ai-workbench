package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import java.time.LocalDate;
import java.util.UUID;

public interface PublishingService {
    OwnerPost create(CreatePost request);
    OwnerPost get(UUID postId);
    PageResponse<OwnerPostCard> page(CommunityPostStatus status, CommunityPostType type, int page, int size);
    Saved save(UUID postId, SaveDraft request);
    Published publish(UUID postId, PublishPost request);
    State withdraw(UUID postId, Version request);
    PageResponse<Material> sources(LocalDate date, int page, int size);
    OwnerPost shareDraft(ShareDraft request);
}
