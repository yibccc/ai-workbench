# D1 技术组合验证记录

验证日期：2026-09-19（Asia/Shanghai）

## 结论

前后端骨架、固定依赖、状态契约、离线测试、生产构建以及一次真实 DeepSeek 最小调用已验证。后端能在存储不可用、未配置 DeepSeek 凭据时正常启动，并通过状态接口明确区分 `UP`、`DOWN`、`NOT_CONFIGURED` 与“已配置但当前请求未实时调用”的 `NOT_CHECKED`。

Windows PowerShell 中没有 `docker` 命令，但 WSL Ubuntu 提供 Docker Engine 28.3.0 与 Compose 2.37.3。Compose 配置解析、两个容器健康检查，以及 Windows 后端跨 WSL 端口映射连接 PostgreSQL/Redis 均已验证通过。

## 版本矩阵

| 组件 | 固定版本/配置 | 本机解析结果 |
| --- | --- | --- |
| Java | 17 | `java 17 2021-09-14 LTS` |
| Maven | 3.9.9 | 已运行构建与依赖树 |
| Spring Boot | 3.5.16 | 编译、测试、打包、启动通过 |
| AgentScope Java | 2.0.3 | `agentscope-core` 与 OpenAI 扩展均解析并编译通过 |
| PostgreSQL JDBC | 42.7.11（由 Boot BOM 管理） | 依赖解析、真实连接通过 |
| PostgreSQL image | `postgres:17.6-alpine` | WSL 容器 healthy；Windows `127.0.0.1:5432 -> 5432` |
| Redis image | `redis:7.4.2-alpine` | WSL 容器 healthy；Windows `127.0.0.1:6379 -> 6379` |
| Node.js | 25.2.1 | npm 安装、lint、构建通过 |
| npm | 11.6.2 | lockfile 已生成 |
| React / React DOM | 19.1.1 | 生产构建通过 |
| Vite | 7.3.6 | 生产构建通过；`npm audit` 无已知漏洞 |
| TypeScript | 5.9.2 | `tsc -b` 通过 |
| DeepSeek API | `https://api.deepseek.com`, `deepseek-flash` | AgentScope 网关真实调用通过 |

## 官方依据

- Spring Boot 官方稳定版本列表在验证时列出 3.5.16：<https://docs.spring.io/spring-boot/spring-projects.html>
- AgentScope Java 官方 v2.0.3 release：<https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.3>
- AgentScope Java 2.0 官方说明模型 provider 已拆成独立扩展，并支持 Spring Boot：<https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/en/intro.md>
- DeepSeek 官方文档确认 OpenAI 兼容 base URL 为 `https://api.deepseek.com`，当前模型名包括 `deepseek-flash`：<https://api-docs.deepseek.com/>

## 实际执行与结果

### 环境

```text
Windows 11 家庭版 中文版 10.0.26200 (64-bit)
Java 17
Apache Maven 3.9.9
Node v25.2.1
npm 11.6.2
Git 2.52.0.windows.1
Docker in Windows PATH: command not found
Docker Engine via WSL Ubuntu: 28.3.0
Docker Compose via WSL Ubuntu: 2.37.3
DEEPSEEK_API_KEY present in ambient shell: false
DEEPSEEK_API_KEY present in ignored root .env: true (value never printed)
```

### 后端

