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
               @Param("referenceAt") Instant referenceAt, @Param("zoneId") String zoneId);
    Optional<InputRow> findById(UUID id);
    Optional<InputRow> findByRequestId(String requestId);
    int beginRetry(UUID id);
    int markSucceeded(@Param("id") UUID id, @Param("completedAt") Instant completedAt);
    int markFailed(@Param("id") UUID id, @Param("errorMessage") String errorMessage, @Param("completedAt") Instant completedAt);
    void insertRecord(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                      @Param("content") String content, @Param("occurredAt") Instant occurredAt);
    void insertTask(@Param("id") UUID id, @Param("inputId") UUID inputId, @Param("projectId") UUID projectId,
                    @Param("title") String title, @Param("notes") String notes, @Param("dueAt") Instant dueAt,
                    @Param("priority") TaskPriority priority);
    List<GeneratedRecordRow> findRecords(UUID inputId);
    List<GeneratedTaskRow> findTasks(UUID inputId);
}
