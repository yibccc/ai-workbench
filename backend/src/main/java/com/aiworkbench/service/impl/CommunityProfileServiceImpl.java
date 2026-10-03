package com.aiworkbench.service.impl;

import com.aiworkbench.dto.community.CommunityModels.*;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.CommunityProfileMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.CommunityProfileService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityProfileServiceImpl implements CommunityProfileService {
    private final CommunityProfileMapper mapper;
    private final WorkbenchEventHub events;

    public CommunityProfileServiceImpl(CommunityProfileMapper mapper, WorkbenchEventHub events) {
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Profile get() {
        return read(CurrentUser.requireId());
    }

    @Transactional
    public Profile update(UpdateProfile request) {
        UUID owner = CurrentUser.requireId();
        String nickname = request.nickname() == null || request.nickname().isBlank() ? null : request.nickname().trim();
        String bio = request.bio() == null ? "" : request.bio();
        if ((nickname != null && nickname.length() > 40) || bio.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称或简介过长");
        }
        int changed = request.version() == 0
                ? mapper.insertProfile(owner, nickname, bio)
                : mapper.updateProfile(owner, nickname, bio, request.version());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "作者资料已变化，请刷新后重试");
        events.publishAfterCommit(owner, "COMMUNITY_PROFILE", owner, "UPDATED");
        return read(owner);
    }

    private Profile read(UUID owner) {
        return mapper.findProfile(owner).map(row -> new Profile(
                row.nickname() == null ? "未设置昵称" : row.nickname(), row.bio(), row.version()))
                .orElseGet(() -> new Profile("未设置昵称", "", 0));
    }
}
