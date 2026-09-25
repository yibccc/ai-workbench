# Identity, Sessions, and Private Business Data

## 1. Scope / Trigger

Use this contract when changing account APIs, Spring Security/Session, a business Controller/Service/Mapper, AI or report background processing, Flyway ownership constraints, or STOMP notifications. The application serves multiple invited users; every business resource, including an ADMIN's, belongs to exactly one stable account ID. PostgreSQL remains the business authority; Redis holds sessions and explicit user-activity state.

## 2. Signatures

- `CurrentUser.requireId() -> UUID` obtains the authenticated stable owner for a user-originated business request. Async work must instead load `userId` from its persisted `capture_inputs` or `reports` row.
- `GET /api/auth/csrf -> {token, headerName}`; use `X-XSRF-TOKEN` on protected HTTP writes and STOMP CONNECT. `POST /api/auth/login {username,password} -> AccountResponse`; `GET /api/auth/me -> AccountResponse`; `POST /api/auth/logout -> 200`; `POST /api/auth/password {currentPassword,newPassword} -> AccountResponse`; `POST /api/auth/activity -> {active:true}`.
- `AccountResponse = {id: UUID, username, role: ADMIN|USER, enabled, createdAt}`. `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}/role`, `PATCH /api/admin/users/{id}/enabled`, and `POST /api/admin/users/{id}/reset-password` are ADMIN-only. See the current controller DTOs for exact field names.
- `/api/e2e/**` is ADMIN-only in Security and its reset controller exists only with `e2e & !live-acceptance`; the reset service additionally requires `current_schema() == d9_e2e`. It is a test fixture, never an anonymous or production management path.
- V13 adds `user_accounts(id, username, password_hash, role, enabled, auth_version, created_at, updated_at)`. V14 adds non-null `user_id` to `projects`, `capture_inputs`, `todo_items`, `work_records`, and `reports`; user-scoped project-name and request-ID uniqueness; composite same-owner foreign keys. V1–V13 remain immutable.
- `WorkbenchEventHub.publishAfterCommit(UUID ownerUserId, String kind, UUID entityId, String state)` delivers an INPUT/REPORT refresh signal through native STOMP `/ws/events` to `/user/queue/workbench-events`.

## 3. Contracts

### Account and session

Username is globally case-insensitive unique and immutable; do not invent a username length or character-set limit. Password creation, admin reset, and self-change share the 8–64 Unicode code-point rule and local weak-password denylist; PBKDF2 stores a one-way hash without bcrypt's 72-byte truncation. Failed login uses a finite Redis limiter (5 failures in 15 minutes, then 60-second wait), never disables the account, and successful login clears only that account's failures. Keep at least one enabled ADMIN with a transactional PostgreSQL administration lock.

Spring Session Redis indexed uses `WorkbenchPrincipal.getName() == userId.toString()` for revocation and STOMP routing. Login and account-changing admin actions share the PostgreSQL advisory transaction lock, so an admin change committed before login credential validation is observed by that login. `auth_version` invalidates old sessions after disable, reset, or role change; self-password change preserves only the current HTTP session. Session deletion alone does not prove an existing socket has stopped receiving events: close indexed sockets and check identity/version again before delivery.

Each independent HTTP session has a Redis active-time marker that is separate from Spring Session `lastAccessedTime`. Login initializes it; only `POST /api/auth/activity` caused by an explicit user action advances it. Background HTTP polling, STOMP reconnect/subscription/heartbeat, and push reception never advance it. At 604800 seconds after the last explicit action, reject before attempting to renew, including when framework session TTL was refreshed by automatic traffic. Missing/failed Redis activity state fails closed. `WORKBENCH_SESSION` is HttpOnly and SameSite=Lax; production HTTPS sets `WORKBENCH_COOKIE_SECURE=true` for Secure cookies. The script-readable XSRF cookie/token is not the session credential.

### Business and realtime ownership

Every list/count/detail/write/association query binds authenticated `userId`. A foreign UUID returns 404 before disclosing status, version conflicts, or content; ADMIN has no business-owner bypass. New owner comes from the server, never a request field. Project associations, generated records/tasks, task events, report sources/snapshots, idempotency request IDs, and weekly version locks/chains remain inside the same owner. Async AI/recovery/retry/report processing reads the persisted request/report owner, not a current thread's `SecurityContext`. V14 refuses nonempty legacy business tables because their owner cannot be proved; it never assigns a default ADMIN owner or clears rows.

