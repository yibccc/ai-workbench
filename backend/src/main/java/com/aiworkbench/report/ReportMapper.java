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
    boolean lockWeeklyVersionChain(@Param("periodStart") LocalDate periodStart);
    int insertReport(@Param("id") UUID id, @Param("requestId") UUID requestId,
                     @Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart,
                     @Param("periodEnd") LocalDate periodEnd, @Param("zoneId") String zoneId,
                     @Param("token") UUID token, @Param("previousReportId") UUID previousReportId);
    Optional<ReportRow> findById(UUID id);
    Optional<ReportRow> findByRequestId(UUID requestId);
    List<ReportRow> findReports(@Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart);
    Optional<ReportRow> findLatest(@Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart);
    List<ReportSourceRow> findCandidateRecords(@Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findCandidateTasks(@Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findWeeklyRecords(@Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findCurrentTasks(@Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findNextWeekTasks(@Param("start") Instant start, @Param("end") Instant end);
    void insertSource(@Param("id") UUID id, @Param("reportId") UUID reportId,
                      @Param("source") ReportSourceRow source);
    List<ReportSourceRow> findSources(UUID reportId);
    int markSucceeded(@Param("id") UUID id, @Param("token") UUID token,
                      @Param("content") String content, @Param("now") Instant now);
    int markFailed(@Param("id") UUID id, @Param("token") UUID token,
                   @Param("message") String message, @Param("now") Instant now);
    int updateContent(@Param("id") UUID id, @Param("content") String content,
                      @Param("version") long version, @Param("now") Instant now);
    int updateManualAdditions(@Param("id") UUID id, @Param("manualAdditions") String manualAdditions,
                              @Param("version") long version, @Param("now") Instant now);
}
