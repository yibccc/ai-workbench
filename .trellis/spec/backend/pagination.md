# PageHelper Pagination

## 1. Scope / Trigger

Use this contract for every paged list. PageHelper performs database count and physical pagination; the API remains zero-based. Do not introduce frontend-only slicing of full lists as the pagination implementation.

## 2. Signatures

- `GET /api/{projects|records|tasks|reports}/page?page=0&size=20` with existing resource filters.
- Report source paging retains its existing report-specific route.
- `PageResponse<T>(items, page, size, totalElements, totalPages)`.
- `PageQueries.select(page, size, mapperQuery, dtoMapper)` owns the PageHelper scope.

## 3. Contracts

- Validate page >= 0 and size in 10/20/50 before starting PageHelper; reject excessive offsets instead of overflowing.
- Translate external page to `page + 1` internally; `reasonable=false` preserves out-of-range empty-page semantics.
- Execute exactly the intended mapper select inside the paging scope. Always `PageHelper.clearPage()` in finally, including exceptions before MyBatis interception.
- Capture `PageInfo` before converting rows to DTOs. Map DTOs after the pagination scope is cleared so any related query is not accidentally paginated.
- Mapper list SQL owns filters and deterministic `created_at DESC, id DESC` ordering; remove manual pagination LIMIT/OFFSET and duplicate count queries replaced by the interceptor.
- Report source queries preserve frozen ordering and stable global numbering; source selection for generation is always unpaged.
- Original array endpoints retain compatibility and must not inherit thread-local pagination from a preceding request.

## 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Negative page / unsupported size / excessive offset | 400 problem detail |
| Valid page beyond total | Empty items and accurate total; no automatic server-side clamp |
| Mapper failure | Pagination state cleared; original error handling retained |
| Last page | Next disabled with unavailable cursor, not wait cursor |
| Actual request in flight | Explicit loading state; always ends on failure/abort/success |
| Filter or page-size change | Reset to first page and reload |

## 5. Good / Base / Bad Cases

- Good: read page 2, then all report sources on the same thread; the second query returns all sources.
- Base: no matches returns totalElements=0 and totalPages=0.
- Bad: call startPage and return without selecting/clearing, or map to an ordinary List before capturing total metadata.

## 6. Tests Required

- Real PostgreSQL count/filter/ordering/first-last-out-of-range page checks.
- Failure before mapper interception still clears thread-local state.
- DTO conversion and subsequent full report-source selection run with no paging state.
- Browser next/previous/page-size tests assert actual item changes and HTTP page parameters, not merely label changes.
- Report history navigation honors dirty confirmation; source page-size changes affect real query size and global source labels.

## 7. Wrong vs Correct

Wrong: `startPage(...); unrelatedMapper.select(); targetMapper.select();` pages the wrong query.

Correct: `PageQueries.select(page, size, () -> mapper.findPage(filters), Row::toResponse)` isolates one select and releases paging before conversion.
