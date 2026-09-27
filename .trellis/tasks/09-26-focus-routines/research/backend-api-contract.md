# Focus HTTP contract (implementation target)

All routes are same-origin `/api/focus`; UUIDs are strings, instants are ISO-8601 UTC, durations are integer milliseconds, dates are `YYYY-MM-DD` in the server business zone. Existing cookie authentication and CSRF apply to every write. Reads do not renew explicit login activity. Mutations return 400 for invalid input, 404 for another owner's or unknown row, 409 for stale versions or conflicting state.

Deployment gate: `GET /api/focus/capabilities` → `{writeEnabled: boolean}`. `workbench.focus.write-enabled` / `FOCUS_WRITE_ENABLED` defaults to `false` in production. When false, routine writes, fill-today and new session starts return 409 problem detail `专注写入尚未开放`; reads remain available so compatibility with existing focus records can be checked before enabling writes. Existing sessions can still checkpoint, transition, end and edit progress while new writes are closed, allowing a safe true→false drain. Test profile explicitly enables new writes; E2E runtime must set `FOCUS_WRITE_ENABLED=true`.

## Routines and today's tasks

- `GET /api/focus/routines` → `Routine[]`.
- `POST /api/focus/routines` body `{title, projectId?: UUID|null, weekdays: number[], defaultDurationMinutes: number}` → `Routine` (201). Weekdays are ISO 1=Monday through 7=Sunday; duration 1..480.
- `PUT /api/focus/routines/{id}` body `{title, projectId?: UUID|null, weekdays: number[], defaultDurationMinutes: number, version: number, enabled: boolean}` → `Routine` (409 on stale version).
- `POST /api/focus/routines/{id}/disable` body `{version}` → `Routine`.
- `POST /api/focus/routines/{id}/enable` body `{version}` → `Routine`.
- `POST /api/focus/routines/fill-today` body `{}` → `{date, created: TaskResponse[], blocked: {routineId, reason}[]}`. This is the sole occurrence creation command; repeated calls do not replace completed or deleted occurrences. Normal task responses add nullable `routineId`, `occurrenceDate`, `defaultFocusDurationMinutes`.

`Routine` is `{id, title, projectId, weekdays, defaultDurationMinutes, enabled, version, createdAt, updatedAt}`. `weekdays` is sorted ascending.

## Session

- `GET /api/focus/current` → `Session|null`; pure read, no state advancement.
- `POST /api/focus/sessions` body `{requestId: UUID, title, taskId?: UUID|null, projectId?: UUID|null, targetMinutes: number, intervalMinutes?: number}` → `Session` (201 or existing same-request session). Minutes: target 1..480, interval 1..120, default 10. Only one unfinished session per account.
- `GET /api/focus/sessions/{id}` → `Session`.
- `POST /api/focus/sessions/{id}/checkpoint` body `{version: number, controllerId?: UUID|null, controllerGeneration?: number|null}` → `Session`. A stale version returns 409; `controllerId` claims or renews a 60s lease. Any elapsed gap, including background work and device sleep, accrues from the last server anchor according to the current phase. Auto polling must not call `/api/auth/activity`.
- `POST /api/focus/sessions/{id}/transition` body `{version: number, action: "PAUSE"|"RESUME"|"BREAK_DUE"|"BREAK_DONE"|"SKIP_BREAK"|"DISMISS_REMINDERS"}` → `Session`. Checkpoints never start a break. The visible client sends `BREAK_DUE` only when `focusMs >= nextBreakAtMs`; the server enters `MICRO_BREAK` only if the target has not been reached. On break completion or skip, the server advances `nextBreakAtMs` strictly beyond current `focusMs`, so missed reminders are not replayed.
- `POST /api/focus/sessions/{id}/end` body `{version: number}` → `Session`; repeat end returns the original settlement. End does not complete a linked task.
- `PUT /api/focus/sessions/{id}/progress` body `{version: number, progress: string}` → `Session`; up to 4000 characters, only ended session, dedicated progress field; conditional version update, stale edits return 409 and never overwrite a newer edit. Does not edit duration.
- `GET /api/focus/today` → `{date, focusMs, breakMs, sessionCount, records: WorkRecordResponse[]}`.

`Session` fields: `{id, requestId, title, taskId, projectId, targetMs, intervalMs, zoneId, phase, version, startedAt, anchorAt, endedAt, focusMs, breakMs, pauseMs, resumePhase, breakRemainingMs, nextBreakAtMs, remindersDismissed, reminderOrdinal, controllerId, controllerGeneration, controllerExpiresAt, progress}`. Phase is `RUNNING|MICRO_BREAK|PAUSED|ENDED`. `nextBreakAtMs` is the next net focus threshold. Reminder identity is `(sessionId, reminderOrdinal, phase)`; only the lease holder may play. Server responses are authoritative; client ticking is display only. V16 normalizes older recovery sessions to their prior phase and resumes accounting from the former `pendingStart`; historical unconfirmed `PENDING` audit intervals remain excluded from settlement.

Work record responses add nullable `{sessionId, businessDate, focusMs, breakMs, segmentStart, segmentEnd, progress}` and source union adds `FOCUS_SESSION`. Records are one per session and business date, only for positive net focus that date. `progress` is dedicated optional text for focus records; `completionResult` keeps its task completion meaning. Progress does not assert completion.
