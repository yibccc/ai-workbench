# UI check — static review and fixture verification

**Parent integration closure, 2026-10-03:** the verification gaps below describe the earlier static-review checkpoint. They are now closed by real community12, original76 (two historical flaky attempts disclosed), latest community12+focus13 no-retry25, and the additional real R2 visual case52767/1PASS. Parent performed the actual17-reference/source-state comparison in `visual-review.md`; final executable specs and delivery are synchronized. Current scope has no known unresolved frontend implementation defect. Final lint/build and exact run evidence are in `validation.md`; fixtures remain explicitly fixture-only and user physical sound/continued-use acceptance is not claimed.

Review date: 2026-10-03 (Asia/Shanghai). Active task: `10-03-community-ui`.
This is a frontend review checkpoint, not full community acceptance. The parent design is the chosen contract; research candidate endpoints/schema/old routes were not adopted.

## Findings (fixed)

- **PublishingEditor.tsx — upload acknowledgement loss/idempotency.** Retry previously always generated a new key and used the current aggregate version. Each entry now retains its original File, post ID, expectedVersion and requestId. UNKNOWN/ACTIVE retries retain the complete original payload. ACTIVE exposes a status-query action; the backend's existing replay path avoids another object Put/reservation. A new key is used only after a documented durable FAILED response or exact returned attachment state proves failure. A VERSION_CONFLICT that created no reservation permits an explicit retry with the refreshed version and the same key after the user reads current state.
- **PublishingEditor.tsx — stale upload receipts.** An old READY receipt no longer grants its historical result version as a current write token. Owner GET supplies current version, draft refs and exact attachment UUID/state; filename/size matching is not used. A file removed from the current draft is not silently rebound. A successful READY receipt followed by a failed owner GET retries the owner read without another upload. Creation/upload errors keep actionable file rows.
- **PublishingEditor.tsx — acknowledged snapshot and conflicts.** Owner rereads update the saved snapshot from the actual server draft while preserving local title/summary/body/attachment selection. Dirty state therefore reflects the latest server content. Saves/publications with a 409 require an explicit version read and subsequent user retry; no automatic overwrite. Upload/recovery/publication rereads use the same acknowledgement path. A successful older publication result cannot regress current state. Missing/non-READY selected IDs block preview confirmation.
- **App.tsx — ADMIN menu skipped the editor leave guard.** User-management navigation now changes the current hash and uses the existing hashchange guard instead of directly replacing page state. Pending editor operations also prevent leaving their write context. Original five-workspace dirty behavior remains retained.
- **PublishingEditor.tsx — accepted uppercase UUIDs loaded forever.** The existing parser accepts either UUID case, whereas JSON serializes lowercase. Resource matching is now case-insensitive inside the editor and its same-post preview guard; the route contract itself is unchanged.
- **CommunityPages.tsx — asynchronously loaded profile form lacked initial input focus.** The nickname field uses React autofocus when its data-backed input mounts, while native Dialog still restores the original trigger on close.
- **api/http.ts — stable safe error metadata.** ApiError keeps status and validated documented code/currentVersion fields through the existing Cookie/CSRF/cancellation/identity pipeline. No alternate fetch/authorization path or arbitrary server extras are exposed.
- **e2e/community.spec.ts — missing regressions.** Added a real lost-upload-ACK scenario with two identical filenames/content, exact multipart file/key/version comparison, no extra attachment/ref/quota, a server change after upload, preserved local body and a subsequent current-version save. Strengthened the stale-clean snapshot/explicit retry, ADMIN menu guard, uppercase UUID and profile focus checks.

## Reviewed code paths

Read the saved complete hook output and all check.jsonl spec/research paths, including the full original backend research after its injected truncation; parent/child PRD/design/implement, reuse-map, implementation-notes and validation were reviewed. Authoritative runtime DTOs/attachment reserve/replay/failure paths were read directly.

