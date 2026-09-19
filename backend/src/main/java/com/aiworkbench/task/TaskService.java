package com.aiworkbench.task;

import com.aiworkbench.project.ProjectService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
    private final TaskMapper mapper;
    private final ProjectService projectService;
    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public TaskService(
            TaskMapper mapper,
            ProjectService projectService,
            @Value("${workbench.zone-id:Asia/Shanghai}") String zoneId) {
        this(mapper, projectService, ZoneId.of(zoneId), Clock.systemUTC());
    }

    TaskService(TaskMapper mapper, ProjectService projectService, ZoneId zoneId, Clock clock) {
        this.mapper = mapper;
        this.projectService = projectService;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        if (request.projectId() != null) {
            projectService.requireActive(request.projectId());
        }
        UUID id = UUID.randomUUID();
        TaskPriority priority = request.priority() == null ? TaskPriority.MEDIUM : request.priority();
        mapper.insert(id, request.projectId(), request.title().trim(), normalizeNotes(request.notes()),
                request.dueAt(), priority);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(
            TaskStatus status,
            UUID projectId,
            boolean unassigned,
            TaskPriority priority,
            TaskDueFilter dueFilter) {
        if (unassigned && projectId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未分类筛选不能与项目筛选同时使用");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        Instant todayStart = today.atStartOfDay(zoneId).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        TaskDueFilter effectiveDueFilter = dueFilter == null ? TaskDueFilter.ALL : dueFilter;
        return mapper.findAll(status, projectId, unassigned, priority, effectiveDueFilter,
                now, todayStart, tomorrowStart).stream().map(TaskRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id) {
        return require(id).toResponse();
    }

    @Transactional
    public TaskResponse update(UUID id, UpdateTaskRequest request) {
        TaskRow current = require(id);
        if (request.projectId() != null && !request.projectId().equals(current.projectId())) {
            projectService.requireActive(request.projectId());
        }
        int changed = mapper.update(id, request.projectId(), request.title().trim(),
                normalizeNotes(request.notes()), request.dueAt(), request.priority(), request.version());
        if (changed == 0) {
            throw versionConflict();
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        require(id);
        if (mapper.delete(id, version) == 0) {
            throw versionConflict();
        }
    }

    private TaskRow require(UUID id) {
        return mapper.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在"));
    }

    private String normalizeNotes(String notes) {
        return notes == null ? "" : notes.trim();
    }

    private ResponseStatusException versionConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "待办已被其他操作修改，请刷新后重试");
    }
}
