# Research: Frontend and direct R2 prototype reuse

- Query: Verify the real frontend against handoff WB-20261003-community-oss-3f9aaa-r3; define direct R2 layout/CSS/component reuse, real API integration, strict hash deep links and login return, identity/focus retention, and practical UI verification.
- Scope: mixed; repository and immutable prototype inspection plus official Markdown dependency references.
- Date: 2026-10-03 (Asia/Shanghai)
- Active task: `.trellis/tasks/10-03-community-oss`.
- Work performed: read-only source/spec/package inspection and local PNG viewing. No product edits, package installation, HTML/prototype execution, tests, lifecycle activation, or git operations.
- Input status: parent PRD was a seed at research start. Product behavior comes from handoff requirements/decisions and the current user instruction; storage is locally RustFS now, with Alibaba work deferred.

## Findings

### 1. Files found and verified source patterns

| Path | Purpose / verified pattern |
| --- | --- |
| `frontend/src/App.tsx` | Auth startup/root, hash view selection, identity-keyed Workspace, retained workspaces, one focus controller. |
| `frontend/src/components/layout/navigation.ts` | Five current business navigation IDs only (`:3–10`). |
| `frontend/src/components/layout/AppShell.tsx` | Existing sidebar, topbar account menu, main focus target and compact real focus controls (`:14–29`). |
| `frontend/src/components/RetainedView.tsx` | Mount once on first visit, then use hidden state (`:3–7`). |
| `frontend/src/api/http.ts` | Same-origin Cookie/CSRF, request cancellation and identity epochs; FormData support; JSON-only success decoding (`:13–21,43–71`). |
| `frontend/src/api/auth.ts` | Real me/login/logout/activity API; Account has username/role but no public nickname or biography (`:3–10`). |
| `frontend/src/features/auth/LoginPage.tsx` | Real login form, hidden-password default, eye SVG/action label, username autofocus (`:5–30`). |
| `frontend/src/features/auth/AccountMenu.tsx` | Existing ADMIN entry, password change/logout and Escape focus restoration (`:15–22`). |
| `frontend/src/components/Dialog.tsx`, `DialogProvider.tsx` | Native modal/inert background, initial/restored focus and synchronous duplicate-submit guard. |
| `frontend/src/api/records.ts` | WorkRecord discriminated source/focus fields and Shanghai-date paged records API (`:5–23,35`). |
| `frontend/src/api/reports.ts`, `features/reports/useReportSources.ts` | Private frozen report evidence, keyed paging/AbortController patterns; these are not community reader APIs. |
| `frontend/src/hooks/usePagedList.ts` | One cancellable request owner and query-key protection against old date/filter rows (`:13–41`). |
| `frontend/src/features/records/RecordsPage.tsx`, `features/reports/ReportsPage.tsx` | Account-local date/filter state, retained manual/AI inputs and report tabs; report dirty date guard. |
| `frontend/src/styles.css` | Existing tokens, bounded viewport shell/scroll chain, true login/password-field styles. |
| `frontend/src/utils/date.ts` | `WORKBENCH_TIME_ZONE = Asia/Shanghai`, `localDate`, local datetime helpers (`:1–17`). |
| `frontend/playwright.config.ts`, `vite.config.ts` | Explicit isolated E2E database/Redis gate; backend/frontend servers and same-origin API proxy. |
| `frontend/e2e/workbench.spec.ts`, `focus-alarm.spec.ts` | Existing real auth, paging, draft/focus/dialog/viewport regression and mocked audio recovery coverage. |
| `assets/prototype-r2/src/app.js` | Original business-page HTML templates and browser-only state/events, 249 lines. |
| `assets/prototype-r2/src/styles.css` | Original responsive CSS, 9 lines; most selectors are compressed into line 1. |
| `assets/prototype-r2/src/fixtures.js` | Synthetic identities/records/posts/files and precise byte limits, 44 lines. |
| `assets/prototype-r2/index.html` | Inline CSS + fixtures + app, 305 lines; executable standalone output, not an importable production entry. |
| `assets/prototype-r2/{README,review-notes}.md` | Original prototype scope/history, page inventory, unsafe-to-reuse simulation boundaries. |
| `assets/prototype-r2/previews/*.png`, `mobile-overview.png` | All 17 individual screenshots plus the mobile overview were visually inspected as static images. |

Above prototype paths are relative to `.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3/`.

### 2. Keep the account root and current workspaces

