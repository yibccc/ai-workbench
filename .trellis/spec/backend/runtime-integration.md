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
| `DEEPSEEK_TIMEOUT` | No | `PT4M`; must be a positive duration shorter than the capture lease |

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
- Both extraction and manual probe use bounded reactive waits with `DeepSeekProperties.requestTimeout()`; never call an unbounded `blockLast()`.
- `workbench.capture.lease-duration` defaults to `PT5M` and must be strictly greater than the DeepSeek timeout. Invalid startup configuration fails fast.
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

---

## Scenario: Source-grounded report generation

### 1. Scope / Trigger

Use this contract for AI-generated daily or weekly report text. The gateway formats candidate snapshots, but the backend owns source authorization and rejects any unsupported model claim.

### 2. Signatures

- `ReportAiGateway.generate(LocalDate date, ZoneId zoneId, List<ReportSourcePrompt> sources) -> AiReportResult`
- `AiReportResult.sections[] = { type, bullets[] }`
- `bullet = { text, sourceIds[] }`
- Daily section types: `ACHIEVEMENTS | PROGRESS | PLANS`

### 3. Contracts

- Serialize date, zone, and frozen report-source IDs/content into one untrusted `input_data` JSON object.
- Decode the whole model response strictly: reject unknown properties, trailing tokens, scalar coercion, malformed JSON, and incomplete Markdown fences.
- Every nonempty AI bullet requires at least one source ID. Each ID must belong to this report snapshot.
- For daily reports, ACHIEVEMENTS/PROGRESS may reference only RECORD sources; PLANS may reference only TASK sources.
- Reject duplicate section types and duplicate source references according to the report policy. Enforce global bullet and text-length limits.
- Render stable `[来源 N]` markers from the persisted source ordering so users can inspect each generated claim.
- Missing sections use deterministic backend placeholders. The model must not invent text for a section without evidence.
- The DeepSeek timeout and sanitized error rules from the strict extraction boundary also apply here.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Bullet has no source IDs | Reject the report result |
| Unknown/out-of-snapshot source ID | Reject; mark this report FAILED |
| RECORD used in PLANS or TASK used in fact sections | Reject |
| Duplicate section/source reference | Reject |
| Missing section | Render deterministic empty placeholder |
| Complete single JSON fence | Unwrap and strictly decode |
| Incomplete fence, explanation, or trailing payload | Reject |

### 5. Good / Base / Bad Cases

- Good: each generated bullet ends with source markers that resolve to the frozen source list.
- Base: one section has no evidence and is rendered by the backend as an explicit empty state.
- Bad: accepting prose around JSON, silently dropping source IDs after validation, or treating manually edited sentences as automatically source-backed.

### 6. Tests Required

- Parser tests for strict JSON, complete/incomplete fences, unknown fields, trailing tokens, and coercion.
- Validation tests for missing, unknown, duplicate, and cross-type source IDs.
- Render tests assert every generated bullet maps to stable source numbers and empty sections use exact placeholders.
- Persistence tests assert invalid model output fails only the new report and older reports remain unchanged.
- Real-model acceptance is separate from regression; record sanitized evidence once unless the gateway path changes materially.

### 7. Wrong vs Correct

#### Wrong

```java
String body = model.generate(sources).text();
reportRepository.save(body); // source references discarded
```

#### Correct

```java
AiReportResult result = gateway.generate(date, zone, frozenSources);
ValidatedReport validated = validateSourceIds(result, frozenSources);
String body = renderWithStableSourceMarkers(validated, frozenSources);
```

Validate authorization first, preserve the mapping, then persist the rendered report.

---

## Scenario: Weekly report model limits and role validation

### 1. Scope / Trigger

Use this contract when generating WEEKLY model prompts or changing report-specific model limits. Report calls have different latency/output needs from capture and must not weaken capture fencing.

### 2. Signatures

- `ReportAiGateway.generateWeekly(periodStart, periodEnd, zoneId, sources) -> AiReportResult`
- `REPORT_AI_TIMEOUT` / `workbench.report.ai.timeout`, default `PT6M`
- `REPORT_AI_MAX_TOKENS` / `workbench.report.ai.max-tokens`, default `4096`
- Capture remains governed by `DEEPSEEK_TIMEOUT=PT4M` and the longer `PT5M` processing lease.

### 3. Contracts

- Weekly prompt sections are fixed: ACHIEVEMENTS, PROGRESS, PLANS.
- Role authorization is enforced again in backend validation, never only in prompt text.
- Set low temperature and a bounded report response-token limit.
- Use the report-specific timeout for daily/weekly report generation. Do not raise the global DeepSeek capture timeout to accommodate large reports.
- Serialize every frozen source included in the report; never silently truncate, sample, or drop evidence to manufacture a successful response.
- A large source set that exceeds the current single-call strategy becomes FAILED with preserved snapshots and a retry/regeneration path.
- Future batching must retain end-to-end source IDs through intermediate summaries; lossy truncation is not an acceptable fix.

### 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| WEEK_RECORD in ACHIEVEMENTS | Allowed |
| WEEK_RECORD or CURRENT_TASK in PROGRESS | Allowed |
| NEXT_WEEK_TASK in PLANS | Allowed |
| Any other role/section pairing | Reject report output |
| Same achievement uses multiple sources | Allowed only when all source IDs are retained |
| Report exceeds report-specific timeout | New version FAILED; older versions unchanged |
| Invalid timeout/token config | Use documented safe defaults; never make capture lease invalid |

### 5. Good / Base / Bad Cases

- Good: a merged completion bullet references all corresponding frozen facts, and manual notes remain outside the AI body.
- Base: a small report completes within the bounded call and missing sections use deterministic placeholders.
- Bad: truncating 96 sources to the first N, increasing `DEEPSEEK_TIMEOUT` beyond the capture lease, or retrying a paid call automatically after a timeout.

### 6. Tests Required

- Prompt tests assert weekly boundaries, role instructions, untrusted input serialization, timeout, temperature, and max-token options.
- Construct at least 96 sources locally and assert prompt generation is bounded in time and preserves every UUID; this is not proof of real-model latency.
- Validation tests cover every allowed and forbidden role/section pair plus multi-source bullets.
- Timeout tests use a fake gateway and assert FAILED isolation without automatic paid retry.
- Record real-model latency/failure evidence separately. Do not claim PT6M success until explicitly revalidated.

### 7. Wrong vs Correct

#### Wrong

```java
List<Source> promptSources = sources.stream().limit(50).toList();
```

#### Correct

```java
AiReportResult result = reportGateway.generateWeekly(start, end, zone, frozenSources);
// If single-call scale is insufficient, fail honestly and design a source-preserving batch pipeline.
```

Keep complete evidence or fail explicitly; never hide omitted facts.
