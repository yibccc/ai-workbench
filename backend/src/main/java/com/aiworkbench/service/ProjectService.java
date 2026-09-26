package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.dto.project.UpdateProjectRequest;
import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectResponse create(CreateProjectRequest request);
    List<ProjectResponse> list(boolean includeArchived);
    List<ProjectResponse> listForUser(UUID userId, boolean includeArchived);
    PageResponse<ProjectResponse> page(boolean includeArchived, String q, int page, int size);
    ProjectResponse get(UUID id);
    ProjectResponse rename(UUID id, UpdateProjectRequest request);
    ProjectResponse archive(UUID id);
    void requireActive(UUID id);
}