- `App.tsx:41–49` checks `/api/auth/me` before rendering business views and distinguishes 401 from connection failure.
- `App.tsx:65–75,125–127` closes realtime/clears request identity at logout or true expiry and keys `Workspace` by stable account ID.
- `App.tsx:168` creates the sole `useFocusController(account.id, ...)` at the Workspace root. `:199–203` retains records/tasks/focus/reports/projects individually; `:206–212` renders completion alarms, reminders and password dialogs outside the selected page.
- Community belongs under this same account-keyed Workspace. A workbench/community space switch must not key/remount Workspace, create a second focus controller or replace the existing retained views.
- Reuse the live `focusStatus/focusToggle` contract from `App.tsx:186–194` and `AppShell.tsx:28` for the R2 chip position; no fixed `18:42`, sample sessions, duplicate audio or passive activity renewal.
- Use the existing shared Dialog/Toast primitives for new confirmations/errors. New visible community actions remain inside `.app-shell` / `.workbench-dialog` so existing explicit-activity listeners (`App.tsx:98–123`) apply. Do not add activity calls to fetching, upload status polling or image loads.
- A retained community overview may preserve filters/editor inputs. Reader detail/author data must refetch when entering/reentering a target; a 404 must clear its old protected projection rather than revealing previously cached content as a new successful read.

### 3. Direct R2 UI/CSS reuse map

Reuse original markup structure, class names, styles, dimensions, ordering, copy that still represents approved product behavior, toolbar insertion mechanics and quota formatting. Convert template strings to typed JSX and event callbacks; do not redesign the pages or mount the inline prototype.

| R2 template source | Production extraction / direct reuse |
| --- | --- |
| `app.js:58–63` shell | Community variant of shared AppShell: desktop workbench/community space switch, content/mine/profile navigation, topbar, real account menu and mobile four-entry bottom nav. |
| `:64–67` heading/steps/feedTabs/feedCard | CommunityHeading, PublicationStepper, FeedTabs, PostCard; keep author/badge/excerpt/checklist/attachment footer grouping. |
| `:68–76` community/emptyHTML | CommunityFeed with the three composer actions, first-publication sorting, continue-draft side card, approved empty/retry layout. |
| `:77` compose | Three publication-type choice rows; no additional types. |
| `:78–81` sources | DailySourcePicker: date, five-row paged private material card, selection counter/list, opt-in focus total, generate-draft action. |
| `:82–88` fileRow/attachmentEditor/publishSettings | AttachmentRow, AttachmentEditor, PublishSettings; same file-type icon areas, failure/retry/remove actions, dropzone, distinct-ID quotas and visibility confirmation. |
| `:89–91` editor | CommunityEditor with type tabs, title/optional blog summary, Markdown textarea/tools/status, attachment area and desktop/mobile save/preview positions. |
| `:93,110–114` image/article/preview | One PublicationDocument for draft preview and current reader projection, AuthenticatedImage, attachment download cards, author side card and TOC. |
| `:115` mine | MyPublications with all/draft/published/withdrawn tabs, rows, changed-draft notice, edit/current version/withdraw actions. Replace automatic-save wording with approved manual-save wording. |
| `:116` profile | AuthorProfile hero and visible published feed; public nickname/biography only. |
| `:117–118` login/unavailable | R2 gated shell/card placement, real login form and protected unavailable state. |
| `:160–166` modal layouts | R2 leave/withdraw/type-change/image/copy visual structure styled on existing native Dialog, preserving inertness/Escape/busy/focus semantics. |

Remove `demo-identity`, `demo-tools`, prototype version/footer, synthetic author/private-data arrays, `prototypeDemo`, fake timers/upload/download/publish delays, diagram-generated sample imagery and workspace placeholder. Do not copy `root.innerHTML`, document-wide event delegation or in-memory authorization as production mechanisms (`app.js:36–43,120–130,168–245`; `index.html:302–304`).

**Exact original layout values:**

- `styles.css:1`: sidebar width **218px**, fixed sidebar padding `30px 16px 20px`; topbar height **68px**, horizontal padding 36px.
- Main max-width **1320px**, `margin:0 auto`, padding `32px 38px 28px`; normal columns `minmax(0,1fr) 270px`, 24px gap; article reading layout uses 245px aside and max-width 1110px.
- `:2`: ≥1600px side column 290px, main top padding40px.
- `:3`: ≤1190px sidebar **192px**, main `27px 25px`, aside230px (reading215px), 20px gap.
- `:4`: ≤1000px single-column pages; hide community/reading/editor asides as defined; show sticky mobile publish actions.
- `:5`: ≤760px sidebar hidden, mobile topbar60px, main `28px 18px 98px`; bottom nav four equal columns with safe-area padding, base min-height62px; editor mobile actions sit above it (`bottom:62px`).
- `:7–9`: exactly one visible publication confirmation panel per viewport: desktop preview aside or mobile panel. Avoid duplicate IDs in hidden JSX copies.

