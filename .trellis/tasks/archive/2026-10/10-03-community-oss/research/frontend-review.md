# Research: Frontend planning review

- Query: Static review of parent PRD/design/implement and community-ui PRD/design/implement for R2 completeness, approved defaults/public fields, current source projection, latest preview publication, retained input/focus, scroll containment and scope drift.
- Scope: internal; planning review against previously verified real frontend/spec/prototype evidence.
- Date: 2026-10-03 (Asia/Shanghai)
- Active task: `.trellis/tasks/10-03-community-oss`.
- Writes: this file only. No product edits, task activation, prototype/script execution, dependency installation, tests or git operations.
- Concurrency: main session is revising planning documents. Findings below identify the text observed during the review; items in the resolved section were re-read after concurrent fixes. Parent should apply remaining corrections and recheck its latest text.

## Findings

### Reviewed files

| File | Scope / evidence |
| --- | --- |
| `.trellis/tasks/10-03-community-oss/prd.md` | FR01–10, all22 AC, R3/current-user storage adaptation, non-goals and approval boundary. |
| `.trellis/tasks/10-03-community-oss/design.md` | Canonical hash/API, source projection, schema, snapshot/attachment transaction and root/layout contracts. |
| `.trellis/tasks/10-03-community-oss/implement.md` | Child ownership/dependencies, UI completion and actual verification commands. |
| `.trellis/tasks/10-03-community-ui/prd.md` | UI scope and parent AC inheritance. |
| `.trellis/tasks/10-03-community-ui/design.md` | Shared request/Blob pipeline, local input versus saved draft, root and R2 reuse. |
| `.trellis/tasks/10-03-community-ui/implement.md` | Page/flow extraction, screenshot/real E2E evidence and regression gates. |
| `frontend/package.json:6–12` | Actual lint/build/e2e script names, verified again in this review. |
| Parent `research/frontend.md` | Already verified actual App/HTTP/login/source/CSS/e2e/prototype anchors; original candidate route names are superseded by parent design. |

### Remaining concrete defects and minimum corrections

#### FRV-01 · High: Approved blog summary is absent from persistence/API contract

- Evidence: immutable R2 `assets/prototype-r2/src/app.js:90` renders the optional BLOG summary input. `:67` uses summary for feed excerpts; `:111` renders it in reader/preview article.
- Reviewed parent design: draft/revision tables (`:44–45` after route additions) list title/body only; atomic publish payload (`:63`) has title/bodyMarkdown but no summary; create/save API rows (`:80–82`) likewise omit summary. UI plan refers broadly to full R2 editing but does not define where this input survives.
- Impact: an implementation can preserve the field visually but discard its text after save/reload/publication, or remove an approved editor/article region. This violates R2 AC22 and input preservation.
- Minimum correction: add optional `summary` to private draft, immutable revision and relevant create/get/save/publish/reader DTOs; include it in publication fingerprint and transaction, and in client edit snapshot/dirty/late-save ownership. Keep BLOG-only visible input exactly at the R2 position and feed/article behavior. No new page, feature or user decision is needed.
- Test: write summary, preview, manual-save/reload, publish, save a changed summary and verify reader still sees the previous revision until explicit update; pending-save/newer-input test must include summary.

#### FRV-02 · High: Planned frontend test command does not exist

- Evidence: `frontend/package.json:11` defines `e2e: playwright test`; there is no `test:e2e`.
- Reviewed parent `implement.md:70,73` and UI `implement.md:12` use `test:e2e`.
- Minimum correction: use `npm --prefix ./frontend run e2e`. For focused suites use `npm --prefix ./frontend run e2e -- e2e/community.spec.ts` (after creation) and existing `e2e/workbench.spec.ts e2e/focus-alarm.spec.ts`. Do not create an unnecessary script alias.
- Maintain explicit isolated PostgreSQL `d9_e2e` and nondefault Redis prerequisites from the actual Playwright config. This static review did not execute the command.

#### FRV-03 · Medium: Formal design should state the bounded community scroll owner

