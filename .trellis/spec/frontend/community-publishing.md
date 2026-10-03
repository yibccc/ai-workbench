# Community and Publishing UI

## 1. Scope / Trigger

Use this contract for community/publishing features, shared hash parsing, protected binary requests, account/space chrome and R2 visual reuse. Backend contracts are [Publications](../backend/community-publication.md) and [Attachments](../backend/private-attachments.md). The existing five-workspace/focus/dialog contracts remain authoritative for their original behavior.

## 2. Signatures

- `components/layout/routes.ts`: `parseHash(hash)->ViewId`, `isCommunityView(view)`, `routeTitle(view)`.
- Current native hashes remain `#records/#tasks/#focus/#reports/#projects/#users`; new paths are `#/community`, `/community/posts/<uuid>`, `/community/authors/<uuid>`, `/publishing`, `/publishing/sources`, `/publishing/new`, `/publishing/new/<DAILY|MOMENT|BLOG>`, `/publishing/posts/<uuid>` and its `/preview` suffix.
- `api/community.ts` / `api/publishing.ts` consume current typed public/owner DTOs; shared `api/http.ts` owns JSON/Blob decoders, Cookie/CSRF, identity epochs, cancellation and safe ApiError fields.
- Upload entries retain original `File`, `postId`, `expectedVersion`, `requestId`; maintenance returns `{version,results:[{attachmentId,state,safeFailureCode}]}`.

## 3. Contracts

- `Workspace` is keyed only by account ID. Space changes keep the same AppShell main DOM path, original five RetainedViews and sole focus controller/audio/realtime lifetime. Logout/real401/account switch unmounts the account tree, cancels old requests and clears/revokes protected Blob state. Network/403/404 keep distinct meanings. Passive data/file reads never signal activity.
- Parse the allowed target before `/auth/me`; legitimate protected deep links survive login without loading protected content early. Only complete known patterns/UUIDs are targets; unsupported/unauthorized paths show unavailable. No arbitrary external redirect or old-prototype hash aliases are added. Compare API UUIDs case-insensitively because accepted URI UUIDs may be uppercase while JSON is lowercase.
- Every community navigation path, including ADMIN account-menu users navigation, uses the shared hashchange editor leave guard. Pending writes cannot silently leave their context. Community dirty guard offers continue/discard/save-and-leave; cancel/failure preserves input and target. Original workbench drafts remain retained, not globally discarded. Preview↔same editor retains unsaved input. Changing type explicitly saves old private draft, then opens another editor/DAILY source picker.
- New-form navigation does not POST by itself. Only explicit save/upload/publish creates a private draft. Save and acknowledged server snapshot are separate from live local input. Late acknowledgements do not replace newer title/summary/body/files or clear dirty improperly. Owner rereads update the saved baseline and version while preserving local input; a409 requires an explicit version read and user retry, never automatic overwrite.
- Publication submits the exact confirmed full tuple; retry retains identical requestId/payload. A replay result is not current authority: re-read current owner lifecycle/refs/version before UI adoption. Save never makes a publication public; first publication time is not changed by editing.
- Upload UNKNOWN/ACTIVE status retries the original payload/key/version and does not assume failure. A new key is permitted only after a documented durable FAILED result (or an explicit no-reservation version-conflict retry). Do not identify the uploaded object by filename/size: identical files may be distinct IDs. After READY replay, verify its exact ID/current reference from owner GET. If owner GET fails, retry that read without another upload. Do not silently rebind a removed file or grant a historical result_version as a current token.
- Every cleanup/recovery result must be checked; preserve text and other usable files on failure. Root server quota is authoritative. Current collection counts ten distinct IDs/50MiB; historical retained files do not inflate it. PDF/MD only download, never inline preview/import/body conversion.
- Source selection uses five-row physical paging and a date-scoped ID map, default empty/CONTENT and false focus opt-in. Card display may be concise, but composition uses complete selected server text. Date change guards/reset page/selection/focus; private project/time/task/session fields never leak into public DTOs. Do not client-deduplicate one page of the server's completion/focus merge.
- Use pinned react-markdown10.1/skipHtml with no raw HTML/GFM, http(s)-only ordinary URLs and current ready-UUID `attachment:` image allowlists. No external image fetch. Image/download Blob reads use the shared identity pipeline, rechecking after body decode and revoking URLs on replacement/unmount/account cleanup. TOC buttons scroll the document without replacing the app hash.
- Directly reuse R2 CSS/template grouping from `features/community/community.css`; production imports do not depend on current Trellis task/input-package directories. Scope original resets/variables and explicitly theme native Dialog portals. Preserve 218/192 desktop sidebar, 68/60 topbar, 1320 main width/columns and mobile four-entry bottom navigation. `.community-scroll` owns positive bounded height under the 100dvh chain; mobile fixed actions sit above bottom navigation. New community route resets only its inner scroll position.
- Original mobile workbench navigation must not gain a duplicate space-switch row that compresses its five-item view; the real account menu supplies the community entry. Community mobile has its workbench return entry. Preserve password eye behavior/username focus; no demo users/status controls/static timer/embedded private fixtures in production.
- `DialogOptions.className` is an optional caller theme on the existing native modal primitive; focus/inertness/Escape/busy/destructive-cancel rules remain shared. Data-backed profile input focuses on first mount after its asynchronous read.
- The E2E Vite launcher allowlists only OS/frontend-needed environment. DB/model/storage/root/AWS credentials never reach that frontend child. Evidence defaults to test-results; an explicit run may copy screenshots to a task, without permanently recreating archived task paths.

