# Database Guidelines

## Scenario: PostgreSQL persistence for projects and work records

### 1. Scope / Trigger

Use these rules for every schema migration, MyBatis mapper, project association, or query that interprets a work date. PostgreSQL is the persistence authority; Redis and frontend state must never be required for correctness.

### 2. Signatures

Current API and query signatures:

- `GET /api/projects?includeArchived=false -> ProjectResponse[]`
- `POST /api/projects { name } -> ProjectResponse`
- `PATCH /api/projects/{id} { name } -> ProjectResponse`
- `POST /api/projects/{id}/archive -> ProjectResponse`
- `GET /api/records?date=YYYY-MM-DD -> WorkRecordResponse[]`
- `POST /api/records { projectId?, content, occurredAt } -> WorkRecordResponse`
- `PUT /api/records/{id} { projectId?, content, occurredAt } -> WorkRecordResponse`
- `DELETE /api/records/{id} -> 204`
- `WorkRecordMapper.findBetween(Instant start, Instant end)` uses a left-closed, right-open range.

Database tables are `projects`, `capture_inputs`, `todo_items`, `work_records`, `task_events`, and `reports`. Primary and foreign business identifiers use PostgreSQL `UUID`.

### 3. Contracts

#### Schema and migration ownership

- Flyway owns schema evolution under `backend/src/main/resources/db/migration/`.
- Never edit an applied migration after it has shipped; add the next versioned migration.
- Use lower snake case for tables, columns, indexes, and constraints.
- Constraints enforce durable invariants: nonblank content, enum-like status values, active project-name uniqueness, valid report periods, and foreign-key ownership.
- Project and work-record foreign keys use `ON DELETE RESTRICT`; archiving is a state transition, not deletion.

#### Time contract

- Persist instants as `TIMESTAMPTZ` and expose ISO-8601 instants through the API.
- `occurred_at` is when work happened; `created_at` is when it was entered. They are not interchangeable.
- A requested work date is interpreted in `workbench.zone-id`, default `Asia/Shanghai`.
- Date queries use `[startOfDay, nextStartOfDay)` after converting both boundaries to `Instant`.

#### MyBatis contract

- SQL lives in mapper XML; Java mapper interfaces expose typed parameters and rows.
- PostgreSQL UUID result columns require the project `UuidTypeHandler`; do not rely on a driver-specific default conversion.
- Mapper rows are persistence-only types. Convert them to API response records at the service boundary.
- Services own transactions and business validation. Controllers do not perform SQL or construct persistence rows.

#### Project association contract

- New records may reference only an `ACTIVE` project.
- Changing an existing record to a different project also requires the target project to be active.
- An existing record may retain its archived project while other fields are edited.
- Project rename preserves the UUID. Archive preserves every historical foreign-key association.
- Active project names are case-insensitively unique through the partial index `uq_projects_active_name`.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Blank/oversized project name | HTTP 400 problem detail |
| Duplicate active project name, case-insensitive | HTTP 409, `同名的活动项目已存在` |
| Missing project or record UUID | HTTP 404 problem detail |
| New record targets archived project | HTTP 409, `归档项目不能用于新记录` |
| Existing record retains the same archived project | Update is allowed |
| Existing record switches to archived project | HTTP 409 |
| Blank/oversized record content or missing `occurredAt` | HTTP 400 problem detail |
| Delete a project referenced by history | PostgreSQL FK rejects it; application flow should archive instead |
| Date omitted from record list | Use the current date in `Asia/Shanghai` |

### 5. Good / Base / Bad Cases

- Good: a record entered today with yesterday's `occurredAt` appears in yesterday's query while `createdAt` remains today; refreshing reloads it from PostgreSQL.
- Base: a record has no project and remains valid; listing an empty day returns an empty array.
- Bad: converting dates with the browser timezone, clearing an archived project from an edited historical record, using `created_at` for reports, or testing PostgreSQL constraints with an in-memory substitute.

### 6. Tests Required

- Run persistence integration tests against real PostgreSQL, including a fresh empty database that executes all Flyway migrations.
- Assert `occurred_at` and `created_at` independently through direct SQL, not only through the same service that wrote them.
- Test the Asia/Shanghai day boundary with records immediately before and after midnight.
- Test active-name uniqueness, archived-project rejection, archived historical association retention, and FK `RESTRICT` behavior.
- Test create, update, re-query after transaction completion, and delete.
- Test HTTP 400/404/409 response bodies as well as status codes.
- Required backend gate: `mvn clean verify` with the configured local PostgreSQL available.

