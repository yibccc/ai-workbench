# Viewport Layout Contract

## Scope

The five workspaces (`records`, `tasks`, `focus`, `reports`, `projects`) occupy one visible browser viewport. The document must not grow vertically with workspace content. Navigation, the topbar, each page title, and compact page controls remain in the viewport. Long forms, lists, report bodies, and evidence scroll in bounded content regions. Desktop main content uses the available width with modest top/side padding; avoid restoring the former centered 1170px cap.

## Height chain

`styles.css` sets `.app-shell` to `100dvh` (`100vh` fallback) and clips document-level overflow. A scrollable workspace needs every ancestor in its height chain to have a bounded height and `min-height: 0`:

```text
.app-shell
  .app-body                         flex column, height 100%, min-height 0
    .topbar                         flex: none
    .main-content                   flex: 1, min-height 0, overflow hidden
      .retained-view                flex: 1, min-height 0
        .page                       flex column, height 100%, min-height 0
          fixed page header/controls
          .workspace-scroll         flex: 1, min-height 0, overflow-y auto
```

Reports use `.report-scroll` inside the active daily/weekly panel, with `.report-controls` above it. The report document and source rows each own `overflow-y: auto` inside `.report-layout`; the outer `.report-scroll` remains a reachability fallback on narrow screens. `ReportsPage` and its retained daily/weekly wrappers also participate in the height chain. On narrow screens the sidebar becomes a bounded top navigation block above `.app-body`; it must not increase document height.

Setting `overflow: hidden` on the shell without this complete height chain hides content. Do not add a new page child that can expand the shell without giving it a reachable scroll owner.

## Fixed and scrollable regions

- Records: title/date controls stay fixed; capture input/results and the record card use `.workspace-scroll`. Inside the record card, `.record-rows` scrolls while its pagination stays at the bottom.
- Tasks: title/new action and `.task-controls` stay fixed; on wide screens the status segment and project/priority/due filters share one row, then wrap at narrower widths. `.task-list` scrolls inside `.task-panel` and its pagination stays at the card bottom. The footnote remains reachable below the card.
- Projects: title and `.project-controls` (create and search) stay fixed; `.project-rows` scrolls inside `.projects` and its pagination stays at the card bottom.
- Reports: the daily/weekly switch sits at the page header's right side; the former trust badge and process hint are absent. `.report-controls` (date, generate, version) stay fixed. On normal desktop heights successful reports fill spare height with the editor/evidence panes, keeping the editor note close to history pagination and the pager near the card bottom. Short/narrow viewports use natural content height and outer scroll fallback. The editor/actions and evidence/source rows scroll independently in `.report-document` and `.source-rows`; the source pagination sits at the evidence card bottom.
- The daily and weekly `.report-heading-row` holds one `.report-toolbar` after the heading. Its keyboard/DOM order is history version, generate action, then date input. On wide screens the selector begins near the report card center and the date is outermost; at narrow widths the toolbar wraps in the same order. Moving these controls must preserve history dirty-discard and date-change guards.
- A successful weekly report uses `.weekly-editor-grid` inside `.report-document`: the AI body form is the left card and manual additions are the right card on desktop, stacked in that order at widths up to 760px. The right card's top `Save AI body` button uses `form="weekly-ai-form"` to submit the left form; `Copy combined report` reads both drafts, while `Save manual additions` keeps its separate request and dirty state. Do not merge these save paths when changing the layout.
- A successful daily report places Delete, Save, and Copy to the right of the generated-version/source metadata in `.daily-report-meta-row`; the row wraps at narrow widths. Delete retains its confirmation, the external Save button uses `form="daily-report-form"` to submit the daily form, and Copy reads the current draft. `.daily-editor-card` holds the body textarea below the action row. Bound the textarea height so long content scrolls inside it; keep all actions visible on entry and accessible on narrow screens.
- Bound weekly AI/manual textarea heights on short and narrow viewports; on normal desktop heights a successful report may grow both cards and their textareas to use spare space. Long report text scrolls inside each textarea; no saved text is truncated. At 1440×900 with status and unsaved notices visible, both the right-card top actions and `Save manual additions` should fit fully inside the visible report document without an outer scroll.
- Every paged card keeps pagination in normal flex-column layout after its data scroll region (`flex: none`), not as an overlay. The final row must remain visible and clickable above the footer. For successful reports on normal desktop heights, `.report-scroll-filled` grows `.report-scroll`, `.report-layout`, and the editor cards together; growing only an outer ancestor creates a gap between the edited note and history pager. On very short screens the containing workspace may still scroll to reach the card.
- For the report evidence card, use a `button` with `aria-expanded` above `.source-rows` and keep `Pagination` below it. Native `details` did not yield a reliable flex height for this three-part layout in Chromium and could clip source rows; verify the expanded and collapsed states after changing this structure.
- Let desktop `.report-layout` shrink with available content height even when it grows to fill spare space. A `flex: none` report layout can overflow its `.report-scroll` by only a few pixels yet hide the source pager and create an extra scrollbar. Keep short source entries compact enough that a three-item page fits at normal desktop heights without truncating text or reducing its font size; long entries scroll only within `.source-rows`, while the source pager remains a fixed footer inside the evidence card.
- A fixed control block can overflow internally on a short viewport. Bound its height so the main scroll region retains positive height. Long input or editor content belongs in the main scroll region, not in the fixed controls.
- Give each scrollable region a descriptive `aria-label`, `role="region"`, and keyboard focus through `tabIndex={0}`. Use `overflow-y: auto` and thin, low-contrast scrollbars. Do not reserve a permanent scrollbar gutter: a five-item list that fits must not show a bar or empty track. Prevent horizontal overflow.

