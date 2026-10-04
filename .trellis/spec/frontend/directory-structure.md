# Frontend Directory Structure

## Organization

Use React feature modules plus shared infrastructure. Frontend components do not mirror Java service interfaces: split them according to UI and state ownership.

```text
src/
├── App.tsx                 Authenticated root, page composition and cross-feature coordination
├── main.tsx                Entry point
├── styles.css              Shared styles
├── features/
│   ├── capture/            AI input and generated results
│   ├── community/          Member feed/profile/document and directly scoped R2 layout
│   ├── publishing/         Date-scoped material selection, draft/preview and upload state
│   ├── auth/               Login, account menu, password dialog and ADMIN user page
│   ├── interview/          Private fixed text interviews, answer state and persistent report views
│   ├── profile/            Private current Markdown resume and imported-original editor
│   ├── focus/              Focus timer, routines, daily totals and account-scoped controller
│   ├── projects/           Project management and project picker
│   ├── records/            Record-list presentation
│   ├── tasks/              Task form, filters and list
│   └── reports/            Daily/weekly panels and shared report hooks
├── components/             Pagination and collapsible sections
├── api/                    Shared HTTP client; per-feature requests and types
├── hooks/                  Identity-scoped STOMP and fallback lifecycle
└── utils/                  Shanghai date conversion
```

## Boundaries

- Import feature APIs from their module under `api`, not a growing root api.ts.
- Shared components receive state/callbacks rather than fetching unrelated business entities.
- `useReportHistory` owns history paging/metadata. `useReportSources` owns source paging, size and stale-request cancellation.
- App composes feature components and coordinates editing/navigation; new feature-specific list logic belongs in its feature directory.
- Community/publishing share the same account-root/main DOM lifetime as retained workspaces; the single strict hash parser owns both route families. Their full snapshots/Blob/retry/R2 contracts are in [Community and Publishing UI](./community-publishing.md).
- Keep STOMP lifecycle shared so collapsing a panel does not open duplicate connections or discard same-user pending requests. Close subscriptions, fallback timers and identity-specific pending IDs on account change; see [Identity and Session UI](identity-session.md).
- AppShell provides records/tasks/focus/reports/projects/interview navigation plus an account menu. Private profile and ADMIN user management are auxiliary views outside the six business nav items. RetainedView mounts on first visit and hides previously visited pages, preserving drafts and realtime subscriptions within one account; the authenticated root remounts it on account change. The focus controller and its compact shell control live at the account root so they remain active when focus, interview or profile views switch. See [Private Resume and Interview UI](./private-interviews.md).
- `ToastProvider` owns transient operation feedback across features and sits above the login/workspace switch so an expiry Toast survives the immediate redirect. Its body portal avoids clipping by the viewport-height shell; callers from retained views only notify while their view is visible. Each new event restarts the five-second timer, and the close action dismisses immediately. Keep validation, unsaved, and stored failure messages in their local context.

  ```tsx
  const { notify, toastRef } = useToast<HTMLDivElement>()
  // Attach toastRef to the feature root; hidden retained views cannot notify.
  return <div ref={toastRef}><button onClick={() => notify('待办已重开', 'success')}>重开</button></div>
  ```
- RecordsPage owns its date and AI/manual input mode; TasksPanel keeps filters while refreshing via a revision prop. Do not use a changing React key to refresh business lists.
- RecordForm and TaskEditorDialog provide right-side editor drawers, full-screen on narrow viewports, with unsaved-change confirmation and focus restoration.
- ReportsPage retains both daily and weekly editors after first visit; switching report type preserves drafts. Date/version changes retain their explicit discard guard.
- ProjectPicker uses the complete loaded option set and current archived association; do not truncate it to 50 options.
- Avoid forwarding-only old root modules after moving components; update imports directly.

## Pagination behavior

- A disabled page boundary uses `cursor: not-allowed`, never a permanent wait cursor.
- Actual fetches expose loading state and clear it on success, failure or cancellation.
- Visible paged workbench lists use `WORKSPACE_PAGE_SIZE = 5` from `api/pagination.ts`; the UI has no page-size selector. Backend API callers may still request 10/20/50.
- A paged card owns its pagination footer below a separate scrolling rows region. Do not place the pager inside the rows scrollbar or absolutely overlay it on the last row. This applies to records, tasks, projects, report history, and report evidence.
- Report history page changes honor the unsaved-content guard before changing selection.
- Source-page changes do not discard the report draft. Changing the selected report resets its source page.
- Refresh totals after generation; otherwise a new version can exist while Next remains incorrectly disabled.
- Use one request-ownership path for initial loads, page/filter changes and CRUD-triggered refreshes. A late refresh must not overwrite a newer page or date, and loading must settle after abort/error.
- On a report-save response, verify the currently selected report and preserve any newer draft edits. A save initiated on version A must not switch the editor back after the user selected version B.

## Verification

Use browser tests with more than two pages. Assert item IDs/text and `size=5` page request parameters, not just page labels. Cover late responses, five-item source numbering, empty pages after deletion, report draft cancellation/confirmation, and generation that crosses a total-page boundary.
