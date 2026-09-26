package com.aiworkbench.service.impl;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.dto.input.CreateInputRequest;
import com.aiworkbench.dto.input.InputResponse;
import com.aiworkbench.dto.input.PreparedCapture;
import com.aiworkbench.dto.input.ProcessingClaim;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.entity.input.InputRow;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.exception.DeepSeekNotConfiguredException;
import com.aiworkbench.mapper.InputMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.InputPersistenceService;
import com.aiworkbench.service.InputService;
import com.aiworkbench.service.ProjectService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class InputServiceImpl implements InputService {
    private static final int MAX_ITEMS = 50;
    private final InputMapper mapper;
    private final InputPersistenceService persistence;
    private final WorkbenchAiGateway aiGateway;
    private final ProjectService projectService;
    private final ZoneId zoneId;
    private final TaskExecutor taskExecutor;
    private final Set<UUID> processingTokens = ConcurrentHashMap.newKeySet();

    public InputServiceImpl(InputMapper mapper, InputPersistenceService persistence, WorkbenchAiGateway aiGateway,
                        ProjectService projectService, TaskExecutor taskExecutor,
                        @Value("${workbench.zone-id:Asia/Shanghai}") String zoneId) {
        this.mapper = mapper; this.persistence = persistence; this.aiGateway = aiGateway;
        this.projectService = projectService; this.taskExecutor = taskExecutor; this.zoneId = ZoneId.of(zoneId);
    }

    public InputResponse create(CreateInputRequest request) {
        ProcessingClaim claim = persistence.createOrGet(request.requestId().trim(), request.content().trim(), Instant.now(), zoneId);
        if (claim.owner()) schedule(claim.row().userId(), claim.row().id(), claim.token());
        return get(claim.row().id());
    }

    public InputResponse retry(UUID id) {
        ProcessingClaim claim = persistence.beginRetry(id, Instant.now());
        if (claim.owner()) schedule(claim.row().userId(), claim.row().id(), claim.token());
        return get(id);
    }

    public InputResponse revert(UUID id) {
        persistence.revert(id, Instant.now());
        return get(id);
    }

    public InputResponse get(UUID id) { return toResponse(persistence.require(id)); }

    private void schedule(UUID userId, UUID id, UUID token) {
        if (!processingTokens.add(token)) return;
        try {
            taskExecutor.execute(() -> {
                try { process(persistence.requireOwned(userId, id), token); }
                finally { processingTokens.remove(token); }
            });
        } catch (RuntimeException exception) {
            processingTokens.remove(token);
            persistence.fail(id, token, "AI 处理暂时不可用，请稍后重试", Instant.now());
        }
    }

    private void process(InputRow row, UUID token) {
        try {
            List<ProjectResponse> projects = projectService.listForUser(row.userId(), false);
            AiCaptureResult result = aiGateway.extract(row.content(), row.referenceAt(), ZoneId.of(row.zoneId()),
                    projects.stream().map(ProjectResponse::name).toList());
            PreparedCapture prepared = validate(result, projects, row.referenceAt());
            persistence.succeed(row.id(), token, prepared, Instant.now());
        } catch (RuntimeException exception) {
            persistence.fail(row.id(), token, safeFailure(exception), Instant.now());
        }
    }

    private PreparedCapture validate(AiCaptureResult result, List<ProjectResponse> projects, Instant referenceAt) {
        if (result == null || result.records() == null || result.tasks() == null) throw new IllegalArgumentException("模型结果缺少 records 或 tasks");
        if (result.records().size() + result.tasks().size() == 0) throw new IllegalArgumentException("模型没有提取出任何条目");
        if (result.records().size() + result.tasks().size() > MAX_ITEMS) throw new IllegalArgumentException("模型提取条目过多");
        Map<String, UUID> byName = new HashMap<>();
        projects.forEach(project -> byName.put(project.name().trim().toLowerCase(Locale.ROOT), project.id()));
        List<PreparedCapture.RecordItem> records = result.records().stream().map(item -> {
            String content = required(item.content(), 4000, "记录内容");
            Instant occurredAt = parseRequiredInstant(item.occurredAt(), "记录发生时间");
            return new PreparedCapture.RecordItem(projectId(item.projectName(), byName), content, occurredAt);
        }).toList();
        List<PreparedCapture.TaskItem> tasks = result.tasks().stream().map(item -> new PreparedCapture.TaskItem(
                projectId(item.projectName(), byName), required(item.title(), 240, "待办标题"),
                optional(item.notes(), 4000, "待办备注"), parseOptionalInstant(item.dueAt(), "待办截止时间"),
                priority(item.priority()))).toList();
        return new PreparedCapture(records, tasks);
    }

    private UUID projectId(String name, Map<String, UUID> projects) {
        return name == null || name.isBlank() ? null : projects.get(name.trim().toLowerCase(Locale.ROOT));
    }
    private TaskPriority priority(String value) {
        if (value == null) return TaskPriority.MEDIUM;
        try { return TaskPriority.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return TaskPriority.MEDIUM; }
    }
    private Instant parseRequiredInstant(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return parseInstant(value, label);
    }
    private Instant parseOptionalInstant(String value, String label) { return value == null || value.isBlank() ? null : parseInstant(value, label); }
    private Instant parseInstant(String value, String label) {
        try { return Instant.parse(value); } catch (DateTimeException exception) { throw new IllegalArgumentException(label + "必须是 ISO-8601 instant", exception); }
    }
    private String required(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return optional(value, max, label);
    }
    private String optional(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "过长");
        return normalized;
    }
    private String safeFailure(RuntimeException exception) {
        if (exception instanceof DeepSeekNotConfiguredException) return "DeepSeek 尚未配置";
        if (exception instanceof IllegalArgumentException) return "AI 返回内容未通过校验，请重试";
        return "AI 处理失败，可稍后重试（" + exception.getClass().getSimpleName() + "）";
    }

    private InputResponse toResponse(InputRow row) {
        List<InputResponse.GeneratedRecord> records = mapper.findRecords(row.userId(), row.id()).stream()
                .map(r -> new InputResponse.GeneratedRecord(r.id(), r.projectId(), r.projectName(), r.content(), r.occurredAt())).toList();
        List<InputResponse.GeneratedTask> tasks = mapper.findTasks(row.userId(), row.id()).stream()
                .map(t -> new InputResponse.GeneratedTask(t.id(), t.projectId(), t.projectName(), t.title(), t.notes(), t.dueAt(), t.priority(), t.version())).toList();
        return new InputResponse(row.id(), row.clientRequestId(), row.content(), row.referenceAt(), row.zoneId(), row.status(),
                row.errorMessage(), row.attemptCount(), row.completedAt(), row.createdAt(), row.updatedAt(), records, tasks);
    }
}
