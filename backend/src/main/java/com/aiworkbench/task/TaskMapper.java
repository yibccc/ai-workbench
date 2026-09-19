package com.aiworkbench.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {
    void insert(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("title") String title,
            @Param("notes") String notes,
            @Param("dueAt") Instant dueAt,
            @Param("priority") TaskPriority priority);

    Optional<TaskRow> findById(UUID id);

    Optional<TaskRow> findAnyById(UUID id);

    List<TaskRow> findAll(
            @Param("status") TaskStatus status,
            @Param("projectId") UUID projectId,
            @Param("unassigned") boolean unassigned,
            @Param("priority") TaskPriority priority,
            @Param("dueFilter") TaskDueFilter dueFilter,
            @Param("now") Instant now,
            @Param("todayStart") Instant todayStart,
            @Param("tomorrowStart") Instant tomorrowStart);

    int update(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("title") String title,
            @Param("notes") String notes,
            @Param("dueAt") Instant dueAt,
            @Param("priority") TaskPriority priority,
            @Param("version") long version);

    int complete(@Param("id") UUID id, @Param("version") long version, @Param("completedAt") Instant completedAt);

    int reopen(@Param("id") UUID id, @Param("version") long version);

    int touchCompletionResult(@Param("id") UUID id, @Param("version") long version);

    int softDelete(@Param("id") UUID id, @Param("version") long version, @Param("deletedAt") Instant deletedAt);

    void insertEvent(
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

    List<TaskEventRow> findEvents(UUID todoId);
}