## 4. Validation & Error Matrix

| Condition | UI result |
|---|---|
| Protected initial target with no session | Real login gate; successful login returns only allowed target |
| Wrong-role users target after login | Unavailable and no admin view; identity still valid, explicit navigation available |
| Current post/ref becomes404 | Clear the inaccessible projection, do not reuse historical protected content |
| Real401 / network or403/404 | Account-tree cleanup / distinct retry or permission feedback |
| Stale save/publication | Keep inputs; explicit read/retry and conflict feedback |
| Lost upload response / confirmed FAILED | Replay original tuple / new attempt key only after terminal proof |
| Failed download | Keep body; show safe retry feedback |
| Unready selected reference | Preview/publication confirmation blocked, without claiming upload success |

## 5. Good / Base / Bad Cases

- Good: edit while an earlier save is in flight; its acknowledgement updates the saved baseline but the newer input remains dirty. A same-name/same-byte upload response loss is retried without a second object/reservation.
- Base: no materials/no attachments/unset nickname render the R2 empty regions; a new editor is local until a deliberate action.
- Bad: remounting Workspace on space navigation, defaulting an author nickname to login username, native #TOC anchors leaving the article, replacing unknown upload keys, or presenting an in-flight preview screenshot as the published reader.

## 6. Tests Required

- Real HTTP/PG/Redis/RustFS browser flows: all types, source≥two pages/date reset/public fields, latest body/summary preview-publication, first timestamp/history, late-save409, publish/upload lost ACK, F1/F2 byte SHA, PDF/MD download, images/lightbox, withdraw/adminhide, deep-link login and A→B delayed Blob/upload.
- Retain original workbench/focus-alarm suites: drafts/filters, single timer/audio, all five navigation entries, mobile short-height last-row reachability, explicit activity, true401 and account/role cleanup. Do not weaken viewport ratio or add legacy route fallback to satisfy an obsolete navigation expectation.
- R2 all17 source PNGs plus source-only compose/empty/loading/retry/unavailable/dirty/type/withdraw/copy/lightbox states. Compare corresponding viewport/scroll/group/action geometry; natural OS fonts/data lengths differ. Mark HTTP/layout fixtures as fixture evidence, separate from true business PASS.
- Test actual published response and reader URL before taking a detail screenshot; old preview DOM is not proof that a publish finished. Verify a mobile route resets community scroll to zero.
- Lint/type/production build and credential-free Vite/Blob/validator environments. Re-run affected tests for genuine source changes; do not sum separate passes into a fictitious full-suite total.

## 7. Wrong vs Correct

Wrong: catch any upload error, refresh the post version, generate a new requestId and try again.

Correct: retain the original tuple while the result is unknown, replay it to recover its exact attachment receipt, read current owner state and preserve local input. Only a proven failed attempt gets a new ID; conflicts remain explicit user decisions.
