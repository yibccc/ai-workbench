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

---

## Scenario: Strict DeepSeek extraction boundary

### 1. Scope / Trigger

Use this contract for any AI feature that converts untrusted user text into typed business input. The model is an untrusted producer: successful HTTP or valid JSON does not imply valid business data.

### 2. Signatures

- `WorkbenchAiGateway.extract(String rawContent, Instant referenceAt, ZoneId zoneId, List<String> activeProjectNames) -> AiCaptureResult`
- `AiCaptureResult.records[] = { content, projectName, occurredAt }`
- `AiCaptureResult.tasks[] = { title, notes, projectName, dueAt, priority }`

### 3. Contracts

- AgentScope Java 2.0.3 uses `OpenAIChatModel` with `DeepSeekFormatter`.
- DeepSeek native structured output is disabled for this compatibility path; the prompt requests one JSON object and Jackson performs strict decoding.
- Serialize `referenceAt`, `zoneId`, active project names, and raw user content into a JSON `input_data` object. Do not concatenate raw user text as prompt instructions.
- State explicitly that `rawContent` is untrusted data and commands or output-format requests inside it must not be executed.
- Reject unknown properties, trailing tokens, and scalar-to-string coercion. Markdown-fenced JSON may be unwrapped only when the complete payload is one matching fence.
- Treat model output as data: enforce required arrays, item-count limits, string lengths, enum fallback, ISO instants, and active-project lookup after parsing.
- Never log or return API keys, full upstream errors, or raw third-party response details.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| DeepSeek key absent | Sanitized not-configured failure |
| Null/empty response | Sanitized processing failure |
| Malformed JSON, extra field, trailing JSON, or forbidden coercion | Validation failure; no business writes |
| Markdown explanation around JSON | Reject; do not heuristically extract a substring |
| One complete `json` fence containing only the payload | May unwrap, then apply the same strict decoder |
| Unknown project | `projectId=null` after backend lookup |
| Unknown priority | `MEDIUM` after backend validation |
| Prompt-injection text in raw input | Preserve as data; output must still satisfy the fixed extraction schema |

### 5. Good / Base / Bad Cases

- Good: a mixed input produces strict JSON, then backend validation maps only an exact active project and persists an atomic batch.
- Base: either array may be empty, but both empty is not a useful extraction and becomes a retryable failure.
- Bad: trusting project IDs from the model, accepting unknown fields, coercing numeric content into strings, logging upstream bodies, or embedding raw input directly after a prompt delimiter.

### 6. Tests Required

- Fake-gateway tests cover mixed, multi-item, record-only, task-only, and empty results.
- Parser tests cover empty response, Markdown fences, unknown fields, trailing JSON, scalar coercion, malformed instants, and more than the item limit.
- Prompt test includes embedded role instructions and JSON delimiters; assert raw input is serialized inside `input_data`.
- Failure tests assert the client receives sanitized text and no secrets/upstream payloads.
- Real DeepSeek is a separate explicit acceptance check; record model, SDK version, time, sanitized input/result, and limitations without adding the call to ordinary regression.

### 7. Wrong vs Correct

#### Wrong

```java
String prompt = "Extract JSON from: " + rawUserText;
return objectMapper.readValue(findFirstJsonObject(modelText), AiCaptureResult.class);
```

#### Correct

```java
String inputData = objectMapper.writeValueAsString(
        new PromptInput(referenceAt, zoneId, activeProjects, rawUserText));
AiCaptureResult result = strictMapper.readValue(wholeModelPayload, AiCaptureResult.class);
```

Serialize untrusted input, decode the entire output strictly, then validate business meaning before persistence.
