package com.aiworkbench.input;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InputPersistenceService {
    private final InputMapper mapper;

    public InputPersistenceService(InputMapper mapper) { this.mapper = mapper; }

    @Transactional
    public InputRow createOrGet(String requestId, String content, Instant referenceAt, ZoneId zoneId) {
        UUID id = UUID.randomUUID();
        mapper.insert(id, requestId, content, referenceAt, zoneId.getId());
        return mapper.findByRequestId(requestId).orElseThrow();
    }

    @Transactional
    public InputRow beginRetry(UUID id) {
        InputRow current = require(id);
        if (current.status() != InputStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前输入不能重试");
        }
        if (mapper.beginRetry(id) != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "输入状态已变化，请刷新后重试");
        return require(id);
    }

    @Transactional
    public void succeed(UUID inputId, PreparedCapture capture, Instant completedAt) {
        for (PreparedCapture.RecordItem item : capture.records()) {
            mapper.insertRecord(UUID.randomUUID(), inputId, item.projectId(), item.content(), item.occurredAt());
        }
        for (PreparedCapture.TaskItem item : capture.tasks()) {
            mapper.insertTask(UUID.randomUUID(), inputId, item.projectId(), item.title(), item.notes(), item.dueAt(), item.priority());
        }
        if (mapper.markSucceeded(inputId, completedAt) != 1) throw new IllegalStateException("输入状态已变化");
    }

    @Transactional
    public void fail(UUID inputId, String message, Instant completedAt) {
        mapper.markFailed(inputId, message, completedAt);
    }

    @Transactional(readOnly = true)
    public InputRow require(UUID id) {
        return mapper.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "输入不存在"));
    }
}
