# Focus routines and session ledger

## 1. Scope / Trigger

Use this contract when changing routine occurrence generation, focus timing, work record settlement, or report evidence. PostgreSQL owns time and identity facts; browser timers only display and request transitions. A focus session records elapsed timer time, not detected human activity; sleep may be included unless the user pauses or ends. A focus timing fact and a task completion are separate facts.

## 2. Signatures

- `GET /api/focus/routines`, `POST /api/focus/routines`, `PUT /api/focus/routines/{id}`, `POST /api/focus/routines/{id}/enable|disable` manage owner-scoped templates.
- `POST /api/focus/routines/fill-today` creates only today's matching task occurrences; `GET /api/focus/current` and `GET /api/focus/today` are pure reads.
- `POST /api/focus/sessions` starts one owner-scoped session, keyed by `(user_id, request_id)`.
- `POST /api/focus/sessions/{id}/checkpoint|transition|end`, `PUT /api/focus/sessions/{id}/progress`, and `GET /api/focus/sessions/{id}` implement the session ledger. The former `/recover` command is removed.
- `FocusStore` is a typed MyBatis mapper with `resources/mapper/FocusStore.xml`; `V15__focus_routines.sql` adds `focus_routines`, routine occurrence columns on `todo_items`, `focus_sessions`, `focus_intervals`, and focus fields on `work_records`. `V16` normalizes legacy pending sessions and removes the obsolete recovery columns/phase without editing V15.

## 3. Contracts

- Routine input: `{title, projectId?, weekdays: number[], defaultDurationMinutes, version?, enabled?}`. Weekdays are ISO 1–7; duration is an integer 1–480 minutes. `fill-today` returns `{date, created, blocked}`. The database unique key `(user_id, routine_id, occurrence_date)` includes soft-deleted tasks; do not repurpose `due_at` for an occurrence date.
- Start input: `{requestId, title, taskId?, projectId?, targetMinutes, intervalMinutes?}`. Target is 1–480 integer minutes; reminder interval is 1–120 integer minutes, default 10. No task is created for a temporary title. One owner has at most one non-ended session.
- Mutation requests carry `version`; a checkpoint may also carry `{controllerId, controllerGeneration}`. Session phases are `RUNNING`, `MICRO_BREAK`, `PAUSED`, `ENDED`. Durations in responses and storage are integer milliseconds. Business date derives from the saved session zone, normally `Asia/Shanghai`.
- Reminder ownership is a versioned lease: `FocusServiceImpl.CONTROLLER_LEASE` is 120 seconds, leaving margin for ordinary hidden-tab minute-level timer checks and request transport. Null controller claims cannot acquire/renew it. A matching controller ID and generation renew an unexpired lease; expiry (inclusive boundary) permits a claim with a new generation. Renewal never shortens the existing expiry under clock rollback.
- A checkpoint that reaches the target renews/acquires the lease before saving/settling in the same transaction. An already-ended target session (`focusMs >= targetMs`) may mutate only its reminder lease, with the same version/owner/generation rules and a version increment on actual mutation; it must not advance anchor/time totals or settle again. Already-ended early sessions stay unchanged. Stale terminal versions still return 409.
- The server records confirmed half-open intervals in `focus_intervals`. `RUNNING` continues across hidden tabs, other applications, browser suspension and device sleep until an explicit pause/end or the net target; a later request credits elapsed server time up to that target. No sleep/absence inference or pending confirmation remains, so sleeping time may count as focus. `PAUSED` never credits focus. A net target beats a new microbreak.
- A checkpoint credits elapsed focus without automatically starting a break. Only a visible-client `BREAK_DUE` transition at or beyond the next net threshold starts a 15-second microbreak and increments its reminder ordinal. Background time crossing multiple thresholds does not create unseen completed breaks or backlog reminders; after a break, advance the next threshold past the current accumulated focus.
- End locks the owner session and writes at most one `FOCUS_SESSION` work record for each business day with positive net focus. Settlement and session end are one transaction; retry reads the original result. `progress` is a separate optional field, never task `completionResult`. `focusMs` and `breakMs` are structured record fields. Ending never completes the linked task or invokes the report model.
- Report candidate queries include active focus records. A report freezes `sessionId`, `taskId`, `businessDate`, `focusMs`, `breakMs`, and `progress` alongside the source. A focus source supports PROGRESS, not ACHIEVEMENTS; a separate `TASK_COMPLETION` source supports completion. For a PROGRESS bullet citing focus but no `TASK_COMPLETION`, render deterministic investment text from frozen focus facts, ignoring arbitrary model prose that could falsely claim completion; preserve all cited source IDs. Old report snapshots remain immutable.
- Every query and foreign key scopes by owner, including project/task association. Existing cookie authentication and CSRF apply to writes. Passive status/checkpoint traffic must not signal `/api/auth/activity`.
- Focus writes are available to authenticated users immediately after startup. Do not add a compatibility rollout gate or default-off feature flag without the user's explicit approval; the server continues to enforce owner, CSRF, validation and state-version contracts.

