# 单屏工作区技术设计

## Boundary and layout

Use a viewport-height shell (`100vh` fallback and `100dvh`) with no document-level vertical scrolling. On desktop, keep the sidebar at the left; on narrow screens, include the top navigation in the same bounded shell above the topbar. Make `.app-body` and `main#main-content` a height-constrained flex chain with `min-height: 0`.

Each active `RetainedView` fills the remaining main height. Each workspace page is a column with a fixed header/compact control region and bounded content. Use `overflow-y: auto` without `scrollbar-gutter: stable`, so a fitting five-row page has no visible scrollbar or reserved gutter. Style only actual scrollbars with a thin, subtle thumb; avoid horizontal scrolling. Label keyboard-scrollable regions accessibly.

Remove the desktop centered 1170px cap and reduce top/left content padding and vertical gaps. Keep a responsive, nonzero margin on narrow widths. This shifts content slightly left/up while using the available workspace width.

## Per-workspace regions

| Workspace | Fixed in viewport | Scrollable below |
| --- | --- | --- |
| Records | Page title/date navigation | AI/manual capture form and results, record list, pagination, footer. The short capture mode control may sit at the top of this scroll region; long form content never consumes the fixed-height budget. |
| Tasks | Page title/new task action, status and filters | Task list, pagination and footnote. If filters wrap beyond the available height, cap only the filter control block and scroll it internally. |
| Projects | Page title, create form, search/archive controls | Project rows and pagination. Cap the control block on short screens so the list remains usable. |
| Reports | Page title, daily/weekly switch, date, generate and history selection | History pagination/notices remain reachable. On desktop the report document and evidence/source panel independently scroll inside the report layout. On narrow screens both panels remain independently bounded and the containing area can scroll to reach them. Existing draft guards and retained editors stay mounted. |

The fixed controls are lightweight. Long input fields, AI results and report body editors belong in scrollable regions, per the user's decision. Do not treat the whole report body as the only scrollbar: the editor pane and fact-source pane have separate scroll ownership, with the source pagination reachable at its bottom. When a fixed control group wraps on a narrow/short viewport, it may scroll internally within a bounded height; the content region must not collapse to zero.

## Paged card footer and report controls

Every paged card owns a stable bottom footer for `Pagination`. Give the card a bounded flex-column layout (`min-height: 0`), place only its rows in an `overflow-y: auto` region, and keep the pager outside that region as `flex: none`. Apply this to record, task, and project lists; daily/weekly history pagination belongs at the bottom of each report card. In the evidence panel, keep the source heading visible, scroll source rows independently, and pin source pagination at the bottom of that panel. Maintain empty/error/loading states and ensure the last row is not covered by the pager. Short or narrow viewports may need a bounded containing scroll fallback, but the pager must remain reachable without scrolling through all rows.

Move the `ReportsPage` daily/weekly segmented control into the page header's right side, replacing the trust badge. Remove the separate switch row and its process hint. Retain dirty dots and mounted draft state. Compact the weekly report's heading/date/generate/version spacing through weekly-specific styles so the document/evidence panes receive more height; keep daily layout consistent without forcing a redesign.

Within a successful weekly report, split the document editor area into two cards: AI body on the left and manual additions on the right at desktop widths, stacked in reading order on narrow screens. Put `Save AI body` and `Copy combined report` at the top of the right card; keep `Save manual additions` associated with only the manual draft. If the AI save button is outside its form, explicitly associate it with the AI form or call the same submit handler, so clicking it cannot submit the manual draft. Preserve separate dirty flags, save concurrency guards, and the existing copy composition. The evidence/source panel remains independently reachable beside or below the two editor cards.

For both report types, put the history selector, generate button, and date input in one compact action group to the right of the report heading, in that DOM order. On wide screens the selector starts around the center of the content panel, with generation immediately to its right and date at the outer right. Let the group wrap at smaller widths without changing its keyboard order or hiding controls. Keep the report-type switch in the page header.

Size the successful weekly editor cards so long text scrolls inside each input; at a 1440×900 desktop viewport with status/dirty notices, the manual-save button at the right card bottom should be fully visible without scrolling the containing report document. On normal desktop heights the cards can grow to use spare space above history pagination. Keep both card headings and the right-card top actions visible. Preserve the independently scrollable source panel and pinned pagination.

