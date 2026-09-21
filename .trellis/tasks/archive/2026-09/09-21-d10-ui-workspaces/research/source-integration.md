# Frontend source integration

## Boundary

Imported the formal `src/` candidate from `changes/workbench-ui-refactor.zip` through an explicit `apply_patch` diff against the current files. The archive was first validated against directory traversal and extracted under ignored `.local-runtime/ui-refactor-reference/`. Reference files in `changes/` remain unchanged.

The change replaces one long page with records, tasks, reports and projects workspaces. Page ownership, editors and shared visual components are in `frontend/src`; API, realtime transport, package dependencies, lockfile, Vite and container configuration are retained.

## Integration adjustments

- Fixed full-project lint findings: visited-state handling in RetainedView, record-date draft synchronization and the task overdue clock. Shared navigation constants live separately for React Fast Refresh.
- Kept the existing daily report deletion and cross-window DELETED handling; the candidate report changes retain those controllers and modify presentation.
- Record lists remain ordered by backend creation time; changed the candidate's occurrence-time caption to `最新创建在前`.
- A manually changed occurrence time counts as a draft and survives browsing dates. Date navigation only moves the default occurrence time when the form is clean.
- The skip link focuses the main content without changing the hash/workspace.
- Preserved Saturday/Sunday date accents and archived-project edit semantics. A newly archived selection in a new task cannot be submitted until another project is selected.
- Empty/error lists show their explicit state; a zero-page list has no pagination controls. Source evidence is expanded by default beside report editing.
- The first real regression run exposed a modal lifecycle issue: project archive/rename and task writes awaited the follow-up GET before closing. A delayed list response left the background inert, blocking a new search. Success now closes the write dialog immediately while refresh errors are handled by the existing list owner. The controlled delayed-response regression passes after this fix.
- The external-report-deletion test originally treated Vite's WebSocket handshake as the business connection. The test owner restricted its handshake assertion to `/ws/events`; deletion clears the selected report through the retained realtime handler without backend or transport changes.
- Visual review of actual 320px screenshots caught the native dialog UA `max-width` leaving a 38px background strip. The drawer now sets `max-width: 100vw` explicitly; the responsive test verifies mobile bounding boxes in addition to overflow.

## Validation

- Initial full `npm run build` (TypeScript project references + Vite production bundle): passed.
- `npm run lint` after integration adjustments: passed.
- After the delayed-refresh fix, full build and lint passed again; the two targeted real-backend regressions passed without retries.
- The complete real-backend suite passed 19/19 with retries disabled. A subsequent CSS-only drawer correction is verified with a separate responsive target and screenshots, retaining the original full-suite artifacts.
- `frontend/src` scan: no preview/mock-api import or demonstration-data banner.
- Real-backend browser navigation, regression and responsive screenshots are recorded separately in `e2e-validation.md` by the test owner.

No backend code changes or model calls were made by this integration step. No production business data was edited.
