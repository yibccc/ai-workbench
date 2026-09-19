# Runtime Integration Contract

## Scenario: Local infrastructure and runtime status

### 1. Scope / Trigger

Use this contract when changing local PostgreSQL or Redis wiring, DeepSeek configuration, the runtime status endpoints, or the frontend status page. These boundaries must stay aligned across `compose.yaml`, Spring configuration, backend responses, tests, and frontend types.

### 2. Signatures

- `GET /api/status -> WorkbenchStatus`
- `POST /api/ai/probe -> 200 | 502 | 503`
- `GET /actuator/health -> Spring Boot Actuator health payload`
- `WorkbenchStatus(String application, Instant checkedAt, Map<String, ProbeStatus> components, Map<String, String> versions)`
- `ProbeStatus(String status, String detail)`

`GET /api/status` is observational and must not make a paid model request. `POST /api/ai/probe` is the only D1 endpoint that performs a live DeepSeek call.

### 3. Contracts

#### Runtime environment

| Key | Required | Default / meaning |
|---|---:|---|
| `POSTGRES_HOST` | No | `localhost` |
| `POSTGRES_PORT` | No | `5432` |
| `POSTGRES_DB` | No | `ai_workbench` |
| `POSTGRES_USER` | No | `ai_workbench` |
| `POSTGRES_PASSWORD` | No for local development | `local_dev_only`; never use this default for a deployed environment |
| `DATABASE_URL` | No | Overrides the composed JDBC URL entirely |
| `REDIS_HOST` | No | `localhost` |
| `REDIS_PORT` | No | `6379` |
| `DEEPSEEK_API_KEY` | Required only for a live AI probe | No default; backend only |
| `DEEPSEEK_BASE_URL` | No | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | No | `deepseek-flash` |

Spring Boot does not automatically load the repository-root `.env`. Local PowerShell startup must explicitly import it into the child process. The frontend must never read or forward the API key.

#### Status payload

`components` uses the stable keys `backend`, `postgres`, `redis`, and `deepseek`. Each value has:

- `status`: `UP | DOWN | NOT_CONFIGURED | NOT_CHECKED`
- `detail`: human-readable diagnostic text that must not contain credentials

The frontend mirrors these values in its `ComponentState` union. New states require coordinated backend, frontend, and test changes.

Local listeners are loopback-only: Spring binds to `127.0.0.1`, and Compose publishes PostgreSQL and Redis as `127.0.0.1:<host-port>:<container-port>`.

### 4. Validation & Error Matrix

| Condition | HTTP / component result | Required behavior |
|---|---|---|
| PostgreSQL connection validates | `components.postgres.status=UP` | Detail confirms connection acceptance |
| PostgreSQL fails or times out | Status endpoint remains HTTP 200; component is `DOWN` | Do not fail the entire status response |
| Redis returns `PONG` | `components.redis.status=UP` | Detail confirms PING/PONG |
| Redis fails or returns another value | Status endpoint remains HTTP 200; component is `DOWN` | Do not fail the entire status response |
| DeepSeek key absent on status read | `NOT_CONFIGURED` | No model call |
| DeepSeek key present on status read | `NOT_CHECKED` | No model call and no claim that the model is healthy |
| Manual AI probe succeeds | HTTP 200, `status=UP` | Include `checkedAt` and model response |
| Manual AI probe lacks a key | HTTP 503, `status=NOT_VERIFIED` | Throw/catch `DeepSeekNotConfiguredException` only |
| Manual AI probe has an SDK/network/model failure | HTTP 502, `status=DOWN` | Return a sanitized exception class, not secrets or raw credentials |

### 5. Good / Base / Bad Cases

- Good: Compose containers are healthy on loopback, `/api/status` reports PostgreSQL and Redis `UP`, and DeepSeek is `NOT_CHECKED` until the user explicitly calls the probe.
- Base: DeepSeek is not configured; the application still starts, storage checks work, and the manual probe returns 503 without exposing configuration values.
- Bad: Treating any `IllegalStateException` as a missing credential, reporting DeepSeek `UP` because a key exists, exposing ports on `0.0.0.0`, or assuming `.env` is loaded automatically.

### 6. Tests Required

- MVC contract test: assert `/api/status` returns `application` and component status fields.
- Missing-key test: assert `POST /api/ai/probe` returns HTTP 503 and `NOT_VERIFIED` only for `DeepSeekNotConfiguredException`.
- Runtime-failure test: assert other model exceptions return HTTP 502 and `DOWN` with sanitized detail.
- Configuration test: assert blank DeepSeek keys are not configured and defaults bind correctly.
- Build gate: run `mvn clean verify`, `npm ci`, `npm run lint`, and `npm run build`.
- Infrastructure gate: run `docker compose config --quiet`, verify both container health checks, then assert `/api/status` and `/actuator/health` report storage dependencies `UP`.
- Security check: confirm listeners bind only to `127.0.0.1`, `.env` is ignored, and no real key appears in tracked files or frontend output.

### 7. Wrong vs Correct

#### Wrong

```yaml
ports:
  - "5432:5432"
```

```java
catch (IllegalStateException exception) {
    return notVerified();
}
```

These patterns expose the database on every interface and misclassify unrelated model failures as missing configuration.

#### Correct

```yaml
ports:
  - "127.0.0.1:${POSTGRES_PORT:-5432}:5432"
```

```java
catch (DeepSeekNotConfiguredException exception) {
    return notVerified();
} catch (RuntimeException exception) {
    return badGateway();
}
```

Keep the missing-configuration path explicit and every local development listener loopback-only.
