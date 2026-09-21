package com.aiworkbench.service.impl;

import com.aiworkbench.config.DeepSeekProperties;
import com.aiworkbench.dto.input.PreparedCapture;
import com.aiworkbench.dto.input.ProcessingClaim;
import com.aiworkbench.entity.input.InputRow;
import com.aiworkbench.enums.InputStatus;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.InputMapper;
import com.aiworkbench.service.InputPersistenceService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InputPersistenceServiceImpl implements InputPersistenceService {
    private static final String RECOVERY_MESSAGE = "服务重启前的处理已中断，请重试";
    private final InputMapper mapper;
    private final Duration leaseDuration;
    private final WorkbenchEventHub events;

    public InputPersistenceServiceImpl(InputMapper mapper,
            @Value("${workbench.capture.lease-duration:PT5M}") Duration leaseDuration,
            DeepSeekProperties deepSeekProperties, WorkbenchEventHub events) {
        if (leaseDuration.isZero() || leaseDuration.isNegative()
                || leaseDuration.compareTo(deepSeekProperties.requestTimeout()) <= 0) {
            throw new IllegalArgumentException("AI 处理租约必须为正数且长于模型调用超时");
        }
        this.mapper = mapper;
        this.leaseDuration = leaseDuration;
        this.events = events;
    }

    @Transactional
    public ProcessingClaim createOrGet(String requestId, String content, Instant referenceAt, ZoneId zoneId) {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        boolean owner = mapper.insert(id, requestId, content, referenceAt, zoneId.getId(), token,
                referenceAt.plus(leaseDuration)) == 1;
        InputRow row = mapper.findByRequestId(requestId).orElseThrow();
        if (owner) events.publishAfterCommit("INPUT", row.id(), "PROCESSING");
        return new ProcessingClaim(row, owner ? token : null, owner);
    }

    @Transactional
    public ProcessingClaim beginRetry(UUID id, Instant now) {
        InputRow current = require(id);
        if (current.status() != InputStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前输入不能重试");
        }
        UUID token = UUID.randomUUID();
        boolean owner = mapper.beginRetry(id, token, now.plus(leaseDuration)) == 1;
        InputRow latest = require(id);
        if (owner) events.publishAfterCommit("INPUT", id, "PROCESSING");
        if (!owner && latest.status() != InputStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "输入状态已变化，请刷新后重试");
        }
        return new ProcessingClaim(latest, owner ? token : null, owner);
    }

    @Transactional
    public void succeed(UUID inputId, UUID token, PreparedCapture capture, Instant completedAt) {
        for (PreparedCapture.RecordItem item : capture.records()) {
            UUID id = UUID.randomUUID();
            mapper.insertRecord(id, inputId, item.projectId(), item.content(), item.occurredAt());
            mapper.insertGeneratedItem(inputId, "RECORD", id, 0);
        }
        for (PreparedCapture.TaskItem item : capture.tasks()) {
            UUID id = UUID.randomUUID();
            mapper.insertTask(id, inputId, item.projectId(), item.title(), item.notes(), item.dueAt(), item.priority());
            mapper.insertGeneratedItem(inputId, "TASK", id, 0);
        }
        if (mapper.markSucceeded(inputId, token, completedAt) != 1) throw new IllegalStateException("输入处理权已失效");
        events.publishAfterCommit("INPUT", inputId, "SUCCEEDED");
    }

    @Transactional
    public void fail(UUID inputId, UUID token, String message, Instant completedAt) {
        if (mapper.markFailed(inputId, token, message, completedAt) == 1) {
            events.publishAfterCommit("INPUT", inputId, "FAILED");
        }
    }

    @Transactional
    public int recoverExpiredProcessing(Instant now) {
        return mapper.recoverExpiredProcessing(now, RECOVERY_MESSAGE);
    }

    @Transactional
    public InputRow revert(UUID id, Instant now) {
        InputRow current = mapper.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "输入不存在"));
        if (current.status() == InputStatus.REVERTED) return current;
        if (current.status() != InputStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有处理成功的输入可以撤销");
        }
        if (!current.revertible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该输入创建于撤销保护启用前，无法安全撤销");
        }
        int recordCount = mapper.countGeneratedItems(id, "RECORD");
        int taskCount = mapper.countGeneratedItems(id, "TASK");
        if (recordCount != mapper.countUnchangedRecords(id) || taskCount != mapper.countUnchangedTasks(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "生成条目已被编辑、完成、重开或删除，不能撤销");
        }
        if (mapper.deactivateGeneratedRecords(id, now) != recordCount
                || mapper.softDeleteGeneratedTasks(id, now) != taskCount
                || mapper.markReverted(id, now) != 1) {
            throw new IllegalStateException("撤销批次时状态发生变化");
        }
        events.publishAfterCommit("INPUT", id, "REVERTED");
        return require(id);
    }

    @Transactional(readOnly = true)
    public InputRow require(UUID id) {
        return mapper.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "输入不存在"));
    }
}
