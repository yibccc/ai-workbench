# Focus page and account-scoped controller

## 1. Scope / Trigger

Use this contract when changing `#focus`, task-to-focus navigation, retained workspaces, audio/reminder presentation, or focus API calls. The focus session belongs to the authenticated account and remains visible through a compact shell control when another page is selected.

## 2. Signatures

- `api/focus.ts` exports `FocusSession`, `FocusRoutine`, `fetchCurrentFocus`, `startFocus`, `checkpointFocus`, `transitionFocus`, `endFocus`, `saveFocusProgress`, `fetchRoutines`, `fillToday`, and `fetchFocusToday`. The former recovery command and pending fields are removed.
- `useFocusController(accountId, onSettled)` owns the current session, approximately 20-second foreground/background checkpoints while the page executes, server lease, BroadcastChannel refresh, audio context, and account cleanup.
- `FocusPage` owns the visible timer, routine editor, today's totals and optional progress input. `AppShell` owns the compact status/return control outside page bodies. `TasksPanel` only passes task context to `FocusPage`.

## 3. Contracts

- The default business page remains `#records`; five navigation entries include `#focus`. Keep existing records/tasks/reports/projects retained views and their drafts/filters. Navigation from a task populates `{taskId,title,projectId,targetMinutes}` but never calls start until the user explicitly confirms.
- The server `Session` phase and version are authoritative. Hidden tab or work in another application is normal: keep sending low-frequency checkpoints when the browser allows it, and on return synchronize immediately. Browser suspension or device sleep does not create a recovery prompt; the next server command credits elapsed `RUNNING` time up to the target. Client projection is display only and must be bounded by the target; after a wall-clock jump freeze it until a server checkpoint returns. A stale 409 requires refetching the authoritative session.
- If an end request loses its response, GET that same session before declaring the outcome. If the server reports `ENDED`, adopt the settled state, refresh records and expose progress entry; otherwise keep a retryable error. Repeating end must not create another daily record or overwrite progress.
- `MICRO_BREAK` guidance renders at the account root, including when another business page is selected. The 15-second guidance says only that the user may close their eyes; it does not claim sensing them. Starting and ending sounds need separate visual notices. Sound is opt-in; `AudioContext` rejection or suspension shows an actionable error while visual controls remain usable.
- Only a visible page issues `BREAK_DUE` and starts a microbreak. Background checkpoints credit focus but never fabricate completed breaks or replay missed reminders; returning to the foreground may start one currently due break if the target has not already ended.
- Audio failure feedback must be visible in the current account view even when the focus `RetainedView` is hidden. On another page show an account-root alert with a return/reenable action; while the microbreak overlay is open, show that alert inside the overlay regardless of the selected page. A successful user-gesture reenable clears the alert.
- The server lease identifies which tab may play a reminder. A BroadcastChannel speeds refresh but never authorizes sound. Do not replay an old reminder after a refresh or long hidden period. Account logout/401/A→B remount closes audio, timers and channel, and discards the old session; automatic checkpoint/status requests do not signal `/api/auth/activity`.
- `FocusPage` contains timer, routine settings and today's totals. The records page keeps its original form, date selection, list and pagination, and shows settled focus only as a list source. Session end does not complete a task. The compact control outside records can pause/continue and return to the same session.
- An unlinked new timer form defaults to 45 net minutes. Clicking or focusing its numeric target input opens a four-option dropdown for 15, 25, 45, and 60 minutes; the user can still type any valid integer from 1 to 480. Escape closes the list, Tab reaches its options, and Enter selects one. The former quick button below the input is absent. Linked tasks and routine occurrences keep their own saved defaults; starting a new unlinked session after settlement resets the form to 45. The timer uses a single main panel without a separate sound/help side card. The explicit Start gesture activates audio before awaiting session creation; a restored active session offers a compact inline enable/preview action and inline failure feedback in the main panel.
- The focus page exposes routine and session creation as soon as the authenticated workbench loads. Do not fetch a focus capability flag or show an unavailable-write notice; real fill/start failures still show their existing retryable error feedback.

## 4. Validation & Error Matrix

| Condition | UI result |
| --- | --- |
| No active session | Show temporary/linked start form; no automatic creation on navigation |
| Stale version or second-tab change | Refetch same session; never replace the active goal |
| End response lost after server commit | GET same session, show recovered settlement and progress entry; one record only |
| Hidden tab, app switch, browser suspension or sleep | Continue the same session from server time without a recovery dialog; user pauses explicitly when work stops |
| Audio unavailable, rejected or suspended | Visible sound failure; visual 15-second guidance stays usable |
| Audio fails while reports/tasks are visible or microbreak overlays focus | Current visible page/overlay shows the failure and recovery action; hidden FocusPage alert alone is insufficient |
| Audio context lost after page refresh | Restored session stays authoritative; main timer panel offers an explicit user-gesture enable/preview action |
| Routine fill blocked by archived project | Show specific blocked feedback and keep other generated occurrences |
| Routine fill network error | Visible retry; do not silently omit today's tasks |
| 401 or account switch | Unmount old timer/audio/requests and show the correct new identity state |

## 5. Good / Base / Bad Cases

- Good: take task T1 into `#focus`, verify context, click Start, move to reports, pause/continue in the shell, return to the same session, then end and see a focus source in records. T2 navigation during T1 must not replace T1.
- Base: a temporary title starts without a task; reload or a second tab reads the same server session. Routine edits affect future occurrences only.
- Bad: mount separate audio/timers in each retained page, put the full timer in records, autoplay on task navigation, infer confirmed duration from `setInterval`, or treat device mute as proof the user heard a sound.

## 6. Tests Required

- Playwright with isolated backend: explicit start after task navigation, same ID across refresh/page/tabs, compact pause/continue, five-item navigation at 320/390/760/1440 pixels, accessible keyboard focus, records pagination and drafts, routine fill feedback, and A→B cleanup.
- Assert the unlinked default is 45, input click shows exactly 15/25/45/60 options, custom input and keyboard selection work, saved task/routine durations override the default, and the lower quick button and sound/help card are absent. Start must attempt audio activation, and a refreshed active session can re-enable audio inline without losing its session.
- Inject `AudioContext` rejection/suspension and assert visible fallback. Exercise hidden background time beyond the former 60-second threshold and sleep-like long gaps: same session continues, no recovery UI appears, focus reaches at most the target, no unseen break is counted or replayed, and passive sync never sends `/api/auth/activity`. A wall-clock jump must not make client projection authoritative.
- Drop an end response after the server commits and assert the browser recovers the ended session, refreshes the record, offers progress entry, and a repeated end still maps to one record.
- Manual evidence must name the actual browser/version and entry URL when claiming audible start/end, physical device-sleep continuation, or HTTP/IP/HTTPS compatibility. API call counts alone do not prove sound was heard; device sleep no longer requires a recovery confirmation.

## 7. Wrong vs Correct

Wrong: pause checkpointing solely because `document.hidden`, demand recovery confirmation when the user works in another application, increment focus time on every browser callback, or play from a retained hidden view after account switch.

Correct: display a bounded projection from server state, keep low-frequency checkpoints while the page executes in foreground or background, synchronize on return, and keep one controller at the authenticated account root. The user explicitly pauses when work stops. Clean the controller up when that account unmounts.
