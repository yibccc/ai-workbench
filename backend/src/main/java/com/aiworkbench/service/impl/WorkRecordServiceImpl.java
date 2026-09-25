package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.UpdateWorkRecordRequest;
import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.entity.record.WorkRecordRow;
import com.aiworkbench.enums.WorkRecordSource;
import com.aiworkbench.mapper.WorkRecordMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.WorkRecordService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkRecordServiceImpl implements WorkRecordService {
    private final WorkRecordMapper mapper;
    private final ProjectService projectService;
    private final ZoneId zoneId;

    public WorkRecordServiceImpl(
            WorkRecordMapper mapper,
            ProjectService projectService,
            @Value("${workbench.zone-id:Asia/Shanghai}") String zoneId) {
        this.mapper = mapper;
        this.projectService = projectService;
        this.zoneId = ZoneId.of(zoneId);
    }

    @Transactional
    public WorkRecordResponse create(CreateWorkRecordRequest request) {
        validateProject(request.projectId());
        UUID id = UUID.randomUUID();
        mapper.insert(CurrentUser.requireId(), id, request.projectId(), request.content().trim(), request.occurredAt());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<WorkRecordResponse> list(LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now(zoneId) : date;
        Instant start = effectiveDate.atStartOfDay(zoneId).toInstant();
        Instant end = effectiveDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        return mapper.findBetween(CurrentUser.requireId(), start, end).stream().map(WorkRecordRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkRecordResponse> page(LocalDate date, int page, int size) {
        LocalDate effectiveDate = date == null ? LocalDate.now(zoneId) : date;
        Instant start = effectiveDate.atStartOfDay(zoneId).toInstant();
        Instant end = effectiveDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        UUID userId = CurrentUser.requireId();
        return PageQueries.select(page, size, () -> mapper.findPageBetween(userId, start, end), WorkRecordRow::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkRecordResponse get(UUID id) {
        return require(id).toResponse();
    }

    @Transactional
    public WorkRecordResponse update(UUID id, UpdateWorkRecordRequest request) {
        WorkRecordRow current = require(id);
        requireManual(current);
        if (request.projectId() != null && !request.projectId().equals(current.projectId())) {
            projectService.requireActive(request.projectId());
        }
        if (mapper.update(CurrentUser.requireId(), id, request.projectId(), request.content().trim(), request.occurredAt()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工作记录状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        WorkRecordRow current = require(id);
        requireManual(current);
        if (mapper.delete(CurrentUser.requireId(), id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工作记录状态已变化，请刷新后重试");
        }
    }

    private void validateProject(UUID projectId) {
        if (projectId != null) {
            projectService.requireActive(projectId);
        }
    }

    private WorkRecordRow require(UUID id) {
        return mapper.findById(CurrentUser.requireId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工作记录不存在"));
    }

    private void requireManual(WorkRecordRow row) {
        if (row.source() != WorkRecordSource.MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "自动完成记录请通过待办操作维护");
        }
    }
}
