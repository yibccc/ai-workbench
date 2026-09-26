# Linux Compose Deployment

## 1. Scope

Default Compose builds and starts frontend, backend, PostgreSQL and Redis. Local Windows development starts postgres/redis and runs application processes separately. Nginx serves static assets and proxies HTTP/STOMP; Spring Security owns invited-user authentication and authorization. Use this contract when changing Compose, Dockerfiles, Nginx, bootstrap settings, HTTPS proxying or the isolated deployment smoke check.

## 2. Commands and configuration

- `docker compose up -d --build --wait` starts the complete stack.
- `docker compose up -d --wait postgres redis` retains local developer infrastructure mode.
- `APP_BIND` defaults to `127.0.0.1`; `APP_PORT` defaults to `8088`.
- `WORKBENCH_BOOTSTRAP_USERNAME` / `WORKBENCH_BOOTSTRAP_PASSWORD` create one ADMIN only when `user_accounts` is empty; existing accounts are never reset by changed startup settings.
- `WORKBENCH_COOKIE_SECURE=true` is required behind public HTTPS. Local loopback HTTP development may use false. `WORKBENCH_SESSION` is HttpOnly/SameSite=Lax; production adds Secure. The separate `XSRF-TOKEN` cookie supplies `X-XSRF-TOKEN` for protected HTTP writes and STOMP CONNECT.
- `WORKBENCH_WS_ALLOWED_ORIGINS` lists exact browser HTTP(S) origins; container defaults use port 8088, host development defaults use 5173/15173.
- `WORKBENCH_AUTH_USER` / `WORKBENCH_AUTH_PASSWORD` and the htpasswd entrypoint are removed; there is no second shared login.

## 3. Contracts

- Multi-stage backend uses Java 17 and non-root runtime. Its container binds internally to 0.0.0.0:8080, with no published backend port. This is the intentional exception to host-development loopback binding.
- The backend Docker build uses `backend/maven-settings-aliyun.xml` to mirror remote Maven repositories through Aliyun public. Local Maven can opt into the same settings with `mvn -s maven-settings-aliyun.xml`; keep settings free of credentials and do not copy `.env` into build layers.
- Backend connects to service names postgres:5432 and redis:6379 regardless of host port mappings. PostgreSQL is the business authority; Redis holds sessions. V14 ownership migration deliberately refuses nonempty legacy business tables with no trustworthy owner. Verify the intended target and recovery snapshot before any one-time test-data removal; never assign those rows to ADMIN or clear them on startup.
- Multi-stage frontend serves Vite production assets using Nginx. The Docker build context excludes environment secrets, backups, logs, runtime state, dependencies and local build output.
- The static login page is anonymous. Backend Security returns 401 for unauthenticated business API calls, 403 for insufficient admin permission, and 404 for another user's business resource. Native STOMP `/ws/events` needs a live application Session, exact Origin and CONNECT CSRF; Nginx is only the proxy. Bootstrap credentials are backend runtime-only and never enter frontend assets or Docker build arguments.
- Health checking is restricted to the container's loopback health listener; public actuator paths are hidden. Bare /api and /ws return explicit 404 rather than SPA HTML.
- Proxy WebSocket Upgrade/Connection and original Host correctly. Permit only explicit configured origins; no wildcard fallback.
- HTTP defaults to a loopback ingress suitable for an SSH tunnel. For an explicitly selected temporary IP-direct HTTP entry, set `APP_BIND=0.0.0.0`, `WORKBENCH_COOKIE_SECURE=false`, and the exact `http://IP:port` WebSocket Origin; verify access from the intended network and record that HTTP does not encrypt credentials or Session traffic. Move to an outer domain/TLS proxy when a certificate is available, forwarding original Host/protocol and WebSocket upgrade, with `WORKBENCH_COOKIE_SECURE=true` and the exact HTTPS Origin. Compose itself does not supply TLS.

## 4. Errors

| Condition | Expected result |
|---|---|
| Empty user table with valid bootstrap config | One enabled ADMIN created; business space empty |
| Existing user with changed bootstrap config | No account added, overwritten or reset |
| Anonymous page / business API / STOMP | Login page visible; API 401; no usable business subscription |
| USER admin API / A requests B's business UUID | 403 / 404, with safe ProblemDetail |
| Missing/forged CSRF or invalid Origin | Protected write or STOMP connection rejected |
| V14 sees unowned legacy business rows | Migration stops with actionable error; no deletion or guessed owner |
| Native Windows old .env with only database settings | postgres/redis-only startup still works |
| Docker registry/network unavailable | Report build blocked; config parsing is not runtime evidence |

## 5. Cases

Good: a fresh isolated stack starts with synthetic bootstrap ADMIN, allows invited USER app login through HTTPS, keeps API/STOMP private and survives restart with accounts/data intact. Base: Windows loopback users keep Vite/JAR and only start dependencies, then use the same app login. Bad: publish backend directly, copy .env into an image, disable CSRF, permit all WebSocket origins, leave a second Basic Auth layer, or connect an old ownerless binary to multiuser data.

## 6. Verification

Use a separate Compose project, verified unused ports and new volumes. Run `docker compose config --quiet`, build the actual Dockerfiles, verify health, app login, API 401/403/404, CSRF, native STOMP, exact Origin, proxy Upgrade, production Secure/HttpOnly/SameSite cookie flags and restart persistence. Recheck Windows startup and README/environment template; old shared credentials must not remain in scripts or smoke assertions. Keep real model keys empty. Record project/volume names, commands, exit codes and external network failures; clean up only the verified new project resources, never the existing `ai-workbench` volumes.

## 7. Wrong vs Correct

Wrong: treat Nginx Basic Auth or a broad Origin as a substitute for application identity.

```nginx
auth_basic "AI Workbench";
location /ws/ { proxy_pass http://backend:8080; }
```

Correct: proxy native STOMP Upgrade while the backend validates the Session, exact Origin, CONNECT CSRF and the user's private subscription.

```nginx
location /ws/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Proto $forwarded_proto;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
}
```