- Parent `design.md:9` fixes CSS scoping and218/192/mobile sidebar, and delegates exact source anchors to frontend research. UI design `:3` similarly says to reuse R2 while keeping original workspace CSS.
- These statements do not name the community scroll region or original root height-chain adaptation. Original R2 uses document growth; real `styles.css:16,50,79,118–121` bounds document overflow and `.main-content`.
- Minimum correction: explicitly state `.community-shell` retains the100dvh root chain, sidebar/topbar and mobile bottom nav outside a positive-height named/focusable `.community-scroll`; R2 main width1320/padding/columns remain inside it. Scope all original global resets/variables and portal/dialog styles; adapt sticky offsets to the new scroll owner rather than altering page grouping.
- Add observed geometry checks: no document horizontal/vertical overflow, end-of-content/pager reachable, mobile save/preview above bottom nav, and viewport images at specified scroll offsets. Existing frontend research describes this; one formal design paragraph plus the UI verification line is sufficient.

#### FRV-04 · Medium: Scope the new leave guard to avoid changing retained original-workspace behavior

- Parent `design.md:31` currently says unsaved space/date/post/back navigation always invokes a shared Dialog. Parent AC07 and UI root contract preserve original five-workspace drafts.
- Existing `ReportsPage.tsx:27–29` and frontend directory/viewport specs preserve original report inputs on work-area navigation without discarding/remounting them.
- Minimum correction: state that the R2 three-action leave guard applies to leaving/changing the community editor context. Original records/reports/tasks retain their current drafts/filters across workbench/community navigation. The new guard must not treat those original retained drafts as a global discard requirement.
- Preserve cancel/no-navigation, save-failure/input retention and beforeunload exception. This is a wording/ownership clarification, not a new product choice.

#### FRV-05 · Medium: Pin the original content-type switch and complete source-only states

- The plans require all R2 states and refer to the source inventory, which is correct, but the UI implement list does not spell out type-switch behavior.
- R2 `app.js:160–162,200–201`: clicking another content type confirms “create another type”, saves current input as a private draft, then opens a new editor or DAILY sources; it does not mutate the existing article's type. The dirty leave modal has continue/discard/save-and-leave; image and copy fallback have distinct dialogs.
- Minimum correction: add this exact type-switch rule to UI design, plus a page/state coverage row referencing the research inventory: compose, feed/type-filter, source empty/paged, editor/new/published edit/attachments failed, preview desktop/mobile single confirmation, detail/gallery/lightbox/download error, mine states including HIDDEN in all, author empty, login, unavailable, loading/retry/empty, dirty/type/withdraw/copy dialogs.
- Preserve R2 type chooser as a page in the current canonical route `#/publishing/new`; do not map original `#/compose` or add another candidate route family.
- This closes implementation ambiguity without reopening already approved product behavior.

### Corrections already observed as resolved

- **Latest inspected input publication:** parent `design.md:63,81–82` and UI `design.md:9` now define one publish payload and transaction saving private draft plus immutable revision/reference switch. No two-step hidden save/publish remains in those re-read contracts. Fingerprint is based on the submitted immutable payload; retry preserves identical request ID/payload and never reapplies an old command after withdraw/hide.
- **R2 compose and preview targets:** parent `design.md:26–29` now includes `#/publishing/new` and `#/publishing/posts/<uuid>/preview`; these supersede the earlier research candidate aliases.
- **Mobile workbench return:** parent `design.md:31` now fixes the real account/space menu action and retains the four R2 mobile entries.
- **Reader revisit:** parent `design.md:31` explicitly refetches/reauthorizes detail and removes inaccessible old projection.
- **New-form navigation side effects:** parent `design.md:31` keeps direct new-form opens local/read-only; private draft creation is tied to explicit save/upload/publish action.
- **ADMIN action version boundary:** parent `design.md:65` now uses public expectedRevisionId with reason, rather than requiring a private author aggregate version.

### Contracts that are already sound