STOMP uses the authenticated HTTP Session Principal, exact handshake Origin, and CONNECT CSRF. Only `/user/queue/workbench-events` may be subscribed to; direct broker queues, wildcard/foreign user destinations, and client SEND are denied. Delivery checks the persisted owner and each live session. Notifications are best-effort after commit; HTTP GET/database remain authoritative. Simple Broker supports the current single backend and does not provide offline replay.

## 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| Anonymous/expired business HTTP request | 401 ProblemDetail, no business content |
| Authenticated USER calls admin API | 403 ProblemDetail, no account mutation |
| A/ADMIN requests B's business UUID | 404, no B content/status or partial write |
| Duplicate case-insensitive username; same-owner active project name | 409; the same project name is allowed for different owners |
| Last enabled ADMIN demotion/disable | 409, including concurrent requests |
| Five failed logins in the configured window | 429 for the wait period; account remains enabled |
| Missing/forged HTTP or CONNECT CSRF, invalid Origin/destination/SEND | Protected action or subscription rejected |
| Authenticated USER calls `/api/e2e/reset` in an isolated E2E run | 403; no reset, even with a valid CSRF token |
| Redis session/explicit activity state unavailable | No authenticated business fallback; reject safely |
| Session reaches 604800 seconds without explicit action | Next request/subscribe rejected; no new event delivered |
| V14 sees legacy business rows without owner | Migration fails with an actionable empty-data message; no deletion or invented owner |

## 5. Good / Base / Bad Cases

- Good: A and B each use the same client request ID and project name, receive separate resources, and only their own committed INPUT/REPORT signal; A's async processing stays with A after logout.
- Base: one user has two sessions. Logging out one leaves the other usable; self-password change keeps the current HTTP session and reconnects its socket with a new Principal while revoking the other.
- Bad: filter only in a Controller, query by UUID without `user_id`, derive async owner from `SecurityContext`, refresh idle time on a 15-second poll, or treat `/user` destination syntax as authorization by itself.

## 6. Tests Required

- In a **fresh isolated PostgreSQL schema**, run all Flyway migrations; separately prove V14 refuses legacy rows without modifying them. Set `TEST_DATABASE_URL` and `WORKBENCH_TEST_SCHEMA` to the same approved test schema; `application-test.yml` binds both Flyway schemas from that test-only key. Do not globally override `spring.flyway.schemas` for a whole Maven run, because the `live-acceptance` profile has its own isolated schema. Never aim tests at `public` or an existing application volume.
- Use real HTTP/Redis tests for CSRF, login/limit, 401/403/404, password rules, multi-session revocation, admin race, and 604799/604800-second explicit activity boundaries. Verify automatic polling cannot renew. Assert both status and safe `ProblemDetail.detail`.
- With A/B/ADMIN and deterministic AI, assert owner filtering for every Mapper path, same-owner association constraints, user-scoped idempotency, async result/retry/recovery, report source snapshots and weekly chain concurrency. Preserve the database/pagination/report-deletion contracts in their own specs.
- Send real STOMP frames for exact Origin, CONNECT CSRF, subscription/SEND authorization, multi-session owner delivery, revocation, self-change reconnect, and post-commit/rollback behavior. Browser tests cover disconnect/reconnect GET and 15-second HTTP fallback.
- Run backend `mvn verify` and frontend lint/build/E2E against isolated services with no live model key. Record actual commands, exit codes, and source/schema under test.

## 7. Wrong vs Correct

Wrong: a UUID-only read or globally keyed async request silently crosses the owner boundary.

```sql
SELECT * FROM capture_inputs WHERE id = :id;
```

Correct: the caller supplies the authenticated or persisted owner, and SQL binds it before exposing resource state.

```sql
SELECT * FROM capture_inputs WHERE id = :id AND user_id = :userId;
```

For background completion, derive `userId` from the persisted input row before querying projects, writing generated items, or publishing `publishAfterCommit(userId, ...)`.
