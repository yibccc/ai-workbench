package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskEventResponse;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.dto.task.TaskVersionRequest;
import com.aiworkbench.dto.task.UpdateCompletionResultRequest;
import com.aiworkbench.dto.task.UpdateTaskRequest;
import com.aiworkbench.enums.TaskDueFilter;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import com.aiworkbench.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(defaultValue = "ALL") TaskDueFilter due) {
        return service.list(status, projectId, unassigned, priority, due);
    }

    @GetMapping("/page")
    public PageResponse<TaskResponse> page(@RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(defaultValue = "ALL") TaskDueFilter due,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(status, projectId, unassigned, priority, due, page, size);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/complete")
    public TaskResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteTaskRequest request) {
        return service.complete(id, request);
    }

    @PostMapping("/{id}/reopen")
    public TaskResponse reopen(@PathVariable UUID id, @Valid @RequestBody TaskVersionRequest request) {
        return service.reopen(id, request);
    }

    @PutMapping("/{id}/completion-result")
    public TaskResponse updateCompletionResult(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompletionResultRequest request) {
        return service.updateCompletionResult(id, request);
    }

    @GetMapping("/{id}/events")
    public List<TaskEventResponse> events(@PathVariable UUID id) {
        return service.events(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam @PositiveOrZero long version) {
        service.delete(id, version);
    }
}
