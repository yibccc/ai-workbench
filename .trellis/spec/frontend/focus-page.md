# Focus page and account-scoped controller

## 1. Scope / Trigger

Use this contract when changing `#focus`, task-to-focus navigation, retained workspaces, audio/reminder presentation, or focus API calls. The focus session belongs to the authenticated account and remains visible through a compact shell control when another page is selected.

## 2. Signatures

- `api/focus.ts` exports `FocusSession`, `FocusRoutine`, `fetchFocusCapabilities`, `fetchCurrentFocus`, `startFocus`, `checkpointFocus`, `transitionFocus`, `recoverFocus`, `endFocus`, `saveFocusProgress`, `fetchRoutines`, `fillToday`, and `fetchFocusToday`.
- `useFocusController(accountId, onSettled)` owns the current session, 20-second visible checkpoints, server lease, BroadcastChannel refresh, audio context, and account cleanup.
- `FocusPage` owns the visible timer, routine editor, today's totals and optional progress input. `AppShell` owns the compact status/return control outside page bodies. `TasksPanel` only passes task context to `FocusPage`.

## 3. Contracts

- The default business page remains `#records`; five navigation entries include `#focus`. Keep existing records/tasks/reports/projects retained views and their drafts/filters. Navigation from a task populates `{taskId,title,projectId,targetMinutes}` but never calls start until the user explicitly confirms.
- The server `Session` phase and version are authoritative. Client projection may show up to the latest trusted anchor while visible; after hidden-to-visible, a >60-second gap, or a wall-clock jump, freeze projected time until a checkpoint returns. A stale 409 requires refetching the authoritative session. Do not display an unconfirmed gap as invested time.
- If an end request loses its response, GET that same session before declaring the outcome. If the server reports `ENDED`, adopt the settled state, refresh records and expose progress entry; otherwise keep a retryable error. Repeating end must not create another daily record or overwrite progress.
- `MICRO_BREAK` guidance renders at the account root, including when another business page is selected. The 15-second guidance says only that the user may close their eyes; it does not claim sensing them. Starting and ending sounds need separate visual notices. Sound is opt-in; `AudioContext` rejection or suspension shows an actionable error while visual controls remain usable.
- Audio failure feedback must be visible in the current account view even when the focus `RetainedView` is hidden. On another page show an account-root alert with a return/reenable action; while the microbreak overlay is open, show that alert inside the overlay regardless of the selected page. A successful user-gesture reenable clears the alert.
- The server lease identifies which tab may play a reminder. A BroadcastChannel speeds refresh but never authorizes sound. Do not replay an old reminder after a refresh or long hidden period. Account logout/401/A→B remount closes audio, timers and channel, and discards the old session; automatic checkpoint/status requests do not signal `/api/auth/activity`.
- `FocusPage` contains timer, routine settings and today's totals. The records page keeps its original form, date selection, list and pagination, and shows settled focus only as a list source. Session end does not complete a task. The compact control outside records can pause/continue and return to the same session.
- An unlinked new timer form defaults to 45 net minutes. Clicking or focusing its numeric target input opens a four-option dropdown for 15, 25, 45, and 60 minutes; the user can still type any valid integer from 1 to 480. Escape closes the list, Tab reaches its options, and Enter selects one. The former quick button below the input is absent. Linked tasks and routine occurrences keep their own saved defaults; starting a new unlinked session after settlement resets the form to 45. The timer uses a single main panel without a separate sound/help side card. The explicit Start gesture activates audio before awaiting session creation; a restored active session offers a compact inline enable/preview action and inline failure feedback in the main panel.
- `GET /api/focus/capabilities` supplies `writeEnabled`. The backend defaults `FOCUS_WRITE_ENABLED=false` during compatible-reader rollout. New routine/occurrence/session creation is closed, while an existing session may still be controlled and settled. Keep historical evidence readable and make unavailable creation clear in the UI; enable E2E writes explicitly in its isolated environment.

## 4. Validation & Error Matrix

| Condition | UI result |
| --- | --- |
| No active session | Show temporary/linked start form; no automatic creation on navigation |
| Stale version or second-tab change | Refetch same session; never replace the active goal |
| End response lost after server commit | GET same session, show recovered settlement and progress entry; one record only |
| `RECOVERY_REQUIRED` | Show exact server pending range and explicit confirm/discard controls; no projected credit before choice |
| Audio unavailable, rejected or suspended | Visible sound failure; visual 15-second guidance stays usable |
| Audio fails while reports/tasks are visible or microbreak overlays focus | Current visible page/overlay shows the failure and recovery action; hidden FocusPage alert alone is insufficient |
| Audio context lost after page refresh | Restored session stays authoritative; main timer panel offers an explicit user-gesture enable/preview action |
| Routine fill blocked by archived project | Show specific blocked feedback and keep other generated occurrences |
| Routine fill network error | Visible retry; do not silently omit today's tasks |
| 401 or account switch | Unmount old timer/audio/requests and show the correct new identity state |
| Focus creation gate closed | Show rollout state; do not issue automatic `fill-today` or offer new routine/session creation, but allow an existing session to finish |

## 5. Good / Base / Bad Cases

- Good: take task T1 into `#focus`, verify context, click Start, move to reports, pause/continue in the shell, return to the same session, then end and see a focus source in records. T2 navigation during T1 must not replace T1.
- Base: a temporary title starts without a task; reload or a second tab reads the same server session. Routine edits affect future occurrences only.
- Bad: mount separate audio/timers in each retained page, put the full timer in records, autoplay on task navigation, infer confirmed duration from `setInterval`, or treat device mute as proof the user heard a sound.

## 6. Tests Required

- Playwright with isolated backend: explicit start after task navigation, same ID across refresh/page/tabs, compact pause/continue, five-item navigation at 320/390/760/1440 pixels, accessible keyboard focus, records pagination and drafts, routine fill feedback, and A→B cleanup.
- Assert the unlinked default is 45, input click shows exactly 15/25/45/60 options, custom input and keyboard selection work, saved task/routine durations override the default, and the lower quick button and sound/help card are absent. Start must attempt audio activation, and a refreshed active session can re-enable audio inline without losing its session.
- Inject `AudioContext` rejection/suspension and assert visible fallback; inject long hidden period or wall-clock jump and assert no unconfirmed projection or replay. Assert passive sync never sends `/api/auth/activity`.
- Drop an end response after the server commits and assert the browser recovers the ended session, refreshes the record, offers progress entry, and a repeated end still maps to one record.
- Manual evidence must name the actual browser/version and entry URL when claiming audible start/end, device sleep recovery, or HTTP/IP/HTTPS compatibility. API call counts alone do not prove sound was heard.

## 7. Wrong vs Correct

Wrong: increment focus time on every browser timer callback and keep playing from a retained hidden view after account switch.

Correct: display a bounded projection from server state, checkpoint on returning to the foreground, freeze while confirmation is pending, and keep one controller at the authenticated account root. Clean it up when that account unmounts.