- R2 source-to-React/CSS map covers 17 source PNGs plus source-only states. Scoped reset/tokens and explicit community Dialog classes preserve the original 218/192 sidebar, 68/60 topbar, main/column/breakpoint layout. Named bounded community scrolling and fixed mobile navigation are implemented. Existing 20-state layout evidence is FIXTURE_ONLY and remains so; it was not rerun without new layout changes.
- Account ID is the only Workspace key. The same AppShell main DOM path retains the original five RetainedViews and sole focus controller/audio. Reader reentry mounts/fetches a fresh current projection, and 404/ADMIN hide remove the prior document. Strict parsed hashes and authenticated startup avoid loading protected data before `/me`.
- Markdown uses pinned react-markdown 10.1.0/skipHtml, no raw HTML/GFM, http(s)-only links and an exact current-UUID image allowlist. External image sources are not fetched. Internal TOC buttons do not overwrite the hash. PDF/MD are Blob downloads, not preview/import.
- Blob reads share epoch/cancellation/401 classification; image/download object URLs revoke on cleanup, and protected account-tree unmount cancels old state. Source IDs persist across pages, date changes guard/reset selection, default CONTENT selection and false focus opt-in remain explicit. BLOG summary participates in all draft/publication paths and dirty snapshots.
- Production modules contain no prototype fixtures/demo controls/innerHTML authorization/fixed focus, cloud URLs/SDK or native application prompt/confirm. Vite child environment is allowlisted and excludes backend/storage credentials. Evidence output defaults to frontend/test-results rather than requiring task artifacts.

## Findings (not fixed)

- **Real business/browser acceptance remains NOT_RUN at this checkpoint.** The parent reserved backend/Redis/Maven during storage work; this reviewer respected that ownership and did not start Maven, mutate a database, or run real E2E. The released 12-case suite must now run against isolated real backend/PostgreSQL/Redis/private RustFS, followed by existing workbench/focus-alarm regression. These are verification gaps, not fixture-proven business PASS.
- **AC-22 formal screenshots remain pending.** Existing 20 fixture layout states and this review's 9 transport fixtures cannot establish all 17 real-source screenshot comparisons or full business states. Complete real same-viewport/scroll-position evidence and source-only states in the formal run.
- **Final spec synchronization belongs to parent integration.** Record the chosen publication/attachment/space contracts and these retry/saved-snapshot invariants in project specs after real integration; this review did not rewrite shared specs owned by the parent.
- **Build advisory retained:** the production JS bundle is approximately 515 KiB before compression and Vite emits its usual >500 KiB advisory. Lint/type/build pass; no threshold/warning suppression or unrelated code splitting was introduced.

No additional unresolved frontend implementation defect was identified within the reviewed static paths after the fixes. This statement does not replace the pending real acceptance gates.

## Verification

| Check | Result / evidence |
| --- | --- |
| `npm --prefix frontend run lint` | PASS, final exit 0 after all product/test changes. |
| `npm --prefix frontend run build` | PASS, exit 0 after final product changes; TypeScript + Vite production build. Tests were subsequently strengthened without further product changes. |
| E2E discovery with isolated E2E env, `npm --prefix frontend run e2e -- e2e/community.spec.ts --list` | PASS, exit 0, 12 cases; discovery only, no backend launch/business execution. |
| `node .trellis/tasks/10-03-community-ui/check-upload-probe.mjs` | PASS FIXTURE_ONLY, final exit 0, 9 actual React/transport assertions, 7 upload requests, 4 draft writes, 0 page errors. Evidence: check-upload-probe.json. |
| Earlier fixture probe attempts | First run reached the 6 retry/snapshot assertions, then timed out because the test used button instead of the real menuitem role. Selector was corrected; final 7 then 9 assertion runs passed. No product change was made to satisfy the incorrect selector. |
| Real community + old workbench/focus E2E | NOT_RUN here; parent integration window required. |

Owned Vite handle: exec session 81905, `node e2e/start-vite.mjs`, listened at 15173 solely during this fixture review. It was terminated with Ctrl-C and the follow-up TCP inspection found no listener. Probe browsers closed in finally. No owned background server remains.

**Ownership release:** frontend product/test files are released to the parent/UI implementer. This reviewer will make no further frontend changes until an explicit follow-up. No backend/pom/compose/root scripts/.local-runtime/shared spec/user SOP writes, git commit/push, cloud API or deployment were performed.