### 7. Wrong vs Correct

#### Wrong

```java
Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
projectId = project.status().equals("ARCHIVED") ? null : projectId;
```

This makes results machine-dependent and silently destroys historical attribution.

#### Correct

```java
Instant start = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
Instant end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();

if (newProjectId != null && !newProjectId.equals(currentProjectId)) {
    projectService.requireActive(newProjectId);
}
```

Keep time boundaries explicit and validate only a newly introduced project association.

---

## Scenario: Task management, filtering, and optimistic locking

### 1. Scope / Trigger

Use this contract when changing `todo_items`, task CRUD, task filters, or the later completion/reopen flow. D3 owns task creation, metadata edits, deletion, and queries. D4 exclusively owns transitions between `PENDING` and `COMPLETED` because those transitions must remain consistent with completion work records.

### 2. Signatures

- `GET /api/tasks?status=&projectId=&unassigned=false&priority=&due=ALL -> TaskResponse[]`
- `GET /api/tasks/{id} -> TaskResponse`
- `POST /api/tasks { projectId?, title, notes?, dueAt?, priority? } -> 201 TaskResponse`
- `PUT /api/tasks/{id} { projectId?, title, notes?, dueAt?, priority, version } -> TaskResponse`
- `DELETE /api/tasks/{id}?version=<non-negative> -> 204`
- `TaskDueFilter = ALL | OVERDUE | TODAY | UPCOMING | NONE`
- `TaskPriority = HIGH | MEDIUM | LOW`; omitted create priority defaults to `MEDIUM`.
- `TaskStatus = PENDING | COMPLETED`; general D3 update requests do not contain status.

### 3. Contracts

- Flyway migrations are immutable. V2 evolves the V1 task table and migrates legacy states; V3 corrects the inherited column default to `PENDING` instead of rewriting V1 or an already-applied V2.
- Every update executes `... WHERE id = ? AND version = ?`, increments `version`, and returns the new value.
- Every delete includes the expected version in the SQL predicate.
- A zero-row update/delete is an optimistic-lock conflict, not a successful idempotent operation.
- New task associations and changes to a different project require an active project. Existing tasks retain archived project attribution.
- `unassigned=true` means `project_id IS NULL` and is mutually exclusive with `projectId`.
- `OVERDUE` means `due_at < now` and `status=PENDING`.
- `TODAY` means the Asia/Shanghai range `[todayStart, tomorrowStart)`.
- `UPCOMING` means `due_at >= tomorrowStart`; `NONE` means `due_at IS NULL`.
- `completed_at` is reserved for D4 state transitions. D3 must not write it or expose a generic status mutation.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Blank/oversized title or oversized notes | HTTP 400 problem detail |
| Invalid status, priority, or due enum | HTTP 400 problem detail |
| `unassigned=true` with `projectId` | HTTP 400, explicit contradictory-filter detail |
| Missing task | HTTP 404 |
| New/different archived project association | HTTP 409 |
| Stale update or delete version | HTTP 409, `待办已被其他操作修改，请刷新后重试` |
| Create priority omitted | Persist and return `MEDIUM` |
| Due date omitted | Persist `NULL`; only `due=NONE` selects it by due category |

### 5. Good / Base / Bad Cases

- Good: a client reads version 2, updates with version 2, receives version 3, and any second write using version 2 receives 409.
- Base: an unassigned task with no due date is valid and defaults to `PENDING`, `MEDIUM`, version 0.
- Bad: changing status through the generic update endpoint, using a read-then-unconditional-write optimistic lock, treating stale delete as 204, or modifying V1/V2 after application.

### 6. Tests Required

- Run real PostgreSQL migration tests for both an empty schema V1→latest and legacy V1 rows containing `OPEN`/`DONE` before V2/V3.
- Assert default status, priority, and version through direct SQL or a re-query after transaction completion.
- Exercise stale update and stale delete; assert HTTP 409 and problem-detail text.
- Test status, project, unassigned, priority, and every due category, including combinations.
- Use a clearly labeled database fixture for `COMPLETED` query tests until D4 implements the transition use case.
- Assert V1 checksum/content remains unchanged while later migrations evolve the table.

### 7. Wrong vs Correct

#### Wrong

```sql
UPDATE todo_items SET title = #{title}, version = version + 1 WHERE id = #{id};
```

