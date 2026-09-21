# Daily Report Version Deletion

## 1. Scope

Keep multiple DAILY versions per date. A user may delete an unwanted terminal DAILY version without removing work records or frozen evidence. WEEKLY deletion is outside this change because weekly versions have predecessor links.

## 2. Signature

`DELETE /api/reports/{id}?version=<non-negative>` returns 204 on success. Reports use `deleted_at` soft deletion.

## 3. Contract

- Lock the report and reject WEEKLY or PROCESSING rows.
- A live terminal DAILY row requires the expected version; concurrent edits must not be lost.
- Repeating deletion of an already-deleted DAILY row returns 204 even with the earlier version.
- Normal detail/history/count/source endpoints hide deleted reports. Original request IDs remain reserved; resubmission must not revive a deleted version.
- Preserve report_sources, source business records and other versions. Conditional content/result writes exclude deleted rows.
- Confirm through the shared application dialog. Cancel keeps the draft; failure keeps the current selection. Success refreshes history and selects an available version or empty state.

## 4. Errors

| Condition | Response |
|---|---|
| Unknown report or normal read of deleted report | 404 |
| Processing/WEEKLY/stale live version | 409 |
| Deleted request ID resubmitted | 409 |
| Successful or repeated DAILY deletion | 204 |

## 5. Cases

Good: create A and B on the same date, delete B, continue editing A. Base: deleting the only version returns the panel to its empty state. Bad: enforce one report per day or cascade-delete work records to remove a draft.

## 6. Verification

Real PostgreSQL tests assert version conflicts, repeat deletion, request-ID reservation, hidden source/detail queries, accurate counts, and preserved source snapshots/records. Browser tests assert cancellation, another-version selection, last-version empty state, failed-report deletion, and no revival by late responses.

## 7. Wrong vs Correct

Wrong: physical report deletion cascading into report_sources or accepting an unconditional delete without version checking.

Correct: transactionally set deleted_at with an expected version, retain historical evidence, and exclude the row from normal queries.
