package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.community.CommunityModels.*;
import com.aiworkbench.enums.CommunityPostType;
import java.util.UUID;

public interface CommunityService {
    PageResponse<PostCard> page(CommunityPostType type, UUID authorId, int page, int size);
    PostDetail get(UUID postId);
    Author author(UUID authorId);
}
