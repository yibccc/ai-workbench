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
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("content") String content,
            @Param("occurredAt") Instant occurredAt);
    void insertTaskCompletion(
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("todoId") UUID todoId,
            @Param("content") String content,
            @Param("completionResult") String completionResult,
            @Param("occurredAt") Instant occurredAt);
    Optional<WorkRecordRow> findById(@Param("userId") UUID userId, @Param("id") UUID id);
    List<WorkRecordRow> findBetween(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<WorkRecordRow> findPageBetween(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<WorkRecordRow> findPresentationByIds(@Param("userId") UUID userId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("ids") List<UUID> ids);
    boolean existsOwned(@Param("userId") UUID userId, @Param("id") UUID id);
    int update(
            @Param("userId") UUID userId,
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("content") String content,
            @Param("occurredAt") Instant occurredAt);
    int delete(@Param("userId") UUID userId, @Param("id") UUID id);
    int invalidateTaskCompletion(@Param("userId") UUID userId, @Param("todoId") UUID todoId, @Param("updatedAt") Instant updatedAt);
    int updateCompletionResult(
            @Param("userId") UUID userId,
            @Param("todoId") UUID todoId,
            @Param("completionResult") String completionResult,
            @Param("updatedAt") Instant updatedAt);
}
