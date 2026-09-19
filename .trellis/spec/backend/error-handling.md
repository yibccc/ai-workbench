# Error Handling

## Overview

REST errors use Spring `ProblemDetail` and the `application/problem+json` media type. Clients must receive a safe, actionable `detail`; an empty response body is not an acceptable business-error contract.

## Error Types

- Use `ResponseStatusException` for expected resource and business-state failures in the current service layer.
- Use `DeepSeekNotConfiguredException` only for the explicit missing-credential case.
- Let Jakarta Validation raise `MethodArgumentNotValidException` for request-body validation.
- Preserve database exceptions as causes, but translate expected constraint conflicts before they cross the API boundary.

## Error Handling Patterns

`ApiExceptionHandler` owns API-wide translation:

- `ResponseStatusException` -> same HTTP status plus a `ProblemDetail` containing its safe reason.
- `MethodArgumentNotValidException` -> HTTP 400 plus the first field-validation message.
- Unexpected infrastructure or model failures must not be mislabeled as configuration absence.

Do not return raw SQL, JDBC URLs, credentials, model keys, or full upstream response bodies in `detail`.

## API Error Responses

Expected response fields follow Spring's problem-details format:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "归档项目不能用于新记录",
  "instance": "/api/records"
}
```

The frontend must read `detail` when available and fall back to an HTTP-status message when the body is absent or malformed.

## Common Mistakes

- Returning a correct 400/409 status with an empty body, leaving the UI unable to explain the failure.
- Catching a broad exception type and classifying every failure as the same business condition.
- Exposing exception messages from untrusted SDK, SQL, or infrastructure boundaries without sanitization.
- Treating a database constraint violation as success because the UI already performed validation.

## Required Tests

- Assert both status and `detail` for validation and business conflicts.
- Assert missing resources return 404.
- Assert missing DeepSeek configuration returns 503 while other model failures return 502.
- Exercise at least one real database constraint so exception translation is not only mocked.
