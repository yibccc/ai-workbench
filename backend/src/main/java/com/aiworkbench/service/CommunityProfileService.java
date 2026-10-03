package com.aiworkbench.service;

import com.aiworkbench.dto.community.CommunityModels.*;

public interface CommunityProfileService {
    Profile get();
    Profile update(UpdateProfile request);
}