```java
updateRequest.status(TaskStatus.COMPLETED);
```

The first silently overwrites concurrent edits; the second bypasses D4 completion-record consistency.

#### Correct

```sql
UPDATE todo_items
SET title = #{title}, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = #{id} AND version = #{version};
```

Return HTTP 409 when the affected row count is zero, and expose completion/reopen only through D4-specific transactional commands.

---

## Scenario: Task completion consistency and history

### 1. Scope / Trigger

Use this contract for task completion, reopen, deletion, completion-result edits, task events, automatic work records, and report queries that consume completion facts. All writes for one state transition belong to one PostgreSQL transaction.

### 2. Signatures

- `POST /api/tasks/{id}/complete { version, result? } -> TaskResponse`
- `POST /api/tasks/{id}/reopen { version } -> TaskResponse`
- `PUT /api/tasks/{id}/completion-result { version, result } -> TaskResponse`
- `GET /api/tasks/{id}/events -> TaskEventResponse[]`, including soft-deleted tasks
- `DELETE /api/tasks/{id}?version=<non-negative> -> 204` performs a soft delete
- `WorkRecordSource = MANUAL | TASK_COMPLETION`

### 3. Contracts

- A real `PENDING -> COMPLETED` transition atomically updates task status/version/completed time, inserts one active `TASK_COMPLETION` work record, and inserts one `COMPLETED` event.
- A real `COMPLETED -> PENDING` transition atomically updates the task, invalidates the active automatic record, and inserts one `REOPENED` event.
- Completing again after reopen creates a new automatic record; invalid historical records remain queryable.
- The partial unique index `uq_work_records_active_task_completion` is the final concurrency guard: at most one row per `todo_id` where `source='TASK_COMPLETION' AND is_active`.
- Repeating `complete` when the current state is already `COMPLETED` is a read-only success. It must not create, repair, or overwrite records or results.
- Repeating `reopen` when the current state is already `PENDING` is a read-only success.
- A stale version receives 409 when the requested target state has not already been achieved.
- Completion-result updates require a currently completed task and active automatic record. The result is stored on that record and copied into the event snapshot.
- Task deletion sets `deleted_at`, invalidates any active automatic completion record, and adds a `DELETED` event. Normal task queries exclude deleted rows; event history remains available.
- `task_events.todo_id` and `work_records.todo_id` use `ON DELETE RESTRICT`. Do not reintroduce cascade deletion.
- Automatic completion records cannot be edited or deleted by general work-record endpoints.
- Reports and daily summaries must consume only `is_active=true` automatic records for current completion facts, while history views may include inactive records.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Missing/negative body version | HTTP 400 problem detail |
| Real transition with current version | Atomic success and version increment |
| Repeat complete when already completed | Return current task; no new event/record/result overwrite |
| Repeat reopen when already pending | Return current task; no new event |
| Stale version while target state not achieved | HTTP 409 optimistic-lock detail |
| Result update while pending or without active completion record | HTTP 409 |
| General PUT/DELETE of automatic work record | HTTP 409 problem detail |
| Concurrent completes | Exactly one active automatic record and one real COMPLETED event |
| Any transition sub-write fails | Entire transaction rolls back |

### 5. Good / Base / Bad Cases

- Good: complete twice, reopen twice, complete again yields active-record counts `1, 1, 0, 0, 1`; the second completion has a new record ID and all history remains.
- Base: a manual work record is never affected by task reopen or deletion.
- Bad: repairing a missing automatic record during an idempotent repeat, physically deleting task/event history, allowing general record CRUD to mutate automatic facts, or filtering reports without `is_active`.

### 6. Tests Required

- Run the exact `1,1,0,1` sequence and assert record IDs, status, versions, and event counts.
- Run two completion calls in independent transactions with an explicit synchronization point; assert one active record and one transition event.
- Repeat the concurrency test to catch scheduling-sensitive failures.
- Inject a failure after the task update and assert task, record, and event writes all roll back.
- Test repeat complete/reopen, stale version when the target is not achieved, and missing-version validation.
- Test result preservation after reopen and historical visibility after deletion/restart.
- Test manual-record isolation and HTTP 409 protection for automatic-record PUT/DELETE.
- Test V1 legacy `OPEN`/`DONE` rows through V4 and an empty schema through all migrations.

### 7. Wrong vs Correct

#### Wrong

