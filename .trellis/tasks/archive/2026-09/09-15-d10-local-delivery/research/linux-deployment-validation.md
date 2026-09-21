# Linux Docker deployment validation

Date: 2026-09-21. Real model calls: 0.

## Implemented

- Root Compose starts four services with health-dependent startup, restart policy, original PostgreSQL/Redis images and named volumes preserved.
- Backend Java 17 multistage image runs as nonroot, container-only port 8080; frontend Node 24 build and Nginx runtime serve SPA, API, authenticated WebSocket.
- Runtime Basic Auth fails closed for missing credentials; password goes through stdin to bcrypt htpasswd, not shell interpolation. Newline passwords are rejected. Health is on separate container-loopback 8081 listener.
- Exact configurable WebSocket origins reject wildcards, paths, userinfo, query and fragment. Default host development origins preserved.
- `.dockerignore` excludes secrets, dependencies, backups and workspace metadata. Shell files have LF checkout rules.
- README documents Linux deployment, SSH tunnel, external HTTPS proxy, update, stop, backup and isolated restore; Windows dependency commands now select postgres/redis only.

## Passed

- `docker compose --env-file .env.example config --quiet`.
- WSL `sh -n deploy/40-workbench-auth.sh`.
- Maven `WorkbenchWebSocketConfigTest` (explicit origins and malformed input), then rerun after trailing-comma guard.
- `git diff --check` (line-ending informational warnings only).

## Initial registry failures (resolved)

Actual `docker compose -p d10deployvalidation --env-file .env.example build` failed resolving all four base images: daemon mirror `docker.m.daocloud.io` returned HTTP 401.
Explicit official `registry-1.docker.io/library/...` pulls for all four base images then timed out at daemon network access. WSL shell curl can reach the official registry, but this does not give the Docker daemon a working connection.

No daemon configuration was changed or daemon restarted. No deployment containers/volumes were created. Existing business containers/databases were untouched.

These attempts stopped before image build and runtime verification. Subsequent successful checks are recorded below. External HTTPS requires the deployment domain, DNS and certificate configuration.

## Official references

- https://nginx.org/en/docs/http/websocket.html
- https://docs.spring.io/spring-framework/reference/web/websocket/server.html

## Additional official registry attempt

AWS ECR Public distributes Docker Official Images (https://aws.amazon.com/blogs/containers/docker-official-images-now-available-on-amazon-elastic-container-registry-public/). Pulling `public.ecr.aws/docker/library/` nginx, node and maven succeeded, then these were locally tagged to the original Dockerfile references. No daemon/global configuration changed.

The original frontend Dockerfile successfully built including npm ci/build and Alpine packages. Image `d10deployvalidation-frontend` SHA256 `460aa7595ca36ed738bfd0fea35a9368852937178fde4f0154f411c37a7b3ea9` contains the reviewed 72-byte password guard and exact `/api` and `/ws` exclusions. Running its auth entrypoint without credentials returned exit 1 as expected.

## Final successful deployment checks (supersedes initial blockers)

Temurin succeeded on a bounded second ECR download. Backend built from the original Dockerfile, Maven `BUILD SUCCESS` in 5m28s; image SHA256 `242f9d13fb1f7b55495cb5b3b0077ddf49cfeb2d903f806960545210f28cba59`. No Docker daemon configuration changes were needed.

`wsl -d Ubuntu --cd /mnt/e/projects/workbench -- python3 -u scripts/local/verify-compose.py` exited **0** and printed:

```text
Four services healthy
Authentication, SPA/API routing and WebSocket origin checks passed
Restart persistence passed
12
```

- Only `d10deployvalidation` created: separate PostgreSQL/Redis volumes, host ports 15439/16389/18088, random runtime credentials, empty AI key.
- Empty, 73-byte and newline passwords rejected by image entrypoint. Unauthenticated and wrong-auth page/API/WS requests returned 401; authenticated page and SPA routes returned 200; unknown API and actuator paths 404.
- Authenticated correct-Origin WebSocket handshake 101, malicious-Origin 403, unauthenticated missing-Origin 401; authenticated non-browser missing-Origin 101 per Spring behavior.
- Project created via real API, backend restarted, same UUID re-read; clean database migrated through V12.
- A first smoke attempt inherited WSL HTTP proxy and timed out sending localhost requests through it; fixed the test client to explicitly bypass proxies, rerun passed. Both attempts cleaned only their owned temporary resources.
- Script refuses existing project containers/volumes and cleans its exact isolated project in `finally`. Original ai-workbench services/database unchanged; zero paid model requests.
- Frontend image contains no `.env` or node_modules in served paths; backend runtime UID verified nonroot. Context excludes secrets and local backups.

Pending deployment checks: domain HTTPS certificate/DNS and browser-specific Basic Auth cache reuse at the actual entrypoint. Protocol-level authenticated WebSocket passed.
