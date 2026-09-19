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
