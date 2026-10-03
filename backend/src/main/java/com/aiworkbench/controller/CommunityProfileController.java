package com.aiworkbench.controller;

import com.aiworkbench.dto.community.CommunityModels.*;
import com.aiworkbench.service.CommunityProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/community/profile")
public class CommunityProfileController {
    private final CommunityProfileService service;

    public CommunityProfileController(CommunityProfileService service) { this.service = service; }

    @GetMapping
    public Profile get() { return service.get(); }

    @PutMapping
    public Profile update(@Valid @RequestBody UpdateProfile request) { return service.update(request); }
}
