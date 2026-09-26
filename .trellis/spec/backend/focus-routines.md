# Focus routines and session ledger

## 1. Scope / Trigger

Use this contract when changing routine occurrence generation, focus timing, work record settlement, or report evidence. PostgreSQL owns time and identity facts; browser timers only display and request transitions. A focus investment and a task completion are separate facts.

## 2. Signatures

- `GET /api/focus/capabilities -> {writeEnabled}` reports the rollout gate. `GET /api/focus/routines`, `POST /api/focus/routines`, `PUT /api/focus/routines/{id}`, `POST /api/focus/routines/{id}/enable|disable` manage owner-scoped templates.
- `POST /api/focus/routines/fill-today` creates only today's matching task occurrences; `GET /api/focus/current` and `GET /api/focus/today` are pure reads.
- `POST /api/focus/sessions` starts one owner-scoped session, keyed by `(user_id, request_id)`.
- `POST /api/focus/sessions/{id}/checkpoint|transition|recover|end`, `PUT /api/focus/sessions/{id}/progress`, and `GET /api/focus/sessions/{id}` implement the session ledger.
- `FocusStore` is a typed MyBatis mapper with `resources/mapper/FocusStore.xml`; `V15__focus_routines.sql` adds `focus_routines`, routine occurrence columns on `todo_items`, `focus_sessions`, `focus_intervals`, and focus fields on `work_records`.

## 3. Contracts

- Routine input: `{title, projectId?, weekdays: number[], defaultDurationMinutes, version?, enabled?}`. Weekdays are ISO 1–7; duration is an integer 1–480 minutes. `fill-today` returns `{date, created, blocked}`. The database unique key `(user_id, routine_id, occurrence_date)` includes soft-deleted tasks; do not repurpose `due_at` for an occurrence date.
- Start input: `{requestId, title, taskId?, projectId?, targetMinutes, intervalMinutes?}`. Target is 1–480 integer minutes; reminder interval is 1–120 integer minutes, default 10. No task is created for a temporary title. One owner has at most one non-ended session.
- Mutation requests carry `version`; a checkpoint may also carry `{controllerId, controllerGeneration}`. Session phases are `RUNNING`, `MICRO_BREAK`, `PAUSED`, `RECOVERY_REQUIRED`, `ENDED`. Durations in responses and storage are integer milliseconds. Business date derives from the saved session zone, normally `Asia/Shanghai`.
- The server records confirmed half-open intervals in `focus_intervals`. A checkpoint gap greater than 60 seconds becomes `PENDING`; it contributes nothing before recovery. Confirmation can credit only that server-stored range and must not invent completed breaks or overdue reminder events. Rejection discards the range. A paused gap cannot become focus. A net target beats a new microbreak.
- End locks the owner session and writes at most one `FOCUS_SESSION` work record for each business day with positive net focus. Settlement and session end are one transaction; retry reads the original result. `progress` is a separate optional field, never task `completionResult`. `focusMs` and `breakMs` are structured record fields. Ending never completes the linked task or invokes the report model.
- Report candidate queries include active focus records. A report freezes `sessionId`, `taskId`, `businessDate`, `focusMs`, `breakMs`, and `progress` alongside the source. A focus source supports PROGRESS, not ACHIEVEMENTS; a separate `TASK_COMPLETION` source supports completion. For a PROGRESS bullet citing focus but no `TASK_COMPLETION`, render deterministic investment text from frozen focus facts, ignoring arbitrary model prose that could falsely claim completion; preserve all cited source IDs. Old report snapshots remain immutable.
- Every query and foreign key scopes by owner, including project/task association. Existing cookie authentication and CSRF apply to writes. Passive status/checkpoint traffic must not signal `/api/auth/activity`.
- `FOCUS_WRITE_ENABLED` defaults to `false` through `workbench.focus.write-enabled`. With it off, creation and routine mutations (`create/update/toggle/fill-today/start`) return 409, while existing sessions may checkpoint, transition, recover, end, and save progress so they are not stranded. Existing focus records and report evidence remain readable. Enable only after compatible readers are deployed; an E2E run must set it to `true` explicitly.

## 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| Blank title, empty/out-of-range weekdays, out-of-range duration or interval, missing version | HTTP 400; no write |
| Foreign routine, session, task, or project UUID (including ADMIN) | HTTP 404; no existence leak or partial write |
| Stale version or invalid phase transition | HTTP 409; retained committed state |
| Different request while an unfinished session exists | Safe HTTP 409 or existing-session response; no replacement |
| Same start request or repeated end after commit | Same session/settlement; no duplicate record |
| Archived template project at fill time | Visible `blocked` item; other eligible templates proceed |
| Database error during settlement | Transaction rollback; no ended session with missing daily records |
| Missing or invalid CSRF on a write | Existing security rejection; no write |
| `FOCUS_WRITE_ENABLED=false` | New routines/occurrences/sessions are refused with 409; existing sessions can settle and evidence stays readable |

## 5. Good / Base / Bad Cases

- Good: a 25-minute net target with 10-minute reminders yields `FOCUS 600000`, `BREAK 15000`, `FOCUS 600000`, `BREAK 15000`, `FOCUS 300000` milliseconds. Total elapsed is 1530000 milliseconds and the linked task remains pending.
- Base: two `fill-today` requests, followed by a soft delete and another request, leave one task occurrence. An ended session with zero net focus creates no daily record.
- Bad: derive duration from browser callback counts, count a pending gap before confirmation, fabricate break completions during recovery, write a focus record with `completionResult`, or rewrite an old report after progress changes.

## 6. Tests Required

- Real PostgreSQL: verify unique occurrence after soft delete and concurrent fill; one open session; owner foreign keys; source/check constraints; failed-settlement rollback; repeated end with one record per session/day.
- Rollout gate: with creation disabled, verify `GET /api/focus/capabilities` reports false, new routine/occurrence/start writes are refused, existing sessions can end, and old focus records/reports remain readable. Re-enable and verify new writes; disable again without data deletion.
- Injectable clock: assert 60-second threshold inclusive, >60-second pending, confirmation/rejection idempotency, pause and microbreak remainder, skip/dismiss/target priority, millisecond conservation, and 23:50→00:15:30 Shanghai split of 600/900 net seconds.
- HTTP with isolated PostgreSQL/Redis: assert authentication, CSRF, 400/404/409, A/B/ADMIN isolation, and passive checkpoint not extending explicit activity.
- Deterministic report gateway: two focus sessions plus a task completion retain distinct source IDs; focus cannot support an achievement; generated versions freeze their source snapshots.
- Feed an adversarial model bullet saying a task was completed while citing only focus sources in DAILY and WEEKLY PROGRESS. Assert persisted text is rewritten to frozen investment facts, retains source references, and does not carry the model's completion claim.
- Browser: same session across refresh/pages/tabs, explicit start after task navigation, global guidance and sound fallback, account cleanup, five reachable nav items, records page without timer controls.

## 7. Wrong vs Correct

Wrong: on recovery, feed a long unobserved interval through the normal `RUNNING → MICRO_BREAK → RUNNING` state loop. That silently counts breaks and reminders the user could not observe. Likewise, a prompt alone does not prevent the model from writing “completed task” for focus-only PROGRESS evidence.

Correct: keep the gap as `PENDING`; after explicit confirmation, account only the allowed server-bounded focus duration, skip obsolete reminder thresholds, and preserve an interrupted break's unconfirmed remainder. Settlement then derives daily records from confirmed intervals only. Render focus-only progress from frozen structured facts, not model prose.
