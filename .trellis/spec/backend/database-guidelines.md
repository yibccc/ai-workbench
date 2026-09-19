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
