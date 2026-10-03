# 本任务数据库规范摘录

来源：`.trellis/spec/backend/database-guidelines.md`，当前 SHA-256 e111cc491b7c9d2bdcac7cbf4e268080f484ffaebe408e4d7fc4f5dc6a4c39df。

原文件38126 bytes超过原生单文件注入上限32768；这里只摘与本任务直接相关的既有规范，内容原样保留，不修改源规范。正式父design覆盖本研究中的旧业务候选；本摘录不授权旧版兼容处理。实施/检查若修改迁移测试或数据库合同，仍须用文件工具完整读取源规范（不能把截断尾部视为已读），并确认SHA未变。

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
- Active project names are case-insensitively unique **per `user_id`** through the partial index `uq_projects_active_name`. Different users may use the same name. See [Identity and Isolation](identity-isolation.md) for the owner contract.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Blank/oversized project name | HTTP 400 problem detail |
| Duplicate active project name for the same user, case-insensitive | HTTP 409, `同名的活动项目已存在`; another user's same name is allowed |
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
- Test active-name uniqueness within one user and the same name across two users, archived-project rejection, archived historical association retention, same-owner foreign keys, and FK `RESTRICT` behavior.
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
- Run V1/V5 legacy rows through their historical recovery migrations and verify ledger backfill plus `revertible=false`; an empty schema must reach latest. V14 intentionally rejects nonempty legacy business rows with no trustworthy `user_id`, so test that rejection separately instead of inventing an owner for the historical fixture.

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
## Pooled connection isolation in migration tests

Migration tests that temporarily use `SET search_path` must restore the borrowed connection's original schema in `finally`, before Hikari can return that connection to another test. `SingleConnectionDataSource(connection, true)` closes only the wrapper; it does not reset PostgreSQL session state. Without restoration, later owner-scoped tests may read a dropped temporary schema and report missing tables or fail `current_schema()` guards depending on test order.

```java
try (Connection connection = dataSource.getConnection();
     Statement statement = connection.createStatement()) {
    String originalSchema = connection.getSchema();
    statement.execute("SET search_path TO " + temporarySchema);
    try {
        runMigrationAssertions(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
    } finally {
        connection.setSchema(originalSchema);
    }
}
```

Run the entire `clean verify` suite after changing a pooled-connection migration helper; a targeted migration test alone cannot reveal cross-test contamination.
