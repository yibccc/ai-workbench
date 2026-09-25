# 认证与会话接口合同（实施版）

## HTTP 与 Cookie

所有请求同源发送 Cookie；除 `GET /api/auth/csrf`、`POST /api/auth/login`、`/actuator/health/**` 和 `/actuator/info` 外，后端默认要求登录。未认证或会话失效返回 HTTP 401 `application/problem+json`，无管理权限返回 403，参数错误返回 400，账号冲突与最后管理员约束返回 409。登录失败统一返回 401，连续 5 次失败后 15 分钟计数窗口内等待 60 秒（429）；成功仅清该账号失败状态。

- 首先 `GET /api/auth/csrf`，响应 `{ "token": "...", "headerName": "X-XSRF-TOKEN" }`，同时写可读的 `XSRF-TOKEN` Cookie。登录及全部 POST/PATCH/PUT/DELETE 请求均带 `X-XSRF-TOKEN`；缺失或伪造返回 403。令牌只在内存使用，不放 localStorage/sessionStorage。
- `POST /api/auth/login` 请求 `{ "username": "...", "password": "..." }`；成功 200 返回 `AccountResponse`，建立 `WORKBENCH_SESSION` Cookie。会话 Cookie 为 HttpOnly、SameSite=Lax；生产设置 `WORKBENCH_COOKIE_SECURE=true`，本地 loopback HTTP 可为 false。
- `GET /api/auth/me` 返回当前 `AccountResponse`。`AccountResponse` 字段为 `id: UUID`、`username: string`、`role: ADMIN|USER`、`enabled: boolean`、`createdAt: ISO instant`；无密码或摘要。
- `POST /api/auth/logout` 结束当前 Session，成功 200、空响应体；不影响同账号其他 Session。
- `POST /api/auth/password` 请求 `{ "currentPassword": "...", "newPassword": "..." }`，成功 200 返回 `AccountResponse`。当前密码错误为 400；成功后只保留当前 Session 的新认证版本，其他 Session 失效。
- `POST /api/auth/activity` 不需要业务请求体，成功 200 `{ "active": true }`。仅明确用户主动操作发送该受 CSRF 保护的信号；前端应限制频率，15 秒后台查询、自动重连/订阅、心跳、纯推送不得发送。到期请求先被拒绝，不能续活。

`/api/admin/users` 仅 ADMIN 可用：`GET` 返回 `AccountResponse[]`，`POST` 请求 `{ "username", "password", "role": "ADMIN"|"USER" }`，成功 201。`PATCH /api/admin/users/{id}/role` 请求 `{ "role": "ADMIN"|"USER" }`；`PATCH /api/admin/users/{id}/enabled` 请求 `{ "enabled": boolean }`；`POST /api/admin/users/{id}/reset-password` 请求 `{ "password": "..." }`；三者成功均 200 `AccountResponse`。禁用、管理员重置及实际角色改变会提高 `authVersion` 并撤销目标旧 Session；无账号删除、用户名修改接口。

`/api/e2e/**` 在应用安全链中仅允许 ADMIN；普通已登录 USER 即使带有效 CSRF，请求 `POST /api/e2e/reset` 仍返回 403 `ProblemDetail`。重置 Controller 本身仅在 `e2e & !live-acceptance` Profile 存在，服务还在执行前核对 `d9_e2e` schema。本地真实 HTTP 负例在隔离 `test` Profile 验证了安全匹配器，E2E Profile 的 ADMIN 正向路径由 Playwright fixture 验证。

登录验证与建会话处于同一个数据库事务，持有与管理员重置、禁用、改角色及自改密码共用的 PostgreSQL advisory lock。管理员变更先提交时，旧密码或已禁用账号的随后登录返回 401；登录事务先完成时可返回 200，但管理员后续变更提交后，该会话在下一次受保护请求被 `authVersion` 拒绝。Spring Session 可能在响应末尾才把会话写入 Redis，因此即使索引清理没有扫描到刚创建的会话，认证版本校验仍是必要门禁。

