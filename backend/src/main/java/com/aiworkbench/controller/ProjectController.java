package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.dto.project.UpdateProjectRequest;
import com.aiworkbench.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(includeArchived);
    }

    @GetMapping("/page")
    public PageResponse<ProjectResponse> page(@RequestParam(defaultValue = "false") boolean includeArchived,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.page(includeArchived, q, page, size);
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public ProjectResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        return service.rename(id, request);
    }

    @PostMapping("/{id}/archive")
    public ProjectResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }
}
