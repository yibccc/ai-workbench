package com.aiworkbench.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface ReportMapper {
    int insertReport(@Param("id") UUID id, @Param("requestId") UUID requestId,
                     @Param("date") LocalDate date, @Param("zoneId") String zoneId,
                     @Param("token") UUID token);
    Optional<ReportRow> findById(UUID id);
    Optional<ReportRow> findByRequestId(UUID requestId);
    List<ReportRow> findDaily(@Param("date") LocalDate date);
    List<ReportSourceRow> findCandidateRecords(@Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findCandidateTasks(@Param("start") Instant start, @Param("end") Instant end);
    void insertSource(@Param("id") UUID id, @Param("reportId") UUID reportId,
                      @Param("source") ReportSourceRow source);
    List<ReportSourceRow> findSources(UUID reportId);
    int markSucceeded(@Param("id") UUID id, @Param("token") UUID token,
                      @Param("content") String content, @Param("now") Instant now);
    int markFailed(@Param("id") UUID id, @Param("token") UUID token,
                   @Param("message") String message, @Param("now") Instant now);
    int updateContent(@Param("id") UUID id, @Param("content") String content,
                      @Param("version") long version, @Param("now") Instant now);
}
