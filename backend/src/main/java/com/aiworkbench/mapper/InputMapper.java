package com.aiworkbench.mapper;

import com.aiworkbench.entity.input.GeneratedRecordRow;
import com.aiworkbench.entity.input.GeneratedTaskRow;
import com.aiworkbench.entity.input.InputRow;
import com.aiworkbench.enums.TaskPriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InputMapper {
    int insert(@Param("userId") UUID userId, @Param("id") UUID id, @Param("requestId") String requestId, @Param("content") String content,
               @Param("referenceAt") Instant referenceAt, @Param("zoneId") String zoneId,
               @Param("token") UUID token, @Param("leaseExpiresAt") Instant leaseExpiresAt);
    Optional<InputRow> findById(UUID id);
    Optional<InputRow> findOwnedById(@Param("userId") UUID userId, @Param("id") UUID id);
    Optional<InputRow> findByRequestId(@Param("userId") UUID userId, @Param("requestId") String requestId);
    Optional<InputRow> findByIdForUpdate(@Param("userId") UUID userId, @Param("id") UUID id);
    int beginRetry(@Param("userId") UUID userId, @Param("id") UUID id, @Param("token") UUID token,
                   @Param("leaseExpiresAt") Instant leaseExpiresAt);
    int markSucceeded(@Param("userId") UUID userId, @Param("id") UUID id, @Param("token") UUID token,
                      @Param("completedAt") Instant completedAt);
    int markFailed(@Param("userId") UUID userId, @Param("id") UUID id, @Param("token") UUID token,
                   @Param("errorMessage") String errorMessage, @Param("completedAt") Instant completedAt);
    int recoverExpiredProcessing(@Param("now") Instant now, @Param("message") String message);
    void insertRecord(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                      @Param("content") String content, @Param("occurredAt") Instant occurredAt);
    void insertTask(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                    @Param("title") String title, @Param("notes") String notes, @Param("dueAt") Instant dueAt,
                    @Param("priority") TaskPriority priority);
    void insertGeneratedItem(@Param("inputId") UUID inputId, @Param("entityType") String entityType,
                             @Param("entityId") UUID entityId, @Param("initialVersion") long initialVersion);
    int countGeneratedItems(@Param("userId") UUID userId, @Param("inputId") UUID inputId, @Param("entityType") String entityType);
    int countUnchangedRecords(@Param("userId") UUID userId, @Param("inputId") UUID inputId);
    int countUnchangedTasks(@Param("userId") UUID userId, @Param("inputId") UUID inputId);
    int deactivateGeneratedRecords(@Param("userId") UUID userId, @Param("inputId") UUID inputId, @Param("now") Instant now);
    int softDeleteGeneratedTasks(@Param("userId") UUID userId, @Param("inputId") UUID inputId, @Param("now") Instant now);
    int markReverted(@Param("userId") UUID userId, @Param("id") UUID id, @Param("now") Instant now);
    List<GeneratedRecordRow> findRecords(@Param("userId") UUID userId, @Param("inputId") UUID inputId);
    List<GeneratedTaskRow> findTasks(@Param("userId") UUID userId, @Param("inputId") UUID inputId);
}