密码校验在创建、管理员重置和自改时共用：按 Unicode 码点数 8～64、允许空格和非 ASCII、拒绝本地常见弱密码表，PBKDF2 单向摘要且无 bcrypt 72 字节截断。用户名数据库 `lower(username)` 唯一；用户名不设额外长度或字符集限制，只拒绝空白。

真实 HTTP/Redis 验收确认：同一账号大小写变体累计 5 次失败，随后正确密码得到 429；失败计数键有效期约 900 秒、等待键约 60 秒。测试只删除该账号等待键模拟等待结束，成功登录仅清其计数，另一账号的失败计数仍保留。管理 API 创建大小写重复用户名返回 409。创建、管理员重置和本人自改均拒绝 7/65 Unicode 码点及本地弱密码，8/64 码点可用；错误当前密码或无效新密码不改变认证版本/当前会话，成功自改后旧密码失效、新密码可登录。生产 `server.servlet.session.cookie.secure=true` 时实际 `Set-Cookie` 中，`XSRF-TOKEN` 带 Secure/SameSite=Lax，`WORKBENCH_SESSION` 带 Secure/HttpOnly/SameSite=Lax。角色变化和可允许的自降级使目标全部旧会话失效；最后一名启用管理员自降级返回 409。USER 升为 ADMIN 后，旧 USER 会话失效，新登录才取得管理权限。

### TC-007：本地弱密码表与离线校验

本地 denylist 是本项目自定义 **v1**，来源为 `AccountService.WEAK_PASSWORDS` 中手工固定的 17 项常见口令：`password`、`password1`、`password123`、`12345678`、`123456789`、`1234567890`、`qwerty123`、`qwertyuiop`、`admin123`、`letmein123`、`welcome123`、`iloveyou`、`11111111`、`00000000`、`abc12345`、`passw0rd`、`workbench`。没有外部泄露口令库、在线信誉接口或网络查询；如需调整集合，须作为后续版本变更并更新固定样本。三入口共用同一服务端校验。

`scripts/local/verify-compose.py --skip-build --offline-password-check` 使用固定 `d10deployvalidation` 四服务与全新随机部署凭据。弱样本每轮均为 `password123`；合规样本分别为 `D10-Offline-Create-{1,2}-2026!`、`D10-Offline-Reset-{1,2}-2026!`、`D10-Offline-Self-{1,2}-2026!`，仅用于临时项目。两轮均检查：弱创建返回 400 且账号数不变；弱管理员重置返回 400 且摘要不变；弱自改返回 400 且摘要/当前会话不变；对应合规输入成功，重置与自改使摘要变化。密码流程前后数据库 `select 1`、Redis `PING` 可用。

可选模式通过临时 Compose override 将 backend、PostgreSQL、Redis 置于同一个 Docker `internal` 网络，frontend 额外连接默认网络以保持 loopback 入口。运行时检查 backend 仅连接该 internal 网络、没有默认 IP 路由，且对公网 IP `1.1.1.1` 的 HTTP TCP 探针失败；PG/Redis 仍可达。这证实密码操作在 backend 无可用公网出口时保持相同结果，**不能单凭该探针断言应用从未尝试建立外连或证明零 DNS 查询**。代码路径中的密码校验只查本地集合，未配置付费模型凭据。

可再加 `--strace-observe`，以 `wsl -d Ubuntu -u root -- python3 scripts/local/verify-compose.py --skip-build --offline-password-check --strace-observe` 执行；**复跑前须由 WSL root 提供可用的 `strace` 命令**，本轮临时安装的 strace 已在验证后卸载。脚本只附加 `docker inspect` 找到的固定 d10 backend 宿主 PID，在两轮密码操作期间用 `strace -f -s 0 -e trace=%network` 捕获短时网络调用；捕获文件置于权限受限的临时目录，`finally` 停止 strace 并删除文件，只输出计数，不打印负载或口令。2026-09-26 复跑退出码 0：记录网络类调用 18 次，其中显式 `connect/sendto/sendmsg` 等外发类调用 0 次、DNS 端口调用 0 次、外部目标地址 0 个。**该观测没有追踪 JVM 对既有 socket 使用的 `write/writev`，不能证明零字节发送或零全部外连尝试**；结合 internal 网络、无默认路由和失败的公网探针，只能确认运行期没有成功的 backend 公网出口。复跑后再次独立确认 d10 容器、卷、网络均为空，长期 `ai-workbench` PostgreSQL/Redis 仍在。

