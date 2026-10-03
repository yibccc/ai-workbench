package com.aiworkbench.service.impl;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.CommunityPostMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.CommunityModerationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityModerationServiceImpl implements CommunityModerationService {
    private final CommunityPostMapper mapper;
    private final WorkbenchEventHub events;

    public CommunityModerationServiceImpl(CommunityPostMapper mapper, WorkbenchEventHub events) {
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public Hidden hide(UUID postId, HidePost request) {
        var actor = CurrentUser.require();
        if (!"ADMIN".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        var post = mapper.lockForModeration(postId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "发布内容不可访问"));
        if (post.status() != CommunityPostStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "发布内容不可访问");
        }
        if (!post.currentRevisionId().equals(request.expectedRevisionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "发布版本已变化，请刷新后重试");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写有效的下架理由");
        }
        Instant now = Instant.now();
        mapper.insertModeration(UUID.randomUUID(), postId, post.ownerId(), actor.userId(),
                post.currentRevisionId(), reason, now);
        if (mapper.updateAggregate(post.ownerId(), postId, post.version(), CommunityPostStatus.HIDDEN,
                post.currentRevisionId(), post.firstPublishedAt(), now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "发布状态已变化，请刷新后重试");
        }
        events.publishAfterCommit(post.ownerId(), "POST", postId, "HIDDEN");
        return new Hidden(postId, CommunityPostStatus.HIDDEN);
    }
}
