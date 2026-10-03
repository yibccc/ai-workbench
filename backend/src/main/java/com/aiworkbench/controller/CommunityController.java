package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.community.CommunityModels.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.service.CommunityService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService service;

    public CommunityController(CommunityService service) { this.service = service; }

    @GetMapping("/posts/page")
    public PageResponse<PostCard> page(@RequestParam(required = false) CommunityPostType type,
            @RequestParam(required = false) UUID authorId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) { return service.page(type, authorId, page, size); }

    @GetMapping("/posts/{postId}")
    public PostDetail get(@PathVariable UUID postId) { return service.get(postId); }

    @GetMapping("/authors/{authorId}")
    public Author author(@PathVariable UUID authorId) { return service.author(authorId); }
}
