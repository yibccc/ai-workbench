# Frontend implementation and real browser evidence

Baseline HEAD: `1a7928c2b2fa15ce0d3148af187770d85aca96f7`; branch `codex/community-oss`; tests cover the current uncommitted frontend worktree. Frontend implementation and required automated gates are complete. The parent session owns the final cross-layer/visual acceptance audit and shared spec synchronization.

| Check | Result / scope |
| --- | --- |
| npm --prefix frontend install --save-exact react-markdown@10.1.0 | PASS, exit 0; lock synchronized; npm reported 0 vulnerabilities. |
| npm --prefix frontend run lint | PASS, final owned session 88080 exit 0, no warnings; includes all three E2E suites. |
| npm --prefix frontend run build | PASS, final owned session 80896 exit 0; TypeScript and Vite production build (515.09 KiB JS /157.25 KiB gzip). The conventional >500KiB advisory remains; no threshold or warning was hidden. |
| Community E2E discovery | PASS, checker discovered 12 cases; discovery alone was not counted as business acceptance. |
| node .trellis/tasks/10-03-community-ui/layout-probe.mjs | PASS FIXTURE_ONLY, 20 states, 0 page errors, 0 document overflow at source desktop/mobile plus 320/1024 widths. See fixture-layout/measurements.json and PNGs. |
| Real community/browser/RustFS integration — run 1 | RUN, exit 1: 9 passed / 3 failed, 12 cases (3 retries also failed). Owned session 42846 reached terminal; Playwright released 18080/15173. Isolated PG15432/db wbcommunityoss_test/d9_e2e, Redis16379, RustFS19000/private wbcommunityoss-e2e. Evidence retained in real-community-run-1. |
| Real community/browser/RustFS integration — run 2 | PASS, session 65760 exit 0, 12/12 first-run passes, 1.4m. Current-reader response/URL and corrected screenshot frames are asserted. Evidence: real-community-run-2. |
| Original workbench/focus-alarm — regression run 1 | RUN, session 35968 exit 1, 74 passed/2 failed, 7.2m; retained in regression-run-1. Mobile duplicate space row and an outdated automatic route-fallback assumption were resolved without lowering geometry, owner, permission or draft assertions. |
| Original two regression fixes | PASS, session 64480 exit 0, 2/2, 33.4s; original viewport ratio 0.1 and full account/403/private-draft checks remain. Evidence: regression-targeted. |
| Original full 76-case regression — run 2 | PASS process exit 0, session 24859, 6.3m: all 63 workbench cases first passed; 11 focus cases first passed and 2 initially flaky focus cases passed retry. Historical flaky attempts are retained in regression-run-2. |
| Focus no-retry diagnostic | Session 61353 exit 1, 12 passed/1 failed, 49.4s. The same initial-enable checkpoint race occurred in a third existing fixture. Evidence retained in focus-no-retry-run-1; not claimed as PASS. |
| Final community 12 + focus 13, retries disabled | PASS, session 59910 exit 0, 25/25 first passes, 1.6m; command `npm --prefix frontend run e2e -- e2e/focus-alarm.spec.ts e2e/community.spec.ts --retries=0`. Final focus fixtures wait for the initial owner claim and enabled action after checkpoint ACK before injecting later state. Product focus logic, all delayed resume/checkpoint barriers, 1500ms expiry, 20s renewal and loop-count assertions are unchanged. Evidence: final-community-focus-no-retry. |
| Final 17 reference screenshot files | PASS file/viewport audit, 17/17 PNG+JSON match desktop1440×1040/mobile390×844; offsets recorded below. Final 09 uses a real anonymous detail at the correct desktop size with username autofocus/hidden password/eye checks, followed by real login return. Parent visual comparison remains its responsibility. |
| Supplemental real R2 visual scenario | PASS, owned session52767 exit0, 1/1 with retries disabled, 26.3s. Actual API state contains three published types and one private UUID BLOG draft; private draft publicGET404, READY PNG/PDF/MD, current-reader publication, exact Chinese download filenames and PDF/MD SHA verified. Existing01/11 empty states are supplemented by populated feed desktop/mobile top+bottom captures;13/14/16 were regenerated in the correct saved/three-file states. |
| Current community test discovery shape | 13 registered cases: the original12 were run together in the final25-case no-retry run, and the additional visual case was run separately. No single new13-case full-suite result is claimed. |
| Parent AC22 visual review | PASS by root after actual inspection of the numbered screenshots plus populated feed/saved editor supplements. Authoritative root-owned report: visual-review.md. |

The fixture probe caught and fixed inherited notice alignment and editor absolute-control document overflow. Its first route glob also intercepted Vite source-module `/src/api/` URLs; it now matches only URL path prefix `/api/`. None of those failed fixture runs is reported as business acceptance.

Executed browser coverage includes three types, manual save/private draft and latest BLOG summary, source two-page/date/public-field defaults, late save/409 input retention, exact lost publish/upload response replay, first publication time/F1-F2 snapshots/download SHA, PNG/PDF/MD and upload/download errors/lightbox, ADMIN hide and no republish, real 401/allowed login return, A→B late private Blob/upload acknowledgements, mobile geometry/profile/workbench draft retention. HTTP response-error fixtures and browser/audio capability probes are explicitly labeled; they prove client behavior, not an actual backend outage or physical sound.