## 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| Blank title, empty/out-of-range weekdays, out-of-range duration or interval, missing version | HTTP 400; no write |
| Foreign routine, session, task, or project UUID (including ADMIN) | HTTP 404; no existence leak or partial write |
| Stale version or invalid phase transition | HTTP 409; retained committed state |
| Unexpired lease claimed by another ID or wrong generation | Keep existing lease; no unauthorized renewal |
| Expired lease claimed under current session version | Acquire with a higher generation; only one concurrent winner |
| Target-ended checkpoint with eligible lease mutation | Update lease/version only; unchanged time ledger and daily records |
| Target-ended checkpoint without lease mutation, or early-ended session | Return unchanged session; no extra settlement |
| Different request while an unfinished session exists | Safe HTTP 409 or existing-session response; no replacement |
| Same start request or repeated end after commit | Same session/settlement; no duplicate record |
| Archived template project at fill time | Visible `blocked` item; other eligible templates proceed |
| Database error during settlement | Transaction rollback; no ended session with missing daily records |
| Missing or invalid CSRF on a write | Existing security rejection; no write |

## 5. Good / Base / Bad Cases

- Good: with a visible client issuing `BREAK_DUE` at each due boundary, a 25-minute net target with 10-minute reminders yields `FOCUS 600000`, `BREAK 15000`, `FOCUS 600000`, `BREAK 15000`, `FOCUS 300000` milliseconds. Total elapsed is 1530000 milliseconds and the linked task remains pending. If the page is hidden for the whole 25 minutes, the session reaches its target without synthesizing those breaks.
- Base: two `fill-today` requests, followed by a soft delete and another request, leave one task occurrence. An ended session with zero net focus creates no daily record.
- Bad: derive duration from browser callback counts, treat a hidden tab as proof work stopped, fabricate completed breaks while no prompt is visible, write a focus record with `completionResult`, or rewrite an old report after progress changes.

## 6. Tests Required

- Real PostgreSQL: verify unique occurrence after soft delete and concurrent fill; one open session; owner foreign keys; source/check constraints; failed-settlement rollback; repeated end with one record per session/day.
- With ordinary test configuration, create a routine, fill today's task and start a session without any focus feature flag; assert authenticated writes succeed and existing owner/CSRF protections remain in force.
- Injectable clock: assert long hidden/sleep-like gaps credit focus to the target without recovery state, paused gaps do not credit focus, only explicit `BREAK_DUE` creates a break, no backlog after hidden threshold crossings, microbreak remainder/skip/dismiss/target priority, millisecond conservation, and 23:50→00:15:30 Shanghai split of 600/900 net seconds when due transitions are issued visibly.
- HTTP with isolated PostgreSQL/Redis: assert authentication, CSRF, 400/404/409, A/B/ADMIN isolation, and passive checkpoint not extending explicit activity.
- Injectable clock + real PostgreSQL: test 61-second lease renewal cadence, wrong generations/IDs, null viewer claims, exact 120-second expiry and takeover, terminal renewal after owner/other-tab settlement, stale terminal versions, clock rollback not shortening expiry, and concurrent terminal takeover with one winner. Assert interval/time/record counts and settlement values remain unchanged by terminal lease writes.
- Deterministic report gateway: two focus sessions plus a task completion retain distinct source IDs; focus cannot support an achievement; generated versions freeze their source snapshots.
- Feed an adversarial model bullet saying a task was completed while citing only focus sources in DAILY and WEEKLY PROGRESS. Assert persisted text is rewritten to frozen investment facts, retains source references, and does not carry the model's completion claim.
- Browser: same session across refresh/pages/tabs, explicit start after task navigation, global guidance and sound fallback, account cleanup, five reachable nav items, records page without timer controls.

## 7. Wrong vs Correct

Wrong: classify every hidden tab as a missing interval or run an unobserved interval through `RUNNING → MICRO_BREAK → RUNNING`. The former interrupts normal work in other applications; the latter invents breaks and reminders the user could not observe. Likewise, a prompt alone does not prevent the model from writing “completed task” for focus-only PROGRESS evidence.

Correct: credit continuous server-bounded focus while `RUNNING`; start a microbreak only on a visible `BREAK_DUE` command, and let explicit pause/end govern stopped work. Settlement derives daily records from elapsed focus/break intervals. Render focus-only progress from frozen structured facts, not model prose.
