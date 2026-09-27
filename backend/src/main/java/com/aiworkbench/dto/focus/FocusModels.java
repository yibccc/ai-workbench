package com.aiworkbench.dto.focus;

import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.dto.task.TaskResponse;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FocusModels {
    private FocusModels() {}

    public record Routine(UUID id, String title, UUID projectId, List<Integer> weekdays,
                          Integer defaultDurationMinutes, Boolean enabled, Long version,
                          Instant createdAt, Instant updatedAt) {}
    public record SaveRoutine(@NotBlank @Size(max=240) String title, UUID projectId,
                              @NotEmpty List<@Min(1) @Max(7) Integer> weekdays,
                              @NotNull @Min(1) @Max(480)
                              @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class)
                              Integer defaultDurationMinutes,
                              Long version, Boolean enabled) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public record Blocked(UUID routineId, String reason) {}
    public record FillToday(LocalDate date, List<TaskResponse> created, List<Blocked> blocked) {}
    public record Start(@NotNull UUID requestId, @NotBlank @Size(max=240) String title,
                        UUID taskId, UUID projectId, @NotNull @Min(1) @Max(480)
                        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class)
                        Integer targetMinutes,
                        @Min(1) @Max(120)
                        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class)
                        Integer intervalMinutes) {}
    public record Checkpoint(@NotNull @PositiveOrZero Long version, UUID controllerId, Long controllerGeneration) {}
    public record Transition(@NotNull @PositiveOrZero Long version, @NotNull Action action) {}
    public enum Action { PAUSE, RESUME, BREAK_DUE, BREAK_DONE, SKIP_BREAK, DISMISS_REMINDERS }
    public record Progress(@NotNull @PositiveOrZero Long version,
                           @NotNull @Size(max=4000) String progress) {}
    public record Session(UUID id, UUID requestId, String title, UUID taskId, UUID projectId,
                          Long targetMs, Long intervalMs, String zoneId, String phase, Long version,
                          Instant startedAt, Instant anchorAt, Instant endedAt, Long focusMs,
                          Long breakMs, Long pauseMs, String resumePhase,
                          Long breakRemainingMs, Long nextBreakAtMs,
                          Boolean remindersDismissed, Integer reminderOrdinal, UUID controllerId,
                          Long controllerGeneration, Instant controllerExpiresAt, String progress) {}
    public record Today(LocalDate date, long focusMs, long breakMs, long sessionCount,
                        List<WorkRecordResponse> records) {}
    public record Capabilities(boolean writeEnabled) {}
}
