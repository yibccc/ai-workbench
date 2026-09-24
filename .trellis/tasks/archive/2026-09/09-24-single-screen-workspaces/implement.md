# Implementation plan

1. Read frontend guidelines and this task's requirements/design. Inspect existing responsive styles and page markup before changing selectors.
2. Constrain the shared shell and retained view height chain to the visible viewport. Replace page-level scrolling with active workspace content scrolling, preserve focus behavior and existing mounted view state.
3. Restructure only the four page layouts needed to separate fixed title/compact controls from scrollable content. Keep report daily/weekly behavior and dialogs intact; add bounded-control fallback for short viewports.
4. Update responsive CSS at 760px/480px and short viewport sizes. Check that scrollable regions have `min-height: 0`, accessible labels/focus where appropriate, and no horizontal overflow.
5. Extend backend `PageResponse.offset` to allow size 5 while keeping 10/20/50 valid. Add or update pagination integration assertions for size 5 across routes and source ordering/counts.
6. Set one shared frontend page size of 5 for records, tasks, projects, report history, and report sources. Remove page-size UI and changing-size state/callbacks; update browser pagination cases to assert five real items and correct page parameters.
7. Move the desktop main content slightly left/up and reduce excess gaps. Remove the permanently reserved scrollbar gutter; style overflow-only scrollbars. Split the report document and evidence/source areas into independently bounded scroll containers on desktop and reachable bounded panels on mobile.
8. Extend Playwright coverage for all four pages at desktop/mobile/short heights: no document overflow; fixed controls remain visible; five rows fit without unnecessary scrollbar when possible; independent report panes and source pagination are reachable. Check dialog/drawer and dirty-draft behavior.
9. Run `npm run lint` and `npm run build` in `frontend`, backend focused pagination integration test, and relevant Playwright cases. Review screenshots or browser geometry for regressions.
10. Perform full-scope Trellis quality check against frontend/backend specs and PRD. Update the viewport and pagination specs to match the final contracts.
11. Move pagination to the bottom of every paged card. Separate each card's row scroll area from its fixed footer, including report history and the report evidence list; verify the last row remains reachable and button state/dirty guards still work.
12. Move report type switching into the report page header, remove the former badge and process hint, and use weekly-specific spacing to enlarge the visible editor/evidence region.
13. Re-run frontend lint/build and focused browser geometry/interaction tests for pinned pagers, report tabs, weekly space, narrow/short viewports, and draft retention. Review screenshots. Then stop and restart the managed local services so the user can inspect `http://127.0.0.1:5173`.
14. Split successful weekly report editing into AI-body and manual-addition cards. Place the AI-save and combined-copy actions at the top of the manual card while keeping their original handlers; stack cards on narrow screens and preserve source-panel reachability.
15. Add focused browser assertions for desktop left/right geometry, mobile stacking, action placement, AI/manual save separation, combined copy, and retained drafts. Run frontend lint/build and relevant Playwright flows; review a screenshot.
16. Rework daily and weekly report controls into a shared visual order of history selection, generate action, and date navigation beside the report heading. Preserve the existing dirty-discard guards and narrow-screen behavior.
17. Shorten the successful weekly AI/manual card heights while making long text scroll inside the textareas. Verify both cards, their actions, source evidence, and the full text remain reachable at desktop and mobile widths; compare a browser screenshot with the user's reference.
18. Create a shared five-second dismissible toast for transient operation feedback and apply it across the workspaces. Preserve persistent validation, draft, and report-state messages. Verify timer restart and manual dismissal.
19. Reflow the task status segment with the three filters on wide screens, with accessible wrapping on narrow screens.
20. Let report history pagination follow the editor note instead of filling the remaining page height; verify report body and source scrolling at normal and short viewport heights.
21. Run frontend lint/build and focused browser checks for the toast, task controls, and report pagination position. Review the relevant desktop and narrow layouts.
22. Fix report evidence sizing so a short source page has no double scrollbar and its source pager is visible at the card bottom. Add browser geometry/overflow assertions for a short source page and a long source page, including narrow/short viewport reachability.
23. Move daily save/copy actions above the daily body textarea in a compact editor card matching weekly actions. Verify long draft scrolling, save/copy semantics, and narrow-screen reachability.
24. Group daily Delete, Save, and Copy to the right of the generated-version/source metadata. Explicitly connect the external Save button to the daily form; remove the old action block and float styling. Verify desktop same-row geometry, narrow wrapping, and all three handlers.
25. Let successful daily and weekly editor/evidence panes consume the spare desktop card height above the history pager. Grow the textareas within their cards while preserving internal scrolling and editor note proximity to the pager. Check source-list pager pinning, overflow ownership, narrow/short viewport access, and a screenshot/geometry regression for the former bottom whitespace.

## Risk and rollback checkpoints

- After shell CSS: verify the active view still has a nonzero content height before restructuring pages; otherwise revert the constraint and fix the height chain.
- After each page: verify no content was hidden by clipping and the last action remains reachable.
- Before final review: verify hidden retained views do not affect document dimensions or discard report drafts.
