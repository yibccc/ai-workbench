package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.service.PublishingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class PublishingController {
    private final PublishingService service;

    public PublishingController(PublishingService service) { this.service = service; }

    @GetMapping("/posts/page")
    public PageResponse<OwnerPostCard> page(@RequestParam(required = false) CommunityPostStatus status,
            @RequestParam(required = false) CommunityPostType type, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return service.page(status, type, page, size);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerPost create(@Valid @RequestBody CreatePost request) { return service.create(request); }

    @GetMapping("/posts/{postId}")
    public OwnerPost get(@PathVariable UUID postId) { return service.get(postId); }

    @PutMapping("/posts/{postId}")
    public Saved save(@PathVariable UUID postId, @Valid @RequestBody SaveDraft request) { return service.save(postId, request); }

    @PostMapping("/posts/{postId}/publish")
    public Published publish(@PathVariable UUID postId, @Valid @RequestBody PublishPost request) { return service.publish(postId, request); }

    @PostMapping("/posts/{postId}/withdraw")
    public State withdraw(@PathVariable UUID postId, @Valid @RequestBody Version request) { return service.withdraw(postId, request); }

    @GetMapping("/community/sources/page")
    public PageResponse<Material> sources(@RequestParam LocalDate date, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) { return service.sources(date, page, size); }

    @PostMapping("/community/share-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerPost shareDraft(@Valid @RequestBody ShareDraft request) { return service.shareDraft(request); }
}
