package com.aiworkbench.mapper;

import com.aiworkbench.entity.task.TaskEventRow;
import com.aiworkbench.entity.task.TaskRow;
import com.aiworkbench.enums.TaskDueFilter;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {
    void insert(
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("title") String title,
            @Param("notes") String notes,
            @Param("dueAt") Instant dueAt,
            @Param("priority") TaskPriority priority);

    Optional<TaskRow> findById(@Param("userId") UUID userId, @Param("id") UUID id);

    Optional<TaskRow> findAnyById(@Param("userId") UUID userId, @Param("id") UUID id);

    List<TaskRow> findAll(
            @Param("userId") UUID userId,
            @Param("status") TaskStatus status,
            @Param("projectId") UUID projectId,
            @Param("unassigned") boolean unassigned,
            @Param("priority") TaskPriority priority,
            @Param("dueFilter") TaskDueFilter dueFilter,
            @Param("now") Instant now,
            @Param("todayStart") Instant todayStart,
            @Param("tomorrowStart") Instant tomorrowStart);
    List<TaskRow> findPage(@Param("userId") UUID userId, @Param("status") TaskStatus status, @Param("projectId") UUID projectId,
            @Param("unassigned") boolean unassigned, @Param("priority") TaskPriority priority,
            @Param("dueFilter") TaskDueFilter dueFilter, @Param("now") Instant now,
            @Param("todayStart") Instant todayStart, @Param("tomorrowStart") Instant tomorrowStart);

    int update(
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("title") String title,
            @Param("notes") String notes,
            @Param("dueAt") Instant dueAt,
            @Param("priority") TaskPriority priority,
            @Param("version") long version);

    int complete(@Param("userId") UUID userId, @Param("id") UUID id, @Param("version") long version, @Param("completedAt") Instant completedAt);

    int reopen(@Param("userId") UUID userId, @Param("id") UUID id, @Param("version") long version);

    int touchCompletionResult(@Param("userId") UUID userId, @Param("id") UUID id, @Param("version") long version);

    int softDelete(@Param("userId") UUID userId, @Param("id") UUID id, @Param("version") long version, @Param("deletedAt") Instant deletedAt);

    void insertEvent(
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("todoId") UUID todoId,
            @Param("eventType") String eventType,
            @Param("title") String title,
            @Param("projectId") UUID projectId,
            @Param("fromStatus") TaskStatus fromStatus,
            @Param("toStatus") TaskStatus toStatus,
            @Param("version") long version,
            @Param("result") String result,
            @Param("occurredAt") Instant occurredAt);

    List<TaskEventRow> findEvents(@Param("userId") UUID userId, @Param("todoId") UUID todoId);
}