2026-09-26 在 WSL Ubuntu 运行上述命令，退出码 0：四服务健康、两轮密码流程通过，原认证/入口/持久化检查通过，Flyway 版本 14。首次重建镜像因 frontend `npm ci` 安装 esbuild 时 `ETXTBSY` 失败，未进入运行验证；随后确认 `d10deployvalidation-backend/frontend` 与本轮 `ai-workbench-backend/frontend` 镜像 ID 分别相同（`sha256:ac97a1e0…`、`sha256:c8299a67…`），再用 `--skip-build` 成功执行。脚本及事后独立检查均确认 `d10deployvalidation` 容器、卷、网络清空；长期 `ai-workbench` 的 PostgreSQL/Redis 容器仍在。

## 后端跨层调用

- `CurrentUser.requireId(): UUID`、`CurrentUser.require(): WorkbenchPrincipal` 从 SecurityContext 获取当前稳定账号 ID；业务 API 不接受请求参数指定 owner。
- `WorkbenchPrincipal(userId: UUID, role: String, authVersion: long)` 实现 `Principal`；`getName()` 为 UUID 字符串，也是 Spring Session indexed 主体名。HTTP `Authentication` 和 Session principal 索引均使用它。
- `SessionAccess.isLive(WorkbenchPrincipal, String springSessionId): boolean` / `requireLive(...)` 检查 Redis 主动活动标记和数据库 enabled、role、authVersion。实时子任务在 STOMP CONNECT、SUBSCRIBE 和发送新事件前使用该合同；Redis/数据库故障时必须拒绝，不按旧身份放行。
- `SessionRevoker.revokeOlder(UUID userId, long currentVersion, String preserveSessionId)` 使用 Spring Session 主体索引删除旧 Session 与活动标记。实时子任务还需关闭关联连接；仅删除 Session 不能证明已建立 WebSocket 不再收新事件。

Spring Session Redis indexed TTL 为 604800 秒。`SessionActivity` 的 Redis 标记以 Session ID 为键、独立于框架 `lastAccessedTime`；只有登录初始化和上述主动活动信号更新。自动 HTTP/STOMP 流量可以刷新框架 TTL，却不能改变该标记。时间达到最近主动活动后第 604800 秒即失效；Redis 标记缺失时拒绝。真实 HTTP + PostgreSQL/Redis 定向测试覆盖 604799/604800 边界、多会话重置、自改保留当前、并发最后管理员、并发首管理员。活动标记读取注入 `RedisConnectionFailureException` 时受保护 HTTP 返回 503 且无身份数据；Spring Session 仓储读取注入同类故障时请求返回 5xx、无业务或异常详情；两者均不放行。受控登录竞争分别验证管理员先提交后旧凭据登录 401，以及登录持锁时管理员重置等待提交。STOMP 关闭/通知需与实时子任务联测。

## 配置与验证

空账号表时同时设置 `WORKBENCH_BOOTSTRAP_USERNAME`、`WORKBENCH_BOOTSTRAP_PASSWORD` 建立首个 ADMIN；已有任意账号时忽略引导配置，不重置凭据。V13 仅新增 `user_accounts`，未改 V1～V12。旧 Nginx Basic Auth 保留到入口子任务替换。

在 `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/ai_workbench?currentSchema=d9_owner_tests_20260925`、`WORKBENCH_TEST_SCHEMA=d9_owner_tests_20260925` 的隔离 `test` Profile 与本机 Redis 上执行：`mvn -q '-DargLine=-Xms64m -Xmx384m -javaagent:C:\Users\kira\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar' verify`，退出码 0；Surefire 25 suites / 124 tests / 0 failures / 0 errors / 0 skipped。现有 `PrivateRealtimeIntegrationTest` 独立使用 `live-acceptance` Profile 和 `d9_live_acceptance` schema。Windows Oracle JDK 17 下 Mockito 自附加失效，`-javaagent` 仅用于本机测试启动。
