# PageHelper Pagination

## 1. Scope / Trigger

Use this contract for every paged list. PageHelper performs database count and physical pagination; the API remains zero-based. Do not introduce frontend-only slicing of full lists as the pagination implementation.

## 2. Signatures

- `GET /api/{projects|records|tasks|reports}/page?page=0&size=5` for workbench UI lists, with existing resource filters. API calls that omit `size` retain the controller default of 20.
- Report source paging retains its existing report-specific route.
- `PageResponse<T>(items, page, size, totalElements, totalPages)`.
- `PageQueries.select(page, size, mapperQuery, dtoMapper)` owns the PageHelper scope.

## 3. Contracts

- Validate page >= 0 and size in 5/10/20/50 before starting PageHelper; reject excessive offsets instead of overflowing. Size 5 serves the workbench UI; 10/20/50 remain accepted for existing API callers.
- Translate external page to `page + 1` internally; `reasonable=false` preserves out-of-range empty-page semantics.
- Execute exactly the intended mapper select inside the paging scope. Always `PageHelper.clearPage()` in finally, including exceptions before MyBatis interception.
- Capture `PageInfo` before converting rows to DTOs. Map DTOs after the pagination scope is cleared so any related query is not accidentally paginated.
- Mapper list SQL owns filters and deterministic `created_at DESC, id DESC` ordering; remove manual pagination LIMIT/OFFSET and duplicate count queries replaced by the interceptor.
- Records paging is a daily presentation projection: aggregate same-owner/task/day focus into an active same-day task-completion item and exclude absorbed focus before PageHelper counts or limits. Count visible entries, not raw ledger rows; keep raw list/detail queries available to timing statistics and report evidence.
- Report source queries preserve frozen ordering and stable global numbering; the visible source list requests size 5, while source selection for generation is always unpaged.
- Original array endpoints retain compatibility and must not inherit thread-local pagination from a preceding request.

## 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Negative page / unsupported size / excessive offset | 400 problem detail |
| Valid page beyond total | Empty items and accurate total; no automatic server-side clamp |
| Mapper failure | Pagination state cleared; original error handling retained |
| Last page | Next disabled with unavailable cursor, not wait cursor |
| Actual request in flight | Explicit loading state; always ends on failure/abort/success |
| UI filter change | Reset to first page and reload |
| Existing API caller changes size | New request uses the supplied supported size; the server remains zero-based |

## 5. Good / Base / Bad Cases

- Good: read page 2 with size 5, then all report sources on the same thread; the second query returns all sources. A size 20 request still succeeds for an older client.
- Base: no matches returns totalElements=0 and totalPages=0.
- Bad: call startPage and return without selecting/clearing, or map to an ordinary List before capturing total metadata.

## 6. Tests Required

- Real PostgreSQL count/filter/ordering/first-last-out-of-range page checks.
- Records merging must return accurate totals even when the raw focus/completion pair spans a page boundary; several same-day sessions sum once, while other owners/days and reopened completions cannot hide focus rows.
- Failure before mapper interception still clears thread-local state.
- DTO conversion and subsequent full report-source selection run with no paging state.
- HTTP tests cover size 5 on all paged routes, including report sources, plus default 20 and retained 10/20/50 compatibility.
- Browser next/previous tests assert actual five-item changes and HTTP `size=5` parameters, not merely label changes. The workbench exposes no page-size selector.
- Report history navigation honors dirty confirmation; source page changes preserve global labels in five-item increments.

## 7. Wrong vs Correct

Wrong: `startPage(...); unrelatedMapper.select(); targetMapper.select();` pages the wrong query.

Correct: `PageQueries.select(page, size, () -> mapper.findPage(filters), Row::toResponse)` isolates one select and releases paging before conversion.