No paid model, cloud API, production database/Redis/bucket, user business data, destructive git or deployment operation was used by this frontend agent.

## First real-run findings

- Actual PASS: BLOG private save/latest summary publication/login-return/safe Markdown; source two-page/default fields/date guard/private composition; late save and 409 baseline; lost upload ACK with exact key/version/file and no extra refs/quota; F1/F2 snapshots/first publication time/real download SHA; real PNG/PDF and malformed upload/download retry/lightbox; ADMIN hide; type change/dirty guard/copy fallback. The HTTP-response-fixture case also passed and proves consumer error classification only.
- Lost-publish test waited on an article already present in preview, then read the old new-form hash before the successful retry completed. It now waits for the repeated publication HTTP 200 receipt, current-reader URL, and real public detail HTTP 200/revision 1 while still comparing the exact retry payload.
- Mobile login helper selected the hidden sidebar account button. It now asserts the actually visible account entry; no product visibility or layout was changed to satisfy the selector.
- Pending upload correctly prevented space navigation after a server-only logout. The second isolation phase now uses real UI logout while the ACK is held, then a different real login. The first phase still verifies a true server 401 and a late private Blob cannot create an object URL for the next account.
- The first 05-detail PNG was captured during publication preview, so it is retained as intermediate evidence, not a final detail comparison. Screenshot capture now waits for rendered/loading state, records viewport/document geometry and scrollTop in a JSON sidecar, and runs only after the required current-reader URL assertion.
- The original R2 navigation resets scrolling to the top. The stable shared main needed the same behavior on its community scroll owner; this missing one-line adaptation is fixed, and mobile publication now asserts reader scrollTop 0 after confirming from the bottom of the preview.

The initial screenshot folder is copied into real-community-run-1/screenshots before the corrected rerun. Main-session visual review must use the final regenerated screenshot set.

## Final screenshot positions and resource release

All final screenshot paths are under `validation/screenshots/`, with same-name JSON recording URL, kind, viewport, document dimensions and inner scrollTop. Desktop screenshots are viewport captures within the bounded shell; original desktop reference images used full-page capture beyond their1440×1040 viewport, so their variable image height is not a document-overflow target.

- 01–09, 11–13, 15 and 17: scrollTop 0. Final13 is an explicitly saved UUID BLOG draft, with its return link/title/summary visible at the top.
- 10 upload error desktop: scrollTop 241; real malformed PNG response and retained body.
- 14 mobile attachments: scrollTop 733; real READY PNG/PDF/MD rows, quota and final actions above the fixed navigation.
- 16 mobile downloads: scrollTop 503; real PDF and MD-only download rows and reader footer, both actually downloaded with exact names and SHA.
- Populated feed supplements: desktop top0/bottom327, mobile top0/bottom598. Three actual public types, real authenticated BLOG thumbnail and the desktop continue-private-draft card are verified.
- Saved editor desktop supplement: scrollTop0, actual UUID draft with three READY files.
- Source-only extra compose, preview/mobile confirmation, empty feed/source, image lightbox and explicitly named403/503 response fixtures have separate PNG/JSON files.

All UI-owned Playwright/Vite/backend handles are terminal. A TCP inspection after final supplemental session52767 found no18080/15173 listener. Maven and shared PG15432/Redis16379 were explicitly released directly to storage_check for its independent AC19 boundary test. Final work after that consisted only of lint/static files; no live UI server remains. Final post-visual lint exit0; production files did not change after the successful final build80896.

Raw trace ZIP/video may contain synthetic-session login traffic/cookies and must not enter a task Git archive. Root preserved original files/SHA while moving historical raw diagnostics to ignored `.local-runtime/community-oss-browser-artifacts/<task-relative-path>`. Supplemental visual run1 failure and run2 success originals are in ignored `visual-run-1`/`visual-run-2-pass` beneath that root. Task evidence retains PNGs, geometry JSON, error context and sanitized summaries; no raw trace/video was copied back.

The supplemental visual run1 failed only because the driver inspected the intentionally hidden desktop draft aside while still at the390px attachment viewport. It now explicitly returns to1440 before checking the desktop link; no product/layout was changed. That failure remains preserved in its ignored diagnostic folder.

Remaining limits: no Alibaba API/cloud resource/adapter was used; no physical device-audibility or device-sleep claim is made. Root visual acceptance has passed; the parent still owns final repository-wide review and spec/delivery synchronization. No commit/push/deployment was authorized or performed by this agent.

## Commit-preamble CSS newline verification

After the user authorized local business commits and task/journal completion, the root retained exclusive Git ownership. Its staged check found one extra trailing blank line in the extracted community CSS and normalized the file to one final LF; the root confirmed all CSS rule bytes were unchanged. This implementer performed only the requested independent frontend checks: `npm --prefix frontend run lint` (owned session79040, terminal exit0) and `npm --prefix frontend run build` (owned session59573, terminal exit0). The build still emitted `index-BvajvXdw.css` and `index-CYKUGOfK.js`, with the existing515.09KiB JS advisory unchanged.

Earlier browser/visual PASS and historical failed/flaky evidence are preserved above. No browser E2E, Maven, product/test edit, Git write or root-audit change was made for this newline-only step; only this validation appendix was added. Both validation processes are terminal.