`RetainedView` remains mounted after first visit. Never remount a workspace or report tab merely to reset scroll position; doing so loses drafts and live work. `App.tsx` resets the active workspace's content scroll position after navigation while retaining component state.

Dialogs and editor drawers remain viewport overlays with their own vertical scrolling. Focus restoration and dirty-draft guards continue to follow [Shared Dialogs](./dialogs.md).

## Verification

For desktop, narrow, and short viewports, browser tests should assert observable geometry and reachability:

1. `document.documentElement.scrollHeight <= window.innerHeight + 1` and no horizontal document overflow.
2. Each active scroll region has a positive client height; after scrolling it to the end, the final list/footer/editor/source pagination action can enter the viewport. If content fits, `scrollHeight <= clientHeight + 1` and no scrollbar gutter is reserved.
3. Scrolling rows within a paged card leaves its pagination at the card bottom and does not cover the final row. The report document and source rows scroll independently; scrolling either pane must leave the other's `scrollTop` unchanged. On short screens, the final control in any bounded controls region is also reachable.
4. Navigation preserves workspace/report state; dialogs and drawers still fit and restore focus. On weekly reports, verify left/right desktop geometry, mobile stacking, bounded textareas with long text reachable by scrolling, AI save through the external form button, independent manual save, and combined copy output.
5. At a wide desktop width, compare the vertical positions of the status segment and all three task filters, not only their left-to-right order. Compare the bottom of the edited report note with the top of the history pager in both daily and weekly reports, and the pager bottom with the report-card bottom; stretching only the outer `.report-scroll` can leave a gap even when the pager remains reachable.
6. For daily and weekly evidence, compare `scrollHeight` with `clientHeight` for both `.report-scroll` and `.source-rows` on a three-source page at 1440×900 and 1024×900. Neither should overflow, and the evidence pager must be inside the card viewport. With long source text, `.source-rows` should overflow while its pager stays in place; in a short narrow viewport the final source and pager can be reached separately through the outer fallback scroll.
7. For a long daily draft, verify Save and Copy precede the textarea and are visible before scrolling. After editing, assert that Save persists the draft, Copy returns the current draft, and the textarea itself scrolls without causing horizontal document overflow on a narrow viewport.
8. For daily reports, compare the metadata and all three action button rectangles at desktop width: Delete, Save, and Copy sit to the right on the same row. Verify Save's `form` association, delete confirmation, and action reachability after narrow-screen wrapping. Test independent document scrolling at a viewport height that actually overflows; a shorter textarea may make a formerly overflowing test viewport fit.
9. For successful daily and weekly reports at 1440×900 and taller desktop sizes, measure the editor/evidence card heights, their textareas, the edited-note gap to history pagination, and the history pager's distance from the report-card bottom. Check the manual weekly textarea as well as the AI textarea. At short and narrow sizes, verify the fallback scroll reaches each control and both pagers.

Do not validate the contract by checking CSS declarations alone. A missing `min-height: 0` in any ancestor can silently clip the last action even when the stylesheet appears correct.
