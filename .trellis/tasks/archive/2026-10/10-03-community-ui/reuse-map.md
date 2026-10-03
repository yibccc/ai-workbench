# R2 source → React → current evidence

The immutable R2 `research/handoff/r3/assets/prototype-r2/src/app.js` and `styles.css` are the source. No input HTML, JS, build script, fixtures or smoke script was executed. CSS was transformed statically by the task-local `extract-r2-css.mjs` using PostCSS; production imports the resulting `frontend/src/features/community/community.css` directly and has no dependency on task directories.

| Source region | React owner / preserved regions |
| --- | --- |
| app.js 58–63 shell | AppShell common stable main and CommunityChrome; brand/space switch/nav/guide/account slot/topbar/bottom nav; unique account root and original five RetainedViews stay mounted. |
| 64–67 shared heading/stepper/tabs/card | community/ui and CommunityPages; author/badge/excerpt/checklist/attachment footer grouping; icons converted from the original SVG elements. |
| 68–76 feed/empty | CommunityFeed; three composer actions, type filter, draft side card, member visibility guidance, page footer and retry/empty layouts. |
| 77 compose | CommunityWorkspace; independent three-row type choice page. |
| 78–81 sources | SourcePicker; private source card/date/five-row pages/selected summary/focus opt-in/server composition. Real Material fields replace fixture-only title/minutes. |
| 82–91 attachments/editor/settings | PublishingEditor; type tabs/title/blog summary/Markdown toolbar/body/status/files/dropzone/quota/side settings/mobile actions. Save and publication remain separate. |
| 93,110–114 article/preview | PublicationDocument and AttachmentMedia; safe Markdown, gallery/lightbox, PDF/MD download rows, author/TOC aside and single visible confirmation panel. |
| 115 mine | MyPublications; status tabs, private change indicator, current-publication/edit/withdraw rows and save/publication guidance. HIDDEN has no republish capability. |
| 116 author | CommunityFeed author mode; public nickname/bio hero and visible feed only. Own profile edit lives in MyPublications. |
| 117–118 gate/unavailable | real LoginPage gated slot under AppShell and Unavailable; real password/eye/autofocus, allowed target return, no demo identity. |
| 160–166 dialogs | shared native Dialog, optional community theme, leave/type-change/image/copy/profile forms. Inert background, initial focus, cancel/error retention and focus restoration remain shared. |

All raw prototype innerHTML, regex Markdown, in-memory auth, simulated writes, fake timers/files, fixtures, generated sample imagery, demo tools/identity selectors and prototype footers are omitted. Scoped CSS preserves 218/192px sidebar, 68/60px topbar, 1320px main, 270px column, 1110px reading grouping and original responsive breakpoints. The named/focusable `.community-scroll` supplies bounded positive-height scrolling; `position:relative` prevents clipped absolute screen-reader file controls escaping its scroll containment.

Original desktop screenshots used viewport 1440×1040 with full-page capture, producing variable image heights; mobile used 390×844 viewport capture. Formal screenshots use those viewport sizes and record relevant inner scroll positions. Extra compose/preview/empty/error states are separate, as they have source layout but no unique original PNG.

| Original PNG | Formal screenshot scene in community.spec.ts |
| --- | --- |
| 01 community desktop | original numbered frame is authenticated empty feed; populated-feed-desktop/top+bottom show three real published types and a continuing private draft |
| 02 source selection desktop | two-page selection and default public fields |
| 03 editor desktop | BLOG title/summary/body, local new-form state |
| 04 publish preview desktop | latest unsaved summary and exact publication input |
| 05 article desktop | current published projection |
| 06 my posts desktop | saved/publication management |
| 07 withdraw confirm desktop | shared native withdrawal modal |
| 08 author profile desktop | public profile/visible articles |
| 09 login gate desktop | anonymous real detail login-return |
| 10 upload error desktop | real malformed-image upload response and retained body |
| 11 community mobile | original numbered empty feed plus populated-feed-mobile/top+bottom,390×844 and four-item bottom nav |
| 12 source selection mobile | same cross-page source selection |
| 13 editor mobile | actual explicitly saved UUID BLOG draft at top0, return link/title/summary/editor and reachable save/preview; desktop supplement also captured |
| 14 attachments mobile | real PNG/PDF/MD READY rows, dropzone and quota, lower region at scroll733 |
| 15 article mobile | reader body at mobile viewport |
| 16 downloads mobile | actual PDF+MD-only download rows at scroll503, both names/raw bytes verified |
| 17 my posts mobile | status rows and primary actions |

The earlier fixture-only probe `layout-probe.mjs` intercepted only business `/api/` URLs and recorded20 layouts in `validation/fixture-layout/measurements.json`. This evidence proves layout/error reachability only. Final real scenes were subsequently executed in the12-case community suite against the isolated backend/PostgreSQL/Redis/private RustFS, with current-reader URL and HTTP200 assertions before reader captures. The final combined25-case community/focus run disabled retries and first passed all cases.

Formal screenshots default to `frontend/test-results/community-evidence` for long-term test use; this run explicitly set `COMMUNITY_EVIDENCE_DIR` to the task's `validation/screenshots`. All17 numbered files now match reference viewport widths/heights: desktop1440×1040/mobile390×844. Desktop reference images additionally used full-page capture; the real bounded scroll owner is recorded in JSON sidecars rather than allowing the document to grow. Final09-login uses a new real anonymous reader context explicitly set to1440×1040, with username focus/hidden-password/eye checks. Positions are listed in validation.md;14/16 show actual lower attachment/download regions and mobile actions. Root owns the final image-by-image visual acceptance.

The original five workspaces retain their layout/scroll owners. Their mobile duplicate workbench/community switch row is hidden because the existing real account menu supplies the community entry; this restores original navigation height and passed the unchanged320×520 viewport/pager/row geometry assertions. Desktop switch and community R2 mobile four-entry navigation remain available. Community route changes reset only their own named scroll owner to0, matching original R2 navigation behavior; no workspace draft/filter/controller key is changed.

Supplemental actual visual case52767 passed1/1 with retries disabled after the initial12-case delivery gate, using the same isolated real backend/PostgreSQL/Redis/private RustFS. It captures populated feed desktop/mobile (top and bottom), a private saved UUID editor, three actual file types and two actual download rows. It is the13th registered community case and a separately executed visual test, not a claim of another full13-case run. Root subsequently inspected these actual scenes and recorded AC22 visual PASS in the root-owned visual-review.md. All raw traces/videos remain under ignored local diagnostic directories, outside task archive evidence.