```java
if (task.isCompleted() && activeRecordMissing()) {
    createCompletionRecord();
}
```

This is not idempotent repair: it can race with reopen and leave a pending task with an active completion fact.

#### Correct

```java
if (task.status() == TaskStatus.COMPLETED) {
    return task.toResponse();
}
```

Only a successful conditional `PENDING -> COMPLETED` write may create the automatic record and event, inside the same transaction.

---

## Scenario: AI capture persistence and atomic batches

### 1. Scope / Trigger

Use this contract for natural-language input, request idempotency, AI-processing state, retry, and generated record/task persistence. PostgreSQL owns the durable input state; in-memory scheduling only coordinates work inside one process.

### 2. Signatures

- `POST /api/inputs { requestId, content } -> 201 InputResponse`
- `GET /api/inputs/{id} -> InputResponse`
- `POST /api/inputs/{id}/retry -> InputResponse`
- `InputStatus = PROCESSING | SUCCEEDED | FAILED | REVERTED`
- Generated `work_records.capture_input_id` and `todo_items.capture_input_id` reference the source input with `ON DELETE RESTRICT`.

### 3. Contracts

- The first short transaction persists raw content, unique `client_request_id`, `reference_at`, `zone_id`, `PROCESSING`, and attempt 1 before any model call.
- `client_request_id`, not content equality, defines request idempotency. Reusing an ID returns the original input; a different ID with identical text is a new input.
- The AI gateway call occurs after the first transaction commits and outside every database transaction.
- Success uses one short transaction to insert every generated record and task, attach each row to the input, and mark the input `SUCCEEDED`.
- Any generated-row or final-state failure rolls back the whole success transaction. No half-batch may remain.
- Failure uses a separate short transaction to preserve raw input and mark it `FAILED` with a sanitized message.
- Basic retry is accepted only from `FAILED`, increments `attempt_count`, clears the previous failure, and reuses the original `reference_at` and `zone_id`.
- Generated projects are resolved only against the current active-project snapshot. Unknown or non-unique names become `NULL`; the system never creates a project from model output.
- Unknown priority becomes `MEDIUM`. Dates must be explicit ISO-8601 instants after model parsing.
- D6 owns cross-process retry locking, abandoned `PROCESSING` recovery, and batch revert; D5 must not claim those guarantees early.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Blank/invalid request fields | HTTP 400 problem detail; no input row |
| Reused request ID | Return original input and original content; no second batch |
| Retry when not `FAILED` | HTTP 409 |
| Model unavailable or invalid output | Input becomes `FAILED`; raw content and baseline remain |
| Empty extraction | `FAILED`; no generated rows |
| More than 50 total items | `FAILED`; no generated rows |
| Invalid/oversized generated field or date | `FAILED`; no generated rows |
| Second generated row violates a database constraint | Entire generated batch rolls back; input failure is persisted separately |
| Scheduler rejects work | Input becomes `FAILED`, not permanently `PROCESSING` |

### 5. Good / Base / Bad Cases

- Good: one mixed input commits first, the model runs without a transaction, and one later transaction stores multiple records/tasks plus `SUCCEEDED`.
- Base: a record-only or task-only result is valid; missing project and due date remain null; missing priority becomes `MEDIUM`.
- Bad: opening one transaction around the model call, deduplicating by text, changing the retry baseline, or persisting generated items one transaction at a time.

### 6. Tests Required

- Prove raw input exists before invoking the fake gateway.
- Inspect transaction synchronization or connection state inside the fake gateway and assert no transaction is active.
- Reuse a request ID with different text and assert the original content and batch remain unchanged.
- Force a later generated insert to fail and assert every generated row rolls back while the input becomes `FAILED`.
- Test multiple items, record-only, task-only, empty output, unknown project, unknown priority, invalid dates, and maximum-item enforcement.
- Retry the same failed input and assert unchanged `reference_at`/`zone_id` plus incremented `attempt_count`.
- Run V1 legacy capture rows through V5 and an empty schema through all migrations.

### 7. Wrong vs Correct

#### Wrong

```java
@Transactional
public void capture(String text) {
    AiCaptureResult result = aiGateway.extract(text); // network call holds the transaction
    result.items().forEach(this::saveIndividually);
}
```

#### Correct

```java
InputRow input = persistence.createOrGet(requestId, text, referenceAt, zoneId);
AiCaptureResult result = aiGateway.extract(input.content(), input.referenceAt(), zoneId, projects);
persistence.succeed(input.id(), validatedBatch, completedAt);
```

