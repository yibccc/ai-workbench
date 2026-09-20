package com.aiworkbench.input;

import com.aiworkbench.task.TaskPriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InputMapper {
    int insert(@Param("id") UUID id, @Param("requestId") String requestId, @Param("content") String content,
               @Param("referenceAt") Instant referenceAt, @Param("zoneId") String zoneId,
               @Param("token") UUID token, @Param("leaseExpiresAt") Instant leaseExpiresAt);
    Optional<InputRow> findById(UUID id);
    Optional<InputRow> findByRequestId(String requestId);
    Optional<InputRow> findByIdForUpdate(UUID id);
    int beginRetry(@Param("id") UUID id, @Param("token") UUID token,
                   @Param("leaseExpiresAt") Instant leaseExpiresAt);
    int markSucceeded(@Param("id") UUID id, @Param("token") UUID token,
                      @Param("completedAt") Instant completedAt);
    int markFailed(@Param("id") UUID id, @Param("token") UUID token,
                   @Param("errorMessage") String errorMessage, @Param("completedAt") Instant completedAt);
    int recoverExpiredProcessing(@Param("now") Instant now, @Param("message") String message);
    void insertRecord(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                      @Param("content") String content, @Param("occurredAt") Instant occurredAt);
    void insertTask(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                    @Param("title") String title, @Param("notes") String notes, @Param("dueAt") Instant dueAt,
                    @Param("priority") TaskPriority priority);
    void insertGeneratedItem(@Param("inputId") UUID inputId, @Param("entityType") String entityType,
                             @Param("entityId") UUID entityId, @Param("initialVersion") long initialVersion);
    int countGeneratedItems(@Param("inputId") UUID inputId, @Param("entityType") String entityType);
    int countUnchangedRecords(UUID inputId);
    int countUnchangedTasks(UUID inputId);
    int deactivateGeneratedRecords(@Param("inputId") UUID inputId, @Param("now") Instant now);
    int softDeleteGeneratedTasks(@Param("inputId") UUID inputId, @Param("now") Instant now);
    int markReverted(@Param("id") UUID id, @Param("now") Instant now);
    List<GeneratedRecordRow> findRecords(UUID inputId);
    List<GeneratedTaskRow> findTasks(UUID inputId);
}
