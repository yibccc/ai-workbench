package com.aiworkbench.service;

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
import java.util.List;
import java.util.UUID;

public interface TaskService {
    TaskResponse create(CreateTaskRequest request);
    List<TaskResponse> list(
            TaskStatus status,
            UUID projectId,
            boolean unassigned,
            TaskPriority priority,
            TaskDueFilter dueFilter);
    PageResponse<TaskResponse> page(TaskStatus status, UUID projectId, boolean unassigned,
                                           TaskPriority priority, TaskDueFilter dueFilter,
                                           int page, int size);
    TaskResponse get(UUID id);
    TaskResponse update(UUID id, UpdateTaskRequest request);
    void delete(UUID id, long version);
    TaskResponse complete(UUID id, CompleteTaskRequest request);
    TaskResponse reopen(UUID id, TaskVersionRequest request);
    TaskResponse updateCompletionResult(UUID id, UpdateCompletionResultRequest request);
    List<TaskEventResponse> events(UUID id);
}
