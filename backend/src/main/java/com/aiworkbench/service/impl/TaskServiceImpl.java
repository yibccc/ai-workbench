package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskEventResponse;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.dto.task.TaskVersionRequest;
import com.aiworkbench.dto.task.UpdateCompletionResultRequest;
import com.aiworkbench.dto.task.UpdateTaskRequest;
import com.aiworkbench.entity.task.TaskEventRow;
import com.aiworkbench.entity.task.TaskRow;
import com.aiworkbench.enums.TaskDueFilter;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import com.aiworkbench.mapper.TaskMapper;
import com.aiworkbench.mapper.WorkRecordMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.TaskService;
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
public class TaskServiceImpl implements TaskService {
    private final TaskMapper mapper;
    private final ProjectService projectService;
    private final WorkRecordMapper workRecordMapper;
    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public TaskServiceImpl(
            TaskMapper mapper,
            ProjectService projectService,
            WorkRecordMapper workRecordMapper,
            @Value("${workbench.zone-id:Asia/Shanghai}") String zoneId) {
        this(mapper, projectService, workRecordMapper, ZoneId.of(zoneId), Clock.systemUTC());
    }

    TaskServiceImpl(TaskMapper mapper, ProjectService projectService, WorkRecordMapper workRecordMapper,
            ZoneId zoneId, Clock clock) {
        this.mapper = mapper;
        this.projectService = projectService;
        this.workRecordMapper = workRecordMapper;
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
        mapper.insert(CurrentUser.requireId(), id, request.projectId(), request.title().trim(), normalizeNotes(request.notes()),
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
        if (projectId != null) projectService.get(projectId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        Instant todayStart = today.atStartOfDay(zoneId).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        TaskDueFilter effectiveDueFilter = dueFilter == null ? TaskDueFilter.ALL : dueFilter;
        return mapper.findAll(CurrentUser.requireId(), status, projectId, unassigned, priority, effectiveDueFilter,
                now, todayStart, tomorrowStart).stream().map(TaskRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> page(TaskStatus status, UUID projectId, boolean unassigned,
                                           TaskPriority priority, TaskDueFilter dueFilter,
                                           int page, int size) {
        if (unassigned && projectId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未分类筛选不能与项目筛选同时使用");
        }
        if (projectId != null) projectService.get(projectId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        Instant todayStart = today.atStartOfDay(zoneId).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        TaskDueFilter effective = dueFilter == null ? TaskDueFilter.ALL : dueFilter;
        UUID userId = CurrentUser.requireId();
        return PageQueries.select(page, size, () -> mapper.findPage(userId, status, projectId, unassigned,
                priority, effective, now, todayStart, tomorrowStart), TaskRow::toResponse);
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
        int changed = mapper.update(CurrentUser.requireId(), id, request.projectId(), request.title().trim(),
                normalizeNotes(request.notes()), request.dueAt(), request.priority(), request.version());
        if (changed == 0) {
            throw versionConflict();
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        TaskRow current = require(id);
        Instant now = clock.instant();
        if (mapper.softDelete(CurrentUser.requireId(), id, version, now) == 0) {
            throw versionConflict();
        }
        workRecordMapper.invalidateTaskCompletion(CurrentUser.requireId(), id, now);
        insertEvent(current, "DELETED", current.status(), current.status(), version + 1, "", now);
    }

    @Transactional
    public TaskResponse complete(UUID id, CompleteTaskRequest request) {
        TaskRow current = require(id);
        if (current.status() == TaskStatus.COMPLETED) {
            return current.toResponse();
        }

        Instant now = clock.instant();
        if (mapper.complete(CurrentUser.requireId(), id, request.version(), now) == 0) {
            TaskRow latest = require(id);
            if (latest.status() == TaskStatus.COMPLETED) {
                return latest.toResponse();
            }
            throw versionConflict();
        }
        String result = normalizeResult(request.result());
        workRecordMapper.insertTaskCompletion(CurrentUser.requireId(), UUID.randomUUID(), current.projectId(), id,
                completionContent(current.title()), result, now);
        insertEvent(current, "COMPLETED", TaskStatus.PENDING, TaskStatus.COMPLETED,
                request.version() + 1, result, now);
        return get(id);
    }

    @Transactional
    public TaskResponse reopen(UUID id, TaskVersionRequest request) {
        TaskRow current = require(id);
        if (current.status() == TaskStatus.PENDING) {
            return current.toResponse();
        }
        Instant now = clock.instant();
        if (mapper.reopen(CurrentUser.requireId(), id, request.version()) == 0) {
            TaskRow latest = require(id);
            if (latest.status() == TaskStatus.PENDING) {
                return latest.toResponse();
            }
            throw versionConflict();
        }
        workRecordMapper.invalidateTaskCompletion(CurrentUser.requireId(), id, now);
        insertEvent(current, "REOPENED", TaskStatus.COMPLETED, TaskStatus.PENDING,
                request.version() + 1, current.completionResult(), now);
        return get(id);
    }

    @Transactional
    public TaskResponse updateCompletionResult(UUID id, UpdateCompletionResultRequest request) {
        TaskRow current = require(id);
        if (current.status() != TaskStatus.COMPLETED || current.completionRecordId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已完成待办可以补充完成结果");
        }
        String result = normalizeResult(request.result());
        Instant now = clock.instant();
        if (mapper.touchCompletionResult(CurrentUser.requireId(), id, request.version()) == 0) {
            throw versionConflict();
        }
        if (workRecordMapper.updateCompletionResult(CurrentUser.requireId(), id, result, now) != 1) {
            throw new IllegalStateException("待办的当前完成记录缺失");
        }
        insertEvent(current, "COMPLETION_RESULT_UPDATED", TaskStatus.COMPLETED, TaskStatus.COMPLETED,
                request.version() + 1, result, now);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<TaskEventResponse> events(UUID id) {
        requireAny(id);
        return mapper.findEvents(CurrentUser.requireId(), id).stream().map(TaskEventRow::toResponse).toList();
    }

    private TaskRow require(UUID id) {
        return mapper.findById(CurrentUser.requireId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在"));
    }

    private TaskRow requireAny(UUID id) {
        return mapper.findAnyById(CurrentUser.requireId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在"));
    }

    private void insertEvent(TaskRow task, String type, TaskStatus fromStatus, TaskStatus toStatus,
            long version, String result, Instant occurredAt) {
        mapper.insertEvent(CurrentUser.requireId(), UUID.randomUUID(), task.id(), type, task.title(), task.projectId(),
                fromStatus, toStatus, version, result, occurredAt);
    }

    private String completionContent(String title) {
        return "完成待办：" + title;
    }

    private String normalizeResult(String result) {
        return result == null ? "" : result.trim();
    }

    private String normalizeNotes(String notes) {
        return notes == null ? "" : notes.trim();
    }

    private ResponseStatusException versionConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "待办已被其他操作修改，请刷新后重试");
    }
}
