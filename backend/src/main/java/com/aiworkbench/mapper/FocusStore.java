package com.aiworkbench.mapper;

import com.aiworkbench.dto.focus.FocusModels.Session;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FocusStore {
    record RoutineRow(UUID id,String title,UUID projectId,String weekdays,Integer defaultDurationMinutes,
                      Boolean enabled,Long version,Instant createdAt,Instant updatedAt) {
        public com.aiworkbench.dto.focus.FocusModels.Routine toResponse() {
            return new com.aiworkbench.dto.focus.FocusModels.Routine(id,title,projectId,
                    java.util.Arrays.stream(weekdays.split(",")).map(Integer::parseInt).toList(),
                    defaultDurationMinutes,enabled,version,createdAt,updatedAt);
        }
    }
    record Segment(Instant start,Instant end,Long focusMs,Long breakMs) {}
    record Totals(Long focusMs,Long breakMs,Long sessionCount) {}
    List<RoutineRow> routines(@Param("userId") UUID userId);
    RoutineRow routine(@Param("userId") UUID userId,@Param("id") UUID id);
    Session session(@Param("userId") UUID userId,@Param("id") UUID id,@Param("lock") boolean lock);
    Session current(@Param("userId") UUID userId);
    Session byRequest(@Param("userId") UUID userId,@Param("requestId") UUID requestId);
    int insertRoutine(@Param("userId") UUID userId,@Param("id") UUID id,@Param("title") String title,
                      @Param("projectId") UUID projectId,@Param("weekdays") String weekdays,
                      @Param("duration") int duration);
    int updateRoutine(@Param("userId") UUID userId,@Param("id") UUID id,@Param("title") String title,
                      @Param("projectId") UUID projectId,@Param("weekdays") String weekdays,
                      @Param("duration") int duration,@Param("enabled") boolean enabled,
                      @Param("now") Instant now,@Param("version") long version);
    int toggleRoutine(@Param("userId") UUID userId,@Param("id") UUID id,@Param("enabled") boolean enabled,
                      @Param("now") Instant now,@Param("version") long version);
    int insertOccurrence(@Param("id") UUID id,@Param("userId") UUID userId,@Param("projectId") UUID projectId,
                         @Param("title") String title,@Param("routineId") UUID routineId,
                         @Param("date") LocalDate date,@Param("duration") int duration);
    int insertSession(@Param("id") UUID id,@Param("userId") UUID userId,@Param("requestId") UUID requestId,
                      @Param("taskId") UUID taskId,@Param("projectId") UUID projectId,@Param("title") String title,
                      @Param("targetMs") long targetMs,@Param("intervalMs") long intervalMs,
                      @Param("zoneId") String zoneId,@Param("now") Instant now);
    int updateSession(@Param("userId") UUID userId,@Param("session") Session session,@Param("now") Instant now);
    int insertInterval(@Param("userId") UUID userId,@Param("sessionId") UUID sessionId,@Param("kind") String kind,
                       @Param("start") Instant start,@Param("end") Instant end,@Param("confirmed") boolean confirmed);
    List<Segment> segments(@Param("userId") UUID userId,@Param("sessionId") UUID sessionId);
    int insertFocusRecord(@Param("id") UUID id,@Param("userId") UUID userId,@Param("projectId") UUID projectId,
                          @Param("taskId") UUID taskId,@Param("sessionId") UUID sessionId,@Param("content") String content,
                          @Param("date") LocalDate date,@Param("focusMs") long focusMs,@Param("breakMs") long breakMs,
                          @Param("start") Instant start,@Param("end") Instant end);
    int updateProgress(@Param("userId") UUID userId,@Param("id") UUID id,@Param("progress") String progress,
                       @Param("now") Instant now,@Param("version") long version);
    int updateRecordProgress(@Param("userId") UUID userId,@Param("id") UUID id,@Param("progress") String progress,
                             @Param("now") Instant now);
    Totals todayTotals(@Param("userId") UUID userId,@Param("date") LocalDate date);
}