**Scope and scroll adaptation:**

- Put reused prototype CSS in `frontend/src/features/community/community.css`, scoped under `.community-shell` / a community content root; this is a proposed new path, not an existing file.
- Move original `:root` variables to the community shell. Scope original global `p/h1/button/input/body` resets and shared names such as `.sidebar/.topbar/.card/.login-card/.notice/.muted`; loading the raw CSS globally would override existing workspace typography/navigation.
- Existing `styles.css:16,50,79,118–121` and viewport spec require the five workspaces to keep document overflow bounded. Keep the root 100dvh chain; place the R2 main content in one named positive-height community scroll region, retaining R2 content width/padding/order. Keep topbar/sidebar and mobile bottom nav outside that region.
- Sticky sidebar/topbar offsets should account for the new scroll owner, while original column grouping and mobile action positions remain unchanged. The change is scroll containment, not another layout.
- Native dialog portals/top-layer content need their community style class/variables explicitly because ancestor scoping may not reach portal content. Keep existing shared focus behavior.
- Mobile R2 has no usable workbench switch in its bottom nav. Local integration should expose the workbench/community switch through the real topbar account/space menu, preserving four R2 bottom entries and the existing five-entry workbench view. Parent should record this small navigation integration choice and verify keyboard/320px reachability.

### 4. Single strict hash contract; no compatibility layer

Current `App.tsx:24–33` accepts only bare `#records/#tasks/#focus/#reports/#projects` and ADMIN `#users`; unknown hashes are normalized to `#records`. `:153–165` listens for hashchange and focuses `#main-content` after navigation.

Parent chose a single typed strict parser, preserving these **current native routes** and adding community routes, with no router dependency or historical route mapping. Suggested canonical community route family:

| Hash | View |
| --- | --- |
| `#/community` | Feed |
| `#/community/compose` | Publication type chooser |
| `#/community/sources` | Private daily material picker |
| `#/community/editor/<draftUuid>` | Author editor |
| `#/community/preview/<draftUuid>` | Author draft preview |
| `#/community/post/<postUuid>` | Current published reader detail |
| `#/community/mine` | Own drafts/publications |
| `#/community/profile/<authorUuid>` | Public author projection |

Names are local technical suggestions to be unified with the final API design. Do not adopt original R2 `#/post/a`, `#/editor/local-1`, `#/workbench` or create redirects/aliases for them. Empty entry still selects the existing records default. Allow only complete known route patterns and validated UUIDs; reject extraneous segments, protocol-relative/absolute/external targets and malformed escapes.

Parse requested target independently of account before `/auth/me`; do not normalize a legitimate community target to records while login is pending. The auth root owns allowed current target state even when Workspace is unmounted. On successful login, use that same parsed target; server decides reader/author permission and returns401/404. Do not store arbitrary URL return parameters, full external URLs, tokens or prior-user content. After a true expiry discard the protected tree, keep only the allowed target, and refetch after login.

Keep hashchange/back/forward focus semantics: the same typed parser feeds document title, navigation selection, current target and `#main-content` focus; no `navigation.find(...)!` assumption for community detail pages. Leaving a dirty editor, including browser history navigation or space switch, must await one shared leave guard. Preview→same editor retains the exact input without an implicit save.

### 5. Material source fields and publication independence

**Real shape differs from fixtures.** Prototype `RECORDS` has separate `title/text/time/project/minutes` (`fixtures.js:12–20`). Real WorkRecord has `content`, `completionResult`, `source`, `taskId`, `businessDate`, `focusMs/breakMs/progress`, timestamps and optional project (`api/records.ts:5–23`); it has no standalone title.

