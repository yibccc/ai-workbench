package com.aiworkbench.project;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private final ProjectMapper mapper;

    public ProjectService(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID id = UUID.randomUUID();
        try {
            mapper.insert(id, request.name().trim());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名的活动项目已存在", exception);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(boolean includeArchived) {
        return mapper.findAll(includeArchived).stream().map(ProjectRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return require(id).toResponse();
    }

    @Transactional
    public ProjectResponse rename(UUID id, UpdateProjectRequest request) {
        require(id);
        try {
            mapper.rename(id, request.name().trim());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名的活动项目已存在", exception);
        }
        return get(id);
    }

    @Transactional
    public ProjectResponse archive(UUID id) {
        require(id);
        mapper.archive(id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public void requireActive(UUID id) {
        if (!"ACTIVE".equals(require(id).status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "归档项目不能用于新记录");
        }
    }

    private ProjectRow require(UUID id) {
        return mapper.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
    }
}