## Pagination contract

All visible paged frontend lists request `size=5`: projects, records, tasks, daily/weekly history, and daily/weekly sources. Use one shared frontend constant and remove the page-size selector and changing-size callbacks/state from visible flows. Keep zero-based page numbering, server totals, next/previous behavior, stale-request cancellation, and report dirty guards. Global source numbering uses `sourcePage * 5 + index + 1` through the returned page size. Report generation continues to read every frozen source, independent of source-list pagination.

`PageResponse.offset` must accept size 5. Keep sizes 10/20/50 valid for existing API callers and leave the API default at 20 to avoid changing callers that omit `size`. No response shape or database query change is needed.

## Final interaction and spacing adjustments

Transient operation feedback is rendered through one shared viewport toast component with a close button and a five-second lifetime. It replaces feature-local success/info messages and recoverable operation errors. Persistent validation errors, unsaved warnings, and stored report failure details remain in context. The timer restarts for each new feedback event, even when the text repeats.

The task control panel uses one responsive grid: status segment and the three filter selects share the desktop row. The result count and optional reset remain accessible without forcing a fifth wide column. At smaller widths, the grid wraps rather than shrinking controls beyond usability.

Report history pagination follows the report content. On desktop successful reports, grow the editor and evidence panes so pagination approaches the card bottom and the edited-note remains immediately above it. On short/narrow viewports, use natural content height and the outer report scroll as a reachability fallback; the document and evidence panes retain bounded independent scrolling for long content.

For the evidence card, size the card and its source rows from the available report area so a short source page does not create nested overflow. Keep the source pager as a non-scrolling footer inside the card. When source rows exceed available space, only `.source-rows` scrolls; the outer report scroll is a reachability fallback for short/narrow viewports, not a second routine scrollbar beside the evidence pane.

The successful daily report editor places its existing save and copy actions above the body textarea, using the same compact card/action styling as the weekly editor. The actions retain their current handlers and operate on the daily draft. Bound the daily textarea so a long body scrolls inside the input instead of pushing the actions below the visible editor area.

The daily editor's three actions share the metadata row above the body card. The delete action remains a separate confirmation; Save submits the daily form from outside via an explicit form association, and Copy reads the current draft. Use a wrapping flex row so the metadata and actions remain accessible at narrow widths.

The successful daily and weekly report layout should use the remaining desktop report-card height above the history pager. Grow the document/evidence grid and the editable textarea cards with the available space, keeping the edited-note after the cards and close to the history pager. The source pager remains a fixed footer in the evidence card. At narrow widths, use bounded independent panes and the outer report scroll fallback instead of stretching stacked panels beyond reach. Test geometry and overflow at realistic browser sizes rather than relying on flex declarations alone.

## State and interaction

- Preserve existing feature data ownership, report draft guards, pagination navigation state and dialogs. Change the visible page size to 5 without replacing server pagination or remounting retained views.
- `RetainedView` keeps previously visited workspaces and report tabs mounted. Do not remount them with changing keys. On workspace navigation, reset the newly active content region to its top while retaining drafts and filter state; keep skip-link focus on `main` without causing document scrolling.
- Dialogs and drawers remain viewport overlays with their own scrolling; opening them must not rely on body scrolling.
- A scrollable list/editor region must allow keyboard and pointer users to reach its last interactive control. No content may be clipped by `overflow: hidden` without an adjacent reachable scroll container.
- A pinned pagination footer remains in normal card layout flow and does not overlay data rows. Keep its button disabled/loading behavior and report draft guard unchanged.

## Compatibility and trade-offs

- This changes the frontend layout and visible page size, plus backend page-size validation. The response shape, PageHelper query behavior, backend default size 20 and old 10/20/50 sizes remain compatible.
- More fixed controls reduce visible list height on short windows. Bound controls and allow their own scrolling only as a short-screen fallback. For reports, multiple local scrollbars are intentional because the document and evidence are separate reading tasks.
- Desktop and mobile browser chrome can alter available height, so `100dvh` governs the shell while `100vh` provides fallback. Verify at wide, narrow and short viewport sizes rather than relying only on CSS reasoning.

## Rollback

Revert the viewport layout wrappers/CSS and fixed frontend page-size changes together; then remove the added backend size-5 allowance if no other client uses it. There is no database migration or persisted state change.