Keep the orchestration service non-transactional and place each short transaction on a separate Spring bean.

---

## Scenario: Capture recovery, fencing, and batch revert

### 1. Scope / Trigger

Use this contract for cross-process capture ownership, retries, abandoned-processing recovery, generated-item versioning, and whole-batch revert. PostgreSQL conditions are the correctness boundary; process memory and Redis may optimize but must be disposable.

### 2. Signatures

- `POST /api/inputs/{id}/retry -> InputResponse`
- `POST /api/inputs/{id}/revert -> InputResponse`
- `processing_token: UUID?`, `lease_expires_at: TIMESTAMPTZ?`
- `capture_generated_items(input_id, entity_type, entity_id, initial_version)`
- `work_records.version >= 0`
- `capture_inputs.revertible` defaults true for new rows; migrated legacy rows remain false.

### 3. Contracts

- Create ownership belongs only to the transaction that inserts the unique request ID and its token. Other callers return the stored input without scheduling work.
- Retry ownership uses one conditional `FAILED -> PROCESSING` update that installs a new token and lease.
- Success inserts the complete generated batch and marks `SUCCEEDED` with `WHERE id=? AND processing_token=?` in the same transaction. A stale token makes the final update affect zero rows, rolling back every generated row and ledger entry.
- Failure also carries the token; an old owner cannot overwrite a newer owner or terminal state.
- Startup recovery changes only `PROCESSING` rows whose lease expired or whose legacy lease is null. It preserves content, `reference_at`, and `zone_id`, then makes the input retryable.
- The model timeout must be shorter than the lease. Startup fails fast if `leaseDuration <= requestTimeout`.
- The immutable ledger records every generated entity and its initial version. New successful inputs are revertible only when all ledger entities still exist and match the untouched state.
- Revert locks the input row. `SUCCEEDED` is required; `REVERTED` returns idempotently. Any edited, completed, reopened, deleted, missing, inactive, or version-changed generated item returns 409 with no mutation.
- Successful revert atomically invalidates generated records, soft-deletes generated tasks, and marks the input `REVERTED`. Original input, ledger, and history remain.
- Legacy inputs migrated without trustworthy creation-state evidence have `revertible=false` and must be rejected rather than guessed.
- Manual record deletion uses `WHERE is_active`; a concurrent revert that already invalidated the row causes 409 instead of physical deletion.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Concurrent create with same request ID | One DB owner; one scheduled model call per database claim |
| Concurrent retries | One token owner; others observe PROCESSING or receive conflict; one final batch |
| Old owner attempts success | Whole transaction rolls back because token finalization affects zero rows |
| Old owner attempts failure | Zero rows changed; newer owner/state preserved |
| Startup sees valid lease | Leave PROCESSING unchanged |
| Startup sees expired/null legacy lease | Mark FAILED with recovery message; baseline preserved |
| Revert untouched successful batch | Atomic REVERTED; generated items disappear from normal queries |
| Repeat revert | Return current REVERTED state without new writes |
| Revert after any downstream change | HTTP 409; zero batch mutations |
| Revert legacy non-revertible input | HTTP 409 safe-revert message |

### 5. Good / Base / Bad Cases

- Good: two service instances race to retry; one owns the token, the stale caller cannot finalize, and exactly one batch exists.
- Base: Redis is down or flushed and behavior is unchanged because no correctness state lives there.
- Bad: scheduling based only on an in-memory set, succeeding without a token predicate, recovering every PROCESSING row at startup, or reverting by current `capture_input_id` rows without an immutable ledger.

### 6. Tests Required

- Use independent transactions and a synchronization barrier for concurrent create and retry.
- Assert one owner, one token, and one successful batch for a shared request ID.
- Force an old token to attempt success and failure after a new owner; assert rollback/no overwrite.
- Test valid lease, expired lease, and legacy null lease recovery with a controllable clock.
- Test successful/repeated revert plus conflicts after record edit/delete and task edit/complete/reopen/delete.
- Inject a revert sub-write failure and assert the input and every generated entity remain unchanged.
- Assert normal record/task queries exclude reverted entities while history/ledger remains.
- Run migrations from V1, V5 legacy data, and an empty schema to latest; verify legacy ledger backfill and `revertible=false`.

### 7. Wrong vs Correct

#### Wrong