- Reuse `fetchRecordPage(date,page,WORKSPACE_PAGE_SIZE,signal)` (`:35`) and `WORKSPACE_PAGE_SIZE=5` (`pagination.ts:1`) for the private picker. Do not use report evidence or fetched current-page items as the whole day's selection.
- Use a date-scoped selection Map/Set at the retained community feature level. Default selection and includeFocus are false; page changes preserve selected IDs/DTO summaries, date changes reset selection, focus option and page0 as R2 `app.js:173` does.
- Reuse `usePagedList` cancellable/query-key pattern; old date/page results cannot populate the new heading or selection.
- Picker labels can derive concise display text from real content; draft composition must use the complete selected public text, not a truncated card/title. Project name, exact event time, task/session IDs and unselected material are private defaults and must not be emitted in reader DTOs.
- The current records list is a server projection: same-day linked completion/focus can appear as one completion entry with summed timing (focus spec). Do not reconstruct or deduplicate only one frontend page. Backend daily-draft composition must use the same effective projection/owner/date rules as the picker and reject missing/inactive/foreign/date-mismatched IDs.
- Selected completion text contributes once. Only explicit includeFocus adds selected net focus totals; it never increments task completion counts. `businessDate`/localDate use Shanghai semantics, including non-Shanghai browser zones.
- Use a server composition operation such as `createDailyDraft({businessDate, selectedRecordIds, includeFocus})`, which revalidates all refs and returns an editable author-only draft. Exact endpoint/payload is a final backend contract, not a currently existing API.
- Source refs remain private provenance. Manual source edits/deletion, later AI/report generation or changes to project/task names do not mutate already-published body/attachment projections.
- Preserve manual save and dirty guards. Preview need not save implicitly: it renders the exact local input/ready attachments. Publication should atomically accept the inspected input plus expected version and idempotency key, rather than silently publish an older last-saved server draft. Parent must make this explicit in the backend publication contract.
- Save response ownership must mirror `usePagedList`/existing report protections: an earlier successful save cannot overwrite newer input, switch another selected draft, or incorrectly clear its dirty flag. 409 retains local text and provides explicit reload/retry feedback.

### 6. API integration needs

The following capabilities are absent from the current frontend and must be provided by the new community API module, with exact names synchronized across frontend/backend:

| Capability | Needed contract |
| --- | --- |
| Feed / author feed | Authenticated current visible publication summaries, first-publication descending order, pagination/filter; no private refs/username/email/session state. |
| Reader detail | Current publication revision, body, attachment metadata and public author; 401 anonymous,404 invisible, no older protected body fallback. |
| Own publications/drafts | Author-owned states, saved draft, current publication metadata, changed-draft indication and optimistic version. |
| Create/composition/save | New private draft; verified own/date source refs; manual saved text/attachment refs; expectedVersion conflicts. |
| Publish/update/withdraw | Explicit checked content, stable logical request key, expectedVersion; preserve first published time; atomic current revision/attachment switch. |
| Public profile | Public nickname/biography/readable publications; real Account.username must not become the public nickname fallback. |
| Attachment upload | Backend multipart scoped to draft; server validated metadata/status/actual bytes; upload retry idempotency and authoritative reserved/current quotas. |
| Draft image read | Owner authorization; never expose cloud object keys/endpoints in client protocol. |
| Published image/download | Publication/ref context checked each new request before bytes; private cache policy and safe filename; no OSS/RustFS redirects or signatures. |
| ADMIN takedown | ADMIN-only action and required reason; audit; author cannot republish; no restore flow. |

`request<T>` already omits JSON Content-Type for FormData and adds CSRF (`http.ts:51–56`), so upload can reuse it. Successful decoding is text→JSON only (`:65–67`): add a shared fetch/identity/error pipeline and `requestBlob`/binary helper rather than bypassing401/cancellation with naked fetch.

Recheck identity after body decoding as well as response headers. Abort/revoke object URLs on replacement, unmount, logout/401 or A→B; old image/upload/download responses cannot apply to B. A bounded Blob download helper can check401 and surface network/download failure without removing the document. PDF/MD remain download actions only; no iframe, preview, imported MD text or frontend cloud SDK.

R2 quota/progress markup expresses total capacity, not transport upload percentage. Show actual uploading/ready/failed states from the backend, never timeout-based fake success. Client format/byte checks are advisory; service enforcement is authoritative. Precisely mirror 5,242,880 image /20,971,520 PDF /1,048,576 MD bytes,10 different IDs/52,428,800 total bytes, inclusive. Historical refs remain retained but do not inflate the current draft set; removing an editor ref does not delete its object.

### 7. Login, dialogs and focus regression requirements

