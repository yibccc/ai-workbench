# Private Resume and Interview UI

## 1. Scope / Trigger

Use for features/profile, features/interview, api/resume.ts/api/interview.ts, account-root navigation and shared Dialog/guard/Markdown. R1 content HTML/CSS is directly converted to scoped JSX/styles; retain the existing shell, focus controller, HTTP, Dialog/Toast/Pagination. Demo hash tabs, mock identities and global body/shell CSS are not production code.

## 2. Signatures

- PageId adds interview at #interview as the sixth retained workspace; #profile is an auxiliary AccountMenu view.
- API types match ResumeModels/InterviewModels and [private backend contracts](../backend/private-interviews.md). List uses InterviewSummary; detail has questions/answers.
- Profile PUT mode=EDIT_CURRENT|PASTE; import File+markdownText+expectedVersion+requestId; DELETE query version/requestId; write Receipt followed by authoritative GET.
- Interview writes return Receipt(sessionId,resultVersion,...), JD parse/retry return JdAnalysis; use existing request/requestBlob with identity epoch/CSRF.
- SafeMarkdown displays image alt text instead of remote img, skips HTML and permits only safe explicit links.
- useDismissDialogs cancels active confirmation with false and generation-fences late callbacks during401/logout/login identity change.

## 3. Contracts

Keep six retained workspaces and account-root focus lifetime. Personal resume is not community public profile. Private profile/account management are auxiliary routes, never a seventh main navigation item. Mobile navigation reaches all six entries; page/header/workspace-scroll stays within viewport and preserves original drafts/filters/focus.

Maintain server current and editor draft separately. File selection validates MD/actual bytes/fatalUTF8/code points then loads the editor without mutation; retain original File while editing final Markdown. Ordinary edit explicitly uses EDIT_CURRENT and preserves original; explicit PASTE replaces source, new File uses multipart. Source metadata/downloads reflect server acknowledgement, not a reconstructed File.

Keep draft editVersion independent of background current metadata. A whole GET adoption must satisfy version >= currentRef.version and >= acknowledged editVersion/resultVersion; reject stale metadata and text together. Save acknowledgements only clear their own input revision; typing during IO remains dirty. A409 preserves input, and adopting a new CAS version for overwrite needs the existing Dialog, not silent refresh.

Count Unicode code points with Array.from(text).length for20kresume/10kJD/5kanswer, not UTF16 length/maxLength truncation. Empty saved resume is exists=true, empty draft is valid, empty submit is SUBMITTED. Original and edited text each meet the20k gate independently of byte size; a1MiB stage pass does not imply complete import passes.

Creation explicitly separates new vs continue, current resume vs none, four directions, three difficulties and all3..20 main counts; display2N rounds. JD parse is explicit, each character/direction change invalidates/deletes old analysis and clears old retry tuples. Once an analysis exists, failed parsing retries its ID; state reads are pure GET, not a fresh parse. Successful analysis may be reused for multiple unchanged creations; success is not consumption.

Keep requestId+complete original tuple across unknown acknowledgements; same POST replays before GET confirms actual server state. A successful submit/complete receipt proves the old round is frozen even if GET then fails: lock writing immediately, wait for a Session version>=resultVersion, show a distinct refresh failure and retry read. Never enable a new mutation on stale old-round data. For unknown submit confirmation, require persisted SUBMITTED text equal to the original tuple before claiming success.

Ignore older read generations/versions and stop hidden-view polling. A404 clears the whole private session/question/answer/report projection and invalidates late reads;403/network/validation do not pretend session expiry. A real401 removes account tree, File/Blob/poll/request/dirty state and active dialogs. Late callbacks cannot close a newer dialog or execute old Profile DELETE under B's cookie.

Early complete shows submitted/unanswered counts, explains unanswered zero and uses shared destructive Dialog. Report submitted failure is null/NOT_EVALUATED and never ??0; UNANSWERED0 remains known when a mixed group is pending/failed. Total null means no complete score. Render persisted feedback safely, preserving exact backend rounded result.
ReportGroup.mainIndex is the main-question turn index (0,2,4...), not a sequential group ordinal; display floor(mainIndex/2)+1. Use real turn indices in fixtures so three groups label1/2/3 rather than1/3/5.

## 4. Validation & Error Matrix

| Condition | UI result |
|---|---|
| Field/file too long, malformedUTF8 or save failure | Preserve entire input/File and old current; no truncation/swap |
| Slow GET older than adopted or acknowledged version | Reject whole adoption, including editor text |
|409 version conflict | Keep draft; explicit read/confirm before new version mutation |
| Write receipt succeeded but refresh failed | Old input read-only, separate refresh status, no new write until authoritative version |
| JD failed/input changed | Manual existing-ID retry or new explicit parse after invalidation; old retry tuple cleared |
| Poll/detail/report404 | Clear private projection, no stale report resurrection |
|401/logout/A->B | Cancel dialogs and old requests/Files/URLs/polls, no cross-owner mutation |
| Mixed failed report | Submitted=null, unanswered0, total null; manual retry only |

## 5. Good / Base / Bad Cases

Good: import A then edit, create1, change current B/create2, preserve each draft across navigation/refresh/relogin, and delete one without disturbing the other. Base: no-resume general session and a failed model report with manual retry. Bad: copy the prototype shell/mock script, clear typed text from a slow GET, advance optimistically after submit, leave confirm promises alive over401, stringify false into server choice, or keep deleted report bodies after404.

## 6. Tests Required

Lint and strict tsc/Vite. Controlled UI fixtures prove loading/failure/unknownACK/late GET/currentCAS/JD retries/report null/404/identity-dialog behavior, not backend ownership. Assert failure before each race fix, then same regression passes. Capture all six content screens at320x520,390x520,760x700,1440x900; document no page overflow, positive scroll regions, last controls reachable, six navigation and retained focus. Raw selector/declaration mapping remains tied to R1 and its known PNG differences.

Real isolated PG/Redis/RustFS+deterministic backend: File import/edit/save, resume A/B frozen sessions, multiple independent submit/draft states, refresh/relogin, early evaluation, current deletion and single-session deletion; verify raw download/SHA and DB state. Run old workbench/focus/community suites plus Dialog regression. E2E config keeps database/schema/Redis guards, synthetic accounts and empty live-model keys; Vite/Chromium never inherit DB/model/storage/root secrets.

## 7. Wrong vs Correct

Wrong: adopt rejects old Current but caller still assigns next.markdownText. Correct: one version-aware whole-adoption result owns both metadata/editor changes. Wrong: submit succeeded, GET failed, catch treats it as unsent and re-enables old input. Correct: receipt establishes a write barrier until authoritative progress is recovered. Wrong: DialogProvider above account root lets an old confirmation execute under B. Correct: identity transitions dismiss and generation-fence the promise before mounting B.
