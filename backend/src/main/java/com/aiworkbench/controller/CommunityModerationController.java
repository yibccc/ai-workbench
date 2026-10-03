package com.aiworkbench.controller;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.service.CommunityModerationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/community/posts")
public class CommunityModerationController {
    private final CommunityModerationService service;

    public CommunityModerationController(CommunityModerationService service) { this.service = service; }

    @PostMapping("/{postId}/hide")
    public Hidden hide(@PathVariable UUID postId, @Valid @RequestBody HidePost request) { return service.hide(postId, request); }
}