- Keep the real `LoginPage` form behavior while allowing the R2 gated-shell card slot. Reuse title/card placement and real auth form; remove mock author/reader identity buttons and mock-login text.
- `LoginPage.tsx:24`: username autofocus and autocomplete username. `:25`: hidden default, eye/eye-off shared Icon, type button, correct accessible action label/pressed state, retained value; toggle cannot submit or alter focus unexpectedly.
- Existing `workbench.spec.ts:140–170` already verifies both directions, Space/Enter activation, password retention and zero login requests until submit. Run it unchanged for the ordinary login page and add the community gated target variant.
- `Dialog.tsx:8–17` supplies showModal, autofocus after opening, original-trigger restoration and busy Escape rejection. `DialogProvider.tsx:8–16,23–39` handles concurrent/duplicate dialogs/submits, failure input retention and initial cancel focus. Reuse these for withdraw, leave, profile editing, copy fallback and lightbox.
- R2 leave dialog has continue/discard/save-and-leave (`app.js:160`); implementing all three requires a feature form composed on the existing Dialog, not window.confirm or duplicate nested confirmations.
- Scope focus restoration across sidebar, bottom nav, type switch, unsaved guard and mobile lightbox close. No community page key should reset original records/report drafts or task filters.

### 8. Dependency state and executable verification commands

Verified installed package metadata:
- React/React DOM19.1.1; Playwright1.55.1; TypeScript5.9.2; Vite7.3.6.
- `frontend/node_modules` and `package-lock.json` exist. Node executable reports v25.2.1 (package engine requires ≥20.19.0).
- Windows Playwright cache contains Chromium1193 /headless-shell1193, ffmpeg1011 and winldd1007.
- `react-markdown` and `remark-gfm` are not installed. No dependency was added in research.

Actual frontend scripts from `package.json`: `npm run lint`, `npm run build` (tsc -b then vite), `npm run e2e` (playwright test).
Suggested implementation validation from cwd `frontend`:

```text
npm run lint
npm run build
npm run e2e -- e2e/community.spec.ts
npm run e2e -- e2e/workbench.spec.ts e2e/focus-alarm.spec.ts
```

`community.spec.ts` is a proposed test file to implement, not an existing suite. Current config requires explicit localhost/127.0.0.1 nondefault-port PostgreSQL with schema `d9_e2e`, explicit non6379 Redis port and supplied PostgreSQL credentials (`playwright.config.ts:3–10`). It starts an e2e-profile backend18080 and Vite15173, with timezone Asia/Shanghai (`:20–54`). These are verification prerequisites; this researcher did not inspect credential values, create/reset resources or run suites. RustFS integration must use dedicated synthetic buckets/resources coordinated by the parent/backend plan.

Existing useful regression anchors in `workbench.spec.ts`: eye140; cross-page alarm232/294; focus/drafts320; account/focus cleanup691; Shanghai date1063; retained state1090; drawers/focus1119; viewport1150; stale paging1439; stale save1496; report source/dirty1709; account2200/late response2261; activity2371/2396; dialog focus2426; realtime2491; true4012550/Toast2570;403/404/network2598; three-page records2647; report history2681.

### 9. UI acceptance strategy

1. Use synthetic author A, reader B and ADMIN C against isolated real backend/PostgreSQL/Redis/RustFS. Verify actual network, persistence, download hashes and roles; browser route fixtures can reach failure/layout states but must be labeled layout/fault-injection evidence.
2. Produce a R2 page/state correspondence sheet for all17 PNGs and source-defined states without their own PNG. Capture real UI at the same widths: desktop1440 (reference screenshots are full-page) and mobile390×844 (actual viewport frames), plus320,760,1024 and short-height reachability checks.
3. Compare the same content and scroll position. Since production has bounded inner scroll, collect viewport frames at defined community-scroll offsets or compose documentation from those frames; never crop/reposition the bottom nav to pretend it matched. Check sidebar/topbar/main offsets, card/aside ratios, title/actions, attachment order and mobile action/bottom-bar geometry, not raw CSS strings alone.
4. Screens01/11 feed;02/12 private source picker;03/13/14 editor/attachments;04 preview;05/15/16 detail/download;06/17 own posts;07 withdraw;08 profile;09 real-login gate;10 upload failure. Source-defined compose, empty, loading/retry, unavailable, type/dirty dialogs and lightbox also need coverage.
5. Verify default none selected, >two source pages, Shanghai date switch, selection reset, complete text/provenance privacy and task/focus dedup; only explicit selected data reaches the publication.
6. Verify latest unsaved preview/publish, manual-save-only privacy, lost response retry/idempotency,409 retention, F1→F2 draft/current split, withdrawal/ADMIN404 and replaced-attachment reads, no MD import/PDF preview, exact quota boundaries and upload failure recovery.
7. Assert no prototype tools/fixtures/fixed focus values/no interactive counters. Verify no user script/unsafe URL/raw HTML/external-image request and all cloud I/O stays through backend.
8. Run the existing five-workspace regression plus community round trips with dirty report/manual records/task filters, current real focus session and alarms, A→B delayed upload/image/save, real401 versus403/404/network and no passive activity renewal.
9. Check DOM geometry/no horizontal overflow, named focusable positive-height community scroll, final attachment/pager/actions reachable, mobile actions above fixed bottom nav, one visible preview confirmation, native modal keyboard/Escape/busy/focus restoration and username/password eye behavior.

