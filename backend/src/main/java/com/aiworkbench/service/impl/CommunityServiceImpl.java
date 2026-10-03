package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.community.CommunityModels.*;
import com.aiworkbench.entity.community.CommunityRows.PublicPost;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.mapper.CommunityPostMapper;
import com.aiworkbench.mapper.AttachmentMapper;
import com.aiworkbench.mapper.CommunityProfileMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.CommunityService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommunityServiceImpl implements CommunityService {
    private final CommunityPostMapper posts;
    private final CommunityProfileMapper profiles;
    private final AttachmentMapper attachments;

    public CommunityServiceImpl(CommunityPostMapper posts, CommunityProfileMapper profiles, AttachmentMapper attachments) {
        this.posts = posts;
        this.profiles = profiles;
        this.attachments = attachments;
    }

    public PageResponse<PostCard> page(CommunityPostType type, UUID authorId, int page, int size) {
        CurrentUser.requireId();
        return PageQueries.select(page, size, () -> posts.findPublishedPage(type, authorId), this::card);
    }

    public PostDetail get(UUID postId) {
        CurrentUser.requireId();
        return detail(posts.findPublished(postId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "发布内容不可访问")));
    }

    public Author author(UUID authorId) {
        CurrentUser.requireId();
        var row = profiles.findAuthor(authorId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作者不存在"));
        return new Author(row.id(), row.nickname(), row.bio());
    }

    private PostCard card(PublicPost row) {
        String summary = row.summary().isBlank()
                ? row.bodyMarkdown().substring(0, Math.min(row.bodyMarkdown().length(), 240)) : row.summary();
        return new PostCard(row.id(), row.type(), row.businessDate(), row.title(), summary, row.firstPublishedAt(),
                row.publishedAt(), row.revisionId(), row.revisionNo(),
                new Author(row.ownerId(), row.nickname(), row.bio()), attachments.findPublished(row.id(), row.revisionId()).stream().map(a -> a.info()).toList());
    }

    private PostDetail detail(PublicPost row) {
        return new PostDetail(row.id(), row.type(), row.businessDate(), row.title(), row.summary(), row.bodyMarkdown(),
                row.firstPublishedAt(), row.publishedAt(), row.revisionId(), row.revisionNo(),
                new Author(row.ownerId(), row.nickname(), row.bio()), attachments.findPublished(row.id(), row.revisionId()).stream().map(a -> a.info()).toList());
    }
}
