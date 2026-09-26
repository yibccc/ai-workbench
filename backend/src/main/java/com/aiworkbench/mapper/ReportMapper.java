package com.aiworkbench.mapper;

import com.aiworkbench.entity.report.ReportRow;
import com.aiworkbench.entity.report.ReportSourceRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportMapper {
    Optional<ReportRow> lockById(@Param("userId") UUID userId, @Param("id") UUID id);
    boolean isDeleted(@Param("userId") UUID userId, @Param("id") UUID id);
    int softDelete(@Param("userId") UUID userId, @Param("id") UUID id, @Param("version") long version, @Param("now") Instant now);
    boolean lockWeeklyVersionChain(@Param("userId") UUID userId, @Param("periodStart") LocalDate periodStart);
    int insertReport(@Param("userId") UUID userId, @Param("id") UUID id, @Param("requestId") UUID requestId,
                     @Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart,
                     @Param("periodEnd") LocalDate periodEnd, @Param("zoneId") String zoneId,
                     @Param("token") UUID token, @Param("previousReportId") UUID previousReportId);
    Optional<ReportRow> findById(@Param("userId") UUID userId, @Param("id") UUID id);
    Optional<ReportRow> findPersistedById(@Param("id") UUID id);
    Optional<ReportRow> findByRequestId(@Param("userId") UUID userId, @Param("requestId") UUID requestId);
    List<ReportRow> findReports(@Param("userId") UUID userId, @Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart);
    List<ReportRow> findReportPage(@Param("userId") UUID userId, @Param("reportType") String reportType,
                                   @Param("periodStart") LocalDate periodStart);
    Optional<ReportRow> findLatest(@Param("userId") UUID userId, @Param("reportType") String reportType, @Param("periodStart") LocalDate periodStart);
    List<ReportSourceRow> findCandidateRecords(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findCandidateTasks(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findWeeklyRecords(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findCurrentTasks(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    List<ReportSourceRow> findNextWeekTasks(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);
    void insertSource(@Param("userId") UUID userId, @Param("id") UUID id, @Param("reportId") UUID reportId,
                      @Param("source") ReportSourceRow source);
    List<ReportSourceRow> findSources(@Param("userId") UUID userId, @Param("reportId") UUID reportId);
    List<ReportSourceRow> findSourcePage(@Param("userId") UUID userId, @Param("reportId") UUID reportId);
    long countSources(@Param("userId") UUID userId, @Param("reportId") UUID reportId);
    int updateSourceCount(@Param("userId") UUID userId, @Param("id") UUID id, @Param("sourceCount") int sourceCount);
    int markSucceeded(@Param("userId") UUID userId, @Param("id") UUID id, @Param("token") UUID token,
                      @Param("content") String content, @Param("now") Instant now);
    int markFailed(@Param("userId") UUID userId, @Param("id") UUID id, @Param("token") UUID token, @Param("message") String message,
                   @Param("errorCode") String errorCode, @Param("errorStage") String errorStage,
                   @Param("sourceCount") int sourceCount, @Param("now") Instant now);
    int updateContent(@Param("userId") UUID userId, @Param("id") UUID id, @Param("content") String content,
                      @Param("version") long version, @Param("now") Instant now);
    int updateManualAdditions(@Param("userId") UUID userId, @Param("id") UUID id, @Param("manualAdditions") String manualAdditions,
                              @Param("version") long version, @Param("now") Instant now);
}