## External references (official, checked 2026-10-03)

- [react-markdown latest stable release10.1.0](https://github.com/remarkjs/react-markdown/releases/tag/10.1.0); official latest-release endpoint and repository package.json agreed on10.1.0. [Package peer dependencies](https://github.com/remarkjs/react-markdown/blob/main/package.json) require React≥18, consistent with installed19.1.1.
- [Official README/API/security](https://github.com/remarkjs/react-markdown#readme): React element rendering, customizable components/URL transformation and skipHtml. Custom URL transforms/plugins can change safety; configuration must be tested.
- [remark-gfm release4.0.1](https://github.com/remarkjs/remark-gfm/releases/tag/4.0.1) was checked as an optional candidate. Parent selected **react-markdown10.1.0 only**, because R2's required headings, emphasis, lists, quotes and code are CommonMark.
- Production Markdown proposal: `skipHtml`, no `rehype-raw` or HTML insertion; shared renderer for preview/current body. Ordinary links only allowed http/https/safe same-origin targets, escaped labels and appropriate rel. Image rendering accepts only validated `attachment:<uuid>` references in the exact current metadata set; custom image component loads authenticated backend Blob. Reject arbitrary src/data/javascript/cloud URLs; never permit custom scheme through a blanket URL-transform bypass.

## Related specs

- `.trellis/workflow.md`: planning artifact/context gates and research persistence; no implementation in this research dispatch.
- `.trellis/spec/frontend/directory-structure.md:28–45,47–61`: feature/API boundaries, retained root, Toast, list/request ownership and paging.
- `.trellis/spec/frontend/identity-session.md:16–28,51–58`: auth/CSRF/epoch cleanup, explicit activity, password eye and isolated browser verification.
- `.trellis/spec/frontend/focus-page.md`: one account-root controller, server-authoritative timing, global sound/alarm lifecycle, effective daily record projection.
- `.trellis/spec/frontend/viewport-layout.md:5–24,40–60`: original-workspace bounded scroll and observed geometry/keyboard reachability.
- `.trellis/spec/frontend/dialogs.md:5–20`: one native shared modal path, cancellation/focus, destructive initial cancel, synchronous guards and failure retention.
- `.trellis/spec/guides/{code-reuse,cross-layer}-thinking-guide.md`: reuse shared mechanisms; explicitly track field/contracts through API/UI/storage boundaries.
- Handoff `requirements.md:57–87,97–139`, `HANDOFF.md:53–59`: exact AC03/04/07/08/09/10/13–22 and closed product decisions. Original README/review-notes “pending” language is historical and superseded by DEC16/17.

## Caveats / Not Found

- No production community modules, public-profile fields, attachment client APIs or Markdown library exist yet. New paths/components/endpoints above are implementation proposals, not current repository facts.
- Public nickname/biography are approved; source account API only provides login username. Final plan must choose an explicit separate public profile DTO/own-edit entry and safe unset display (never publish username). This is a local implementation decision, not a reason to reopen closed product scope.
- Parent must fix the final community route/endpoint names, effective source projection/composition contract, atomic publication of latest inspected input, and mobile workbench-switch entry. None needs an old-version compatibility layer.
- Exact R2 desktop sidebar is218px, not216/224; do not silently use the current shell value.
- Raw prototype stylesheet/global parser/model cannot be imported wholesale: global style collisions, body-scroll differences, in-memory auth, fake downloads and regex Markdown are concrete reasons for scoped JSX/API extraction.
- Existing prototype CHECKS/results “PASS” labels were not rerun and do not demonstrate real authorization, cloud ACL, transactions, quota concurrency or focus audio.
- All installed-module/browser/cache findings are read-only observations. Full build/E2E/physical audible-focus/cloud tests remain unexecuted in this planning research. Alibaba APIs/resources are intentionally outside current execution scope; real local RustFS test readiness is owned by the backend/environment research.
