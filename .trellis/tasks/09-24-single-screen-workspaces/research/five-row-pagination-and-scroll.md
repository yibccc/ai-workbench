# Five-row pagination and scroll audit

## Evidence

- `backend/src/main/java/com/aiworkbench/common/PageResponse.java` rejects every page size except 10, 20, 50; all paged services call `PageQueries.select`, which invokes this validation. A UI-only switch to size 5 would return HTTP 400.
- `frontend/src/components/Pagination.tsx` exposes 10/20/50 page-size options. Records, tasks, projects, report history, and report sources each initialize size 20. `frontend/src/api/pagination.ts` is the existing shared pagination module suitable for a single UI page-size constant.
- `frontend/e2e/workbench.spec.ts` explicitly tests size changes, page counts, and source global numbering. The 5-row requirement needs these tests revised to validate actual request size and content instead of selecting old sizes.
- `frontend/src/styles.css` currently centers `.main-content` under a 1170px width cap with 38/40px top/side padding and uses `scrollbar-gutter: stable` for broad scroll regions. This explains the unused desktop margins and the visible scrollbar track even when little content overflows.
- The report page currently places document and source evidence in one `.report-scroll`, so scrolling the body moves both together. The user requires separate scroll owners, especially for weekly fact sources.

## Compatibility

Keep backend 10/20/50 support and its default size 20 for API callers that do not use the frontend. Add size 5 rather than replacing old API sizes. Do not paginate the source collection used to generate report content; only the visible evidence list is paged.

## Relevant specs

- `.trellis/spec/backend/pagination.md`: zero-based API, PageHelper scope, validation, source numbering and browser tests.
- `.trellis/spec/frontend/directory-structure.md`: report history/source hooks own paging and stale-request cancellation.
- `.trellis/spec/frontend/viewport-layout.md`: viewport height chain and scroll ownership; revise after independent report panes are implemented.
