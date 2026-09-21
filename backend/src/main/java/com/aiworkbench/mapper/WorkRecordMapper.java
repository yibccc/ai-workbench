package com.aiworkbench.mapper;

import com.aiworkbench.entity.record.WorkRecordRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkRecordMapper {
    void insert(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("content") String content,
            @Param("occurredAt") Instant occurredAt);
    void insertTaskCompletion(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("todoId") UUID todoId,
            @Param("content") String content,
            @Param("completionResult") String completionResult,
            @Param("occurredAt") Instant occurredAt);
    Optional<WorkRecordRow> findById(UUID id);
    List<WorkRecordRow> findBetween(@Param("start") Instant start, @Param("end") Instant end);
    List<WorkRecordRow> findPageBetween(@Param("start") Instant start, @Param("end") Instant end);
    int update(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("content") String content,
            @Param("occurredAt") Instant occurredAt);
    int delete(UUID id);
    int invalidateTaskCompletion(@Param("todoId") UUID todoId, @Param("updatedAt") Instant updatedAt);
    int updateCompletionResult(
            @Param("todoId") UUID todoId,
            @Param("completionResult") String completionResult,
            @Param("updatedAt") Instant updatedAt);
}
