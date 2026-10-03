package com.aiworkbench.service;

import com.aiworkbench.dto.publishing.PublishingModels.HidePost;
import com.aiworkbench.dto.publishing.PublishingModels.Hidden;
import java.util.UUID;

public interface CommunityModerationService {
    Hidden hide(UUID postId, HidePost request);
}