- Parent PRD includes all22 AC and explicitly preserves original private-data/CSRF/401/session/date/focus contracts; local RustFS now and deferred Alibaba adapter/API are clear current-user scope.
- Parent/API author DTO is a separate nickname/biography projection, default “未设置昵称”, with no login username; own edit lives in an existing author operation area. No unresolved product choice is needed.
- Source composition uses current same-owner/day WorkRecord display projection and full-page query, not raw ledger/current-page-only dedup; includeFocus is separate and does not increase completed tasks. IDs/provenance are writer-only; same-day multiple posts permitted.
- UI plan separates local input, draft/version and in-flight save/upload acknowledgements; permits continued input and only clears dirty for the acknowledged exact snapshot.409 retains inputs.
- Root Workspace remains keyed only by account ID; one useFocusController/real chip/audio and original RetainedView tree persist across spaces.401/account switch aborts old requests/Blob URLs;403/404/network keep their own meanings.
- R2 direct CSS/JSX extraction,18px icons/card/button grouping, mock-tool removal, real LoginPage password-eye/username focus, shared Dialog/HTTP identity pipeline and no route library/legacy mapping are correct.
- Markdown10.1.0/skipHtml/no raw HTML/no GFM/no external-image fetch, current-attachment UUID allowlist and authenticated Blob images/downloads match the approved boundaries.
- Attachment quotas and failure states are correctly current-set/real-bytes based; PDF/MD are download only. No social/restore/anonymous/cloud/compatibility scope was added.

## Code patterns / source anchors

- `frontend/src/App.tsx:65–75,125–127,168,199–212`: true401 identity cleanup, account-keyed root, sole controller/retained original views/global overlays.
- `frontend/src/api/http.ts:43–71`: common identity/CSRF/FormData behavior and decoder extraction point.
- `frontend/src/features/auth/LoginPage.tsx:24–25`, `frontend/e2e/workbench.spec.ts:140–170`: autofocus/eye/keyboard/value/no-submission contract.
- `frontend/src/features/reports/ReportsPage.tsx:17–29`: date dirty guard versus retained workspace navigation.
- `frontend/src/components/Dialog.tsx:8–17`, `DialogProvider.tsx:8–39`: modal focus/cancel/busy/duplicate-write guard.
- `frontend/src/api/records.ts:5–23,35`, `hooks/usePagedList.ts:13–41`: actual fields, size5 paging and cancellation/query ownership.
- Immutable R2 `app.js:67,77–90,110–118,160–166,200–201`: summary/type chooser/editor/reader/state/modal contracts.
- Immutable R2 `styles.css:1–9`: exact218/192/sidebar-hidden,68/60 topbar,1320 main and mobile action/preview responsive rules.

## Related specs

- `.trellis/spec/frontend/directory-structure.md:28–45,47–61`: root/state retention, feature/API boundaries, single request ownership.
- `.trellis/spec/frontend/identity-session.md:16–28,51–58`: auth cleanup/activity/true401 versus other errors, eye verification.
- `.trellis/spec/frontend/focus-page.md`: unique root controller, same-day effective projection, account/global sound cleanup.
- `.trellis/spec/frontend/viewport-layout.md:5–24,40–60`: bounded root, reachable named internal scroll, retain state and observed geometry.
- `.trellis/spec/frontend/dialogs.md:5–20`: shared native dialog, failure retention and trigger restoration.
- Parent immutable R3 requirements AC03/04/07/08/09/10/13–22 and HANDOFF DEC11–13/16–17.

## External references

No new web lookup was necessary for this static review. The already verified official react-markdown10.1.0 reference and constraints are persisted in `research/frontend.md`; this review adds no new dependency/version claim.

## Caveats / Not Found

- Review was static; no build/E2E/physical sound/storage/visual rendering result is claimed.
- Main session owns all planning corrections. This reviewer did not edit parent/child documents and must not overwrite their concurrent changes.
- Apart from the concrete technical gaps above, no unresolved user product choice or new scope was found. Summary preservation, canonical compose/type-switch handling, bounded scroll and correct existing command are recoverable within already approved behavior.
- Parent should verify FRV01–05 against its latest texts and remove stale wording before the one final planning approval. Original prototype “pending” labels do not reopen DEC16/17.
