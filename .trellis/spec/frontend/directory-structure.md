# Frontend Directory Structure

## Organization

Use React feature modules plus shared infrastructure. Frontend components do not mirror Java service interfaces: split them according to UI and state ownership.

```text
src/
├── App.tsx                 Page composition and cross-feature coordination
├── main.tsx                Entry point
├── styles.css              Shared styles
├── features/
│   ├── capture/            AI input and generated results
│   ├── projects/           Project management and project picker
│   ├── records/            Record-list presentation
│   ├── tasks/              Task form, filters and list
│   └── reports/            Daily/weekly panels and shared report hooks
├── components/             Pagination and collapsible sections
├── api/                    Shared HTTP client; per-feature requests and types
├── hooks/                  Shared realtime lifecycle
└── utils/                  Shanghai date conversion
```

## Boundaries

- Import feature APIs from their module under `api`, not a growing root api.ts.
- Shared components receive state/callbacks rather than fetching unrelated business entities.
- `useReportHistory` owns history paging/metadata. `useReportSources` owns source paging, size and stale-request cancellation.
- App composes feature components and coordinates editing/navigation; new feature-specific list logic belongs in its feature directory.
- Keep WebSocket lifecycle shared so collapsing a panel does not open duplicate connections or discard pending requests.
- AppShell provides records/tasks/reports/projects navigation. RetainedView mounts on first visit and hides previously visited pages, preserving drafts and realtime subscriptions across navigation.
- RecordsPage owns its date and AI/manual input mode; TasksPanel keeps filters while refreshing via a revision prop. Do not use a changing React key to refresh business lists.
- RecordForm and TaskEditorDialog provide right-side editor drawers, full-screen on narrow viewports, with unsaved-change confirmation and focus restoration.
- ReportsPage retains both daily and weekly editors after first visit; switching report type preserves drafts. Date/version changes retain their explicit discard guard.
- ProjectPicker uses the complete loaded option set and current archived association; do not truncate it to 50 options.
- Avoid forwarding-only old root modules after moving components; update imports directly.

## Pagination behavior

- A disabled page boundary uses `cursor: not-allowed`, never a permanent wait cursor.
- Actual fetches expose loading state and clear it on success, failure or cancellation.
- Every visible size selector must update both request size and source numbering; do not pass no-op callbacks.
- Report history page/size changes honor the unsaved-content guard before changing selection.
- Source-page changes do not discard the report draft. Changing the selected report resets its source page.
- Refresh totals after generation; otherwise a new version can exist while Next remains incorrectly disabled.
- Use one request-ownership path for initial loads, page/filter changes and CRUD-triggered refreshes. A late refresh must not overwrite a newer page or date, and loading must settle after abort/error.
- On a report-save response, verify the currently selected report and preserve any newer draft edits. A save initiated on version A must not switch the editor back after the user selected version B.

## Verification

Use browser tests with more than two pages. Assert item IDs/text and page request parameters, not just page labels. Cover late responses, 10/20/50 source sizing, empty pages after deletion, report draft cancellation/confirmation, and generation that crosses a total-page boundary.