```java
if (processingIds.add(inputId)) {
    process(inputId); // process-local ownership only
}
```

#### Correct

```sql
UPDATE capture_inputs
SET status='PROCESSING', processing_token=:token, lease_expires_at=:lease
WHERE id=:id AND status='FAILED';
```

```sql
UPDATE capture_inputs
SET status='SUCCEEDED', processing_token=NULL, lease_expires_at=NULL
WHERE id=:id AND status='PROCESSING' AND processing_token=:token;
```

Acquire and fence ownership in PostgreSQL, then require that token on every terminal write.

---

## Scenario: Daily report versions and immutable source snapshots

### 1. Scope / Trigger

Use this contract for daily-report source selection, generation requests, report history, edits, and any consumer that displays evidence for generated text. A report is a versioned artifact with frozen evidence, not a live projection that changes when source rows change.

### 2. Signatures

- `POST /api/reports { requestId, reportType?: "DAILY", date? } -> 201 ReportResponse`
- `GET /api/reports?date=YYYY-MM-DD -> ReportResponse[]`
- `GET /api/reports/{id} -> ReportResponse`
- `PATCH /api/reports/{id} { content, version } -> ReportResponse`
- `ReportStatus = PROCESSING | SUCCEEDED | FAILED`
- `report_sources(report_id, source_type, entity_id, content, project/projectName, source_status, source_time, snapshot)`

### 3. Contracts

- Each generation request creates a new report row. Multiple DAILY versions for the same date are allowed; `request_id` alone is unique.
- Reusing a request ID for the same date/type returns the existing report and does not call the gateway again. Reusing it for another date/type returns 409.
- The owner transaction creates `PROCESSING` and freezes all source snapshots before the model call. The model runs outside the transaction.
- Daily record sources are active work records in the Asia/Shanghai interval `[dayStart, nextDayStart)`. Inactive records, including reopened/deleted completion facts and reverted capture rows, are excluded.
- Plan sources are non-deleted `PENDING` tasks whose `due_at` is inside that same interval. Completed tasks are represented only by their active completion work record, preventing duplicate facts.
- Source snapshot text, project identity/name, status, time, and structured metadata never change after insertion, even if the source is edited, archived, invalidated, or deleted later.
- Success requires the processing token and writes content/status/version. Failure affects only the new report row and never overwrites an older successful or edited report.
- Content edits are allowed only for `SUCCEEDED`, require the current version, increment it, and set `edited_at`.
- Generated source markers describe the AI-generated content. Once a user edits the body, the UI must state that source markers are not automatically recalculated and must not claim that new user text has AI evidence.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Same request ID, same DAILY date | Return existing version; one gateway call |
| Same request ID, different date/type | HTTP 409 |
| Empty source set | Succeed with deterministic empty-section text; no fabricated bullets |
| Model/source validation failure | New row becomes FAILED; older reports unchanged |
| PATCH missing/negative version | HTTP 400 |
| PATCH stale version or non-SUCCEEDED report | HTTP 409 |
| Source changes after generation | Existing report_sources and content remain unchanged |

### 5. Good / Base / Bad Cases

- Good: generate a report, edit the underlying record, and still inspect the original snapshot and `[来源 N]` mapping in the saved report.
- Base: no sources yields “暂无记录” and “暂无已安排计划” without calling or trusting the model.
- Bad: enforcing one report per date, joining live source text when reading history, including inactive completion records, or updating the latest report row in place for regeneration.

### 6. Tests Required

- Test the Asia/Shanghai day boundary and historical-date backfill with fixed instants.
- Test empty, plan-only, progress-only, active completion, invalid completion, and reverted source cases.
- Generate, mutate/delete the source, and assert the report snapshot is unchanged.
- Test same-request concurrency with independent transactions; assert one report and one gateway invocation.
- Test multiple request IDs on one date, failed regeneration isolation, successful edit, stale edit conflict, and missing version.
- Run legacy reports through V8 and an empty schema through all migrations, confirming the old per-period unique constraint is removed safely.

### 7. Wrong vs Correct

#### Wrong

```sql
SELECT wr.content
FROM report_sources rs
JOIN work_records wr ON wr.id = rs.entity_id;
```

#### Correct

```sql
SELECT content, project_name, source_status, source_time, snapshot
FROM report_sources
WHERE report_id = :reportId
ORDER BY source_time, id;
```

Read report evidence from the immutable snapshot, never from mutable live source rows.
