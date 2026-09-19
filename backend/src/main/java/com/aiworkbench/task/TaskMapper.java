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

    int delete(@Param("id") UUID id, @Param("version") long version);
}
