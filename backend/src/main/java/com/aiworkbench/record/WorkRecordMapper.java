package com.aiworkbench.record;

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
    Optional<WorkRecordRow> findById(UUID id);
    List<WorkRecordRow> findBetween(@Param("start") Instant start, @Param("end") Instant end);
    int update(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("content") String content,
            @Param("occurredAt") Instant occurredAt);
    int delete(UUID id);
}
