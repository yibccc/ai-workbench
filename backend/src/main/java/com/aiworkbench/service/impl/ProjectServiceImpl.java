package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.dto.project.UpdateProjectRequest;
import com.aiworkbench.entity.project.ProjectRow;
import com.aiworkbench.mapper.ProjectMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final ProjectMapper mapper;

    public ProjectServiceImpl(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID id = UUID.randomUUID();
        try {
            mapper.insert(CurrentUser.requireId(), id, request.name().trim());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名的活动项目已存在", exception);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(boolean includeArchived) {
        return listForUser(CurrentUser.requireId(), includeArchived);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listForUser(UUID userId, boolean includeArchived) {
        return mapper.findAll(userId, includeArchived).stream().map(ProjectRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> page(boolean includeArchived, String q, int page, int size) {
        String query = q == null ? null : q.trim();
        UUID userId = CurrentUser.requireId();
        return PageQueries.select(page, size, () -> mapper.findPage(userId, includeArchived, query), ProjectRow::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return require(id).toResponse();
    }

    @Transactional
    public ProjectResponse rename(UUID id, UpdateProjectRequest request) {
        require(id);
        try {
            mapper.rename(CurrentUser.requireId(), id, request.name().trim());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名的活动项目已存在", exception);
        }
        return get(id);
    }

    @Transactional
    public ProjectResponse archive(UUID id) {
        require(id);
        mapper.archive(CurrentUser.requireId(), id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public void requireActive(UUID id) {
        if (!"ACTIVE".equals(require(id).status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "归档项目不能用于新记录");
        }
    }

    private ProjectRow require(UUID id) {
        return mapper.findById(CurrentUser.requireId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
    }
}
