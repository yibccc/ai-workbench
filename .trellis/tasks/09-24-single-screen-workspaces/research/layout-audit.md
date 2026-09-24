# Layout audit

## Current structure

- `frontend/src/components/layout/AppShell.tsx` owns sidebar, topbar and `main#main-content`. `frontend/src/App.tsx` keeps each visited workspace mounted through `RetainedView` and currently calls `window.scrollTo({ top: 0 })` on navigation.
- `frontend/src/styles.css`: `.app-shell` uses `min-height: 100vh`; the fixed desktop sidebar scrolls independently; `.app-body` and `.main-content` have no viewport height constraint. At ≤760px the sidebar becomes a normal block above the topbar. The document therefore grows with page content.
- `RecordsPage` contains header/date, a potentially tall AI/manual capture panel, record list, and footer. `TasksPanel` contains header/new action, status/filter controls, list, pagination, and footnote. `ProjectsPanel` contains header, create form, search control, list, and pagination. `ReportsPage` contains header/type switch and retained daily/weekly panels, whose date/generate/history controls precede a long editor and source list.
- Native dialogs/drawers already use `max-height`/`overflow-y: auto`; keep their independent viewport behavior.

## Constraints and implications

- `RetainedView` wrappers must participate in the height chain (`min-height: 0`) so hidden/visited pages cannot force document scrolling. The active workspace should own its scroll state and remain mounted across navigation.
- A flex/grid child with `overflow-y: auto` needs every ancestor in the height chain constrained and `min-height: 0`; simply adding `overflow: hidden` to `body` can hide content.
- Narrow-screen nav height and form wrapping reduce remaining space. Fixed controls need a bounded height and their own overflow only when necessary; the primary content scroll area must retain a useful minimum height.
- Reports have two retained editor trees; layout must apply to the active one without resetting drafts or replacing existing report selection logic.
- Existing responsive browser test (`frontend/e2e/workbench.spec.ts`, around line 93) checks horizontal overflow at 320/390/768/1024/1440 widths. Extend it with vertical viewport and content reachability assertions rather than testing CSS declarations alone.

## Relevant guidance

- `.trellis/spec/frontend/directory-structure.md`: `AppShell` owns navigation; retained views preserve drafts and realtime work; avoid changing keys to refresh content.
- `.trellis/spec/frontend/dialogs.md`: drawers and dialogs must preserve focus, cancellation, and narrow-screen usability.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: check shared layout patterns before duplicating per-page rules.
