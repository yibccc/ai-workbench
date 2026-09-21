# Linux Compose Deployment

## 1. Scope

Default Compose builds and starts frontend, backend, PostgreSQL and Redis. Local Windows development starts postgres/redis and runs application processes separately. Nginx provides single-user entry authentication.

## 2. Commands and configuration

- `docker compose up -d --build --wait` starts the complete stack.
- `docker compose up -d --wait postgres redis` retains local developer infrastructure mode.
- `APP_BIND` defaults to `127.0.0.1`; `APP_PORT` defaults to `8088`.
- `WORKBENCH_AUTH_USER` / `WORKBENCH_AUTH_PASSWORD` configure the single-user ingress.
- `WORKBENCH_WS_ALLOWED_ORIGINS` lists exact browser HTTP(S) origins; container defaults use port 8088, host development defaults use 5173/15173.

## 3. Contracts

- Multi-stage backend uses Java 17 and non-root runtime. Its container binds internally to 0.0.0.0:8080, with no published backend port. This is the intentional exception to host-development loopback binding.
- Backend connects to service names postgres:5432 and redis:6379 regardless of host port mappings. Existing database images, volume names and migrations remain compatible.
- Multi-stage frontend serves Vite production assets using Nginx. The Docker build context excludes environment secrets, backups, logs, runtime state, dependencies and local build output.
- Nginx Basic Auth covers the page, API and WebSocket entry points. Credentials are runtime-only; password goes through stdin into a restricted htpasswd file, never into build args or logged command strings.
- Missing/empty credentials, invalid usernames, multiline passwords and passwords beyond bcrypt's 72-byte boundary fail closed. Do not silently truncate authentication input.
- Health checking is restricted to the container's loopback health listener; public actuator paths are hidden. Bare /api and /ws return explicit 404 rather than SPA HTML.
- Proxy WebSocket Upgrade/Connection and original Host correctly. Permit only explicit configured origins; no wildcard fallback.
- HTTP defaults to a loopback ingress suitable for an SSH tunnel. Public exposure requires an outer domain/TLS proxy; document actual certificate setup rather than claiming it is supplied.

## 4. Errors

| Condition | Expected result |
|---|---|
| Missing deployment auth | Frontend entrypoint fails; no anonymous app |
| Unauthenticated/wrong credentials | 401 for protected routes |
| Invalid Origin | Backend handshake rejection |
| Native Windows old .env with only database settings | postgres/redis-only startup still works |
| Docker registry/network unavailable | Report build blocked; config parsing is not runtime evidence |

## 5. Cases

Good: fill .env once, build four services, access through authenticated loopback/HTTPS ingress. Base: local Windows users keep Vite/JAR and only start dependencies. Bad: publish backend directly, copy .env into an image, permit all WebSocket origins, or expose Basic Auth over public plaintext HTTP.

## 6. Verification

Use a separate Compose project, ports and volumes. Build original Dockerfiles, verify health/auth/API/SPA/WebSocket and restart persistence, then clean up only that project's resources. Keep real model keys empty during verification. Record any external network limitation and do not report unexecuted image tests as passing.

## 7. Wrong vs Correct

Wrong: connect backend to localhost or the host-mapped database port inside its container.

Correct: use postgres:5432 and redis:6379 on the Compose network, keeping host mappings independent.