```powershell
cd backend
mvn clean verify
mvn dependency:tree "-Dincludes=org.springframework.boot:*,io.agentscope:*,org.postgresql:postgresql,org.springframework.data:*" "-DoutputFile=target/dependency-tree.txt"
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

结果：

- `mvn clean verify`：BUILD SUCCESS；5 tests，0 failures，0 errors，0 skipped。
- `mvn package`：成功生成可执行 JAR。
- 依赖树确认 Spring Boot 3.5.16、AgentScope core/OpenAI extension 2.0.3、PostgreSQL JDBC 42.7.11。
- JAR 在 8080 端口成功启动。
- 首次在依赖未就绪时，`GET /api/status` 正确返回存储 `DOWN`，`GET /actuator/health` 返回 HTTP 503 / `DOWN`。
- 不加载 `.env` 时，`POST /api/ai/probe` 返回 HTTP 503 / `NOT_VERIFIED`，detail 为 `DEEPSEEK_API_KEY is not configured`；未发出模型调用。

在 WSL Compose 容器 healthy 后，从被 Git 忽略的 `.env` 向后端子进程注入数据库用户名、密码、数据库名及端口（值未输出），实际结果为：

- `GET /api/status`：HTTP 200；backend、postgres、redis 均为 `UP`。
- PostgreSQL 探针：`Connection accepted`。
- Redis 探针：`PING returned PONG`。
- `GET /actuator/health`：HTTP 200 / `UP`；`db` 与 `redis` 均为 `UP`，Redis 版本为 7.4.2。
- DeepSeek 在普通状态请求中为 `NOT_CHECKED`，确认状态页不会自动产生模型费用。
- `Get-NetTCPConnection -State Listen -LocalPort 8080` 确认后端仅监听 `127.0.0.1`。

### 真实 DeepSeek 最小调用

根 `.env` 已被 `.gitignore` 忽略。验证脚本仅在一次性后端子进程中加载 `DEEPSEEK_API_KEY`、base URL 与模型名，命令输出及本文均未打印或记录密钥。

```powershell
# 脱敏后的等价流程；实际 key 从被忽略的根 .env 注入进程环境
$env:DEEPSEEK_API_KEY = '<redacted>'
java -jar target/backend-0.0.1-SNAPSHOT.jar
curl.exe --max-time 120 -X POST http://localhost:8080/api/ai/probe
```

结果（2026-09-19 21:37:46 +08:00）：

- AgentScope Java 2.0.3 `OpenAIChatModel` + `DeepSeekFormatter`
- base URL：`https://api.deepseek.com`
- model：`deepseek-flash`
- HTTP 200
- API 状态：`UP`
- 模型响应：`AI_WORKBENCH_OK`

### 前端

```powershell
cd frontend
npm ci
npm run lint
npm run build
```

结果：

- 依赖按 `package-lock.json` 全新安装成功。
- ESLint 通过，无报错。
- TypeScript 与 Vite 生产构建通过；30 modules transformed，构建产物生成于 `dist/`。
- 初始 Vite 7.1.5 被 `npm audit` 报告存在 Windows 路径穿越风险，已升级并固定为 7.3.6；升级后审计无已知漏洞。
- 初始 ESLint 9.35.0 已停止支持；已升级并固定为 ESLint 10.11.0 及兼容插件，升级后 lint 与构建通过。

### Compose

Windows 当前环境通过 WSL Ubuntu 使用 Docker：

```powershell
wsl -d Ubuntu -- bash -lc "cd /mnt/e/projects/workbench && docker compose config --quiet"
wsl -d Ubuntu -- bash -lc "cd /mnt/e/projects/workbench && docker compose up -d --wait"
wsl -d Ubuntu -- bash -lc "cd /mnt/e/projects/workbench && docker compose ps"
```

结果：

- `docker compose config --quiet` 通过。
- `ai-workbench-postgres-1`：healthy，`127.0.0.1:5432 -> 5432`。
- `ai-workbench-redis-1`：healthy，`127.0.0.1:6379 -> 6379`。
- 5432 原由弃用的 `langchain-pgvector` 容器（`pgvector/pgvector:pg16`）占用；经用户授权删除该容器但保留其卷，随后项目 PostgreSQL 使用默认 5432 成功启动。
- 6379 原由旧的 `redis-search` 容器占用；该旧容器已删除但其卷未删除，随后项目 Redis 使用默认 6379 成功启动。
- 后端数据源现同时支持 `POSTGRES_HOST`、`POSTGRES_PORT`、`POSTGRES_DB`，无需为本机端口冲突构造额外 `DATABASE_URL`。

## 待补验收

1. 打开 <http://localhost:5173>，确认页面展示与 `/api/status` 一致。当前生产构建已通过，但浏览器人工验收尚未执行。

## 安全边界

- `DEEPSEEK_API_KEY` 只由 Spring Boot 的环境配置读取。
- Compose 自动读取根 `.env`，Spring Boot 不会自动读取；README 的 PowerShell 启动流程会先把该文件注入后端进程环境，避免两端配置漂移。
- 前端没有任何密钥变量，状态响应只返回是否已配置，不返回密钥或其片段。
- 普通状态页面不会触发模型调用；模型探针必须显式 POST，避免无意产生费用。
- Spring Boot、PostgreSQL 与 Redis 只监听宿主机 `127.0.0.1`，不向局域网暴露开发口令或模型探针。
