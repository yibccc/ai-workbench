# AI Workbench

个人 AI 工作台，用于记录工作、管理待办，以及生成可编辑、可追溯的日报和周报。支持 Windows 本机运行和 Linux Docker 部署。

## 功能

- 一句话拆分工作记录和待办，实时展示 AI 处理结果，支持失败重试和批次撤销。
- 项目管理、历史日期补记、待办筛选与完成结果记录。
- 手动选择日期生成日报，按周一至周日生成自然周周报。
- 报告保留历史版本和来源快照，支持编辑、人工补充与复制。日报可删除指定版本，原工作记录保留。
- 工作记录、待办任务、工作汇报、项目管理四个工作区；列表分页、项目搜索和按需编辑抽屉。

工作记录是默认入口，可切换 AI 快记与手工记录。待办以列表为主，新建和编辑在右侧抽屉中完成；手机上抽屉全屏展示。已访问的工作区保留草稿与筛选状态。工作汇报集中管理日报和周报，桌面并列展示正文与来源，窄屏将来源放在正文之后。

## Linux Docker 部署

安装 Docker Engine 和 Compose v2，在仓库根目录执行：

```bash
test -f .env || cp .env.example .env
chmod 600 .env
# 编辑 .env，设置数据库密码、访问密码和 DeepSeek API Key
docker compose up -d --build --wait
```

Compose 启动 PostgreSQL、Redis、Java 后端和 Nginx 前端，构建所需的 Java、Node 均由镜像提供。首次构建需要访问 Docker 镜像源、Maven 和 npm。

默认入口为 <http://127.0.0.1:8088>，使用 `.env` 中的 `WORKBENCH_AUTH_USER` 和 `WORKBENCH_AUTH_PASSWORD` 登录。

远程访问可建立 SSH 隧道：

```bash
ssh -L 8088:127.0.0.1:8088 user@server
```

随后在本机浏览器打开上述入口。

### 域名与 HTTPS

公网入口使用 HTTPS。保持 `APP_BIND=127.0.0.1`，由服务器上的反向代理连接工作台。例如 Caddy 配置：

```caddyfile
workbench.example.com {
    reverse_proxy 127.0.0.1:8088
}
```

将域名解析到服务器，配置 80/443 端口，并在 `.env` 设置：

```dotenv
WORKBENCH_WS_ALLOWED_ORIGINS=https://workbench.example.com
```

执行 `docker compose up -d --build --wait` 应用配置。WebSocket Origin 与浏览器入口的协议、主机、端口一致；多个入口用逗号分隔。Caddy 会处理 HTTPS 证书和 WebSocket 转发；使用其他代理时需转发 Host、X-Forwarded-Proto 和 Upgrade 头。

### 查看、停止和更新

```bash
docker compose ps
docker compose logs --tail=100 backend frontend
docker compose stop
# 更新源码后重新构建启动
docker compose up -d --build --wait
```

数据存储于 `ai-workbench_postgres-data` 和 `ai-workbench_redis-data` 命名卷。日常停服使用 `stop`；`down -v` 会删除数据卷。已有数据库的密码变更需要同时在 PostgreSQL 中执行，修改 `.env` 仅更新连接配置。

## 配置

配置模板见 [.env.example](.env.example)。

| 配置 | 用途 |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | 数据库连接信息；新部署设置独立密码 |
| `DEEPSEEK_API_KEY` | 后端模型调用凭据 |
| `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | 模型地址和名称 |
| `DEEPSEEK_TIMEOUT` | 输入解析超时，默认 `PT4M` |
| `REPORT_AI_TIMEOUT` / `REPORT_AI_MAX_TOKENS` | 报告超时与输出长度，默认 `PT6M` / `4096` |
| `WORKBENCH_AUTH_USER` / `WORKBENCH_AUTH_PASSWORD` | Docker 入口账号密码，启动前填写；密码为单行、最多 72 个 UTF-8 字节 |
| `APP_BIND` / `APP_PORT` | Docker 入口绑定地址与端口，默认 `127.0.0.1:8088` |
| `WORKBENCH_WS_ALLOWED_ORIGINS` | WebSocket 允许的完整入口地址；开发默认 5173/15173，Compose 默认 8088 |
| `POSTGRES_PORT` / `REDIS_PORT` | 数据库宿主机端口，默认 5432/6379 |

模型凭据放在后端环境变量中；前端的 `VITE_` 变量用于可公开的构建配置。AI 输入和报告生成会调用付费模型，健康检查只检查服务状态。

## Windows 本机运行

准备 Java 17、Maven、Node.js，以及 Docker。本机验证环境为 Maven 3.9.9、Node 25.2.1、npm 11.6.2；Node 版本约束见 [frontend/package.json](frontend/package.json)。

以下命令在仓库根目录 PowerShell 执行，每条成功后继续：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Docker 位于 WSL Ubuntu 时，按实际仓库位置调整 --cd
wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- docker compose up -d --wait postgres redis
Push-Location backend
mvn clean verify
Pop-Location
Push-Location frontend
npm ci
npm run build
Pop-Location
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1
```

若 Docker CLI 安装在 Windows，依赖启动命令为 `docker compose up -d --wait postgres redis`。

打开 <http://127.0.0.1:5173>。本机模式运行打包 JAR 和 Vite 开发服务器，模型配置由启动脚本从 `.env` 加载；日志和进程记录保存在 `.local-runtime/`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/stop.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1
```

重复启动会复用已登记的进程。端口被其他进程占用时，脚本提示在原终端停止。更新后端前先执行停止脚本，再构建和启动，以释放 Windows JAR 文件锁。依赖容器和数据卷在停止应用后继续保留。

重启 Windows 后，依次启动 WSL/Docker、数据库依赖和应用。健康检查地址为 <http://127.0.0.1:8080/actuator/health>。

## 备份与恢复

备份包含业务数据，请存放在受保护目录并保留异机副本。恢复演练使用新的数据库，核对数据后再切换应用连接。

### Linux

```bash
umask 077
mkdir -p .local-backups
backup=".local-backups/workbench-$(date +%Y%m%d-%H%M%S).dump"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
# 确认备份成功后，恢复到尚不存在的新库
docker compose exec -T postgres sh -c 'createdb -U "$POSTGRES_USER" d10_restore_check'
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d d10_restore_check --no-owner --exit-on-error' < "$backup"
```

### Windows

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/backup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/backup.ps1 -RestoreTo d10_restore_mycheck
# 将 ArchivePath 换成实际备份文件
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/backup.ps1 -RestoreTo d10_restore_recovery -ArchivePath .local-backups/d10/ai_workbench_REPLACE.dump
```

脚本针对本机 `ai-workbench-postgres-1` 容器，使用 PostgreSQL 标准 custom archive，输出文件路径和 SHA256。`RestoreTo` 要求一个全新的 `d10_restore_*` 库名；恢复失败时该验证库保留供排查。使用 `DATABASE_URL` 连接其他数据库时，应按目标数据库单独执行备份。

恢复后核对迁移版本、业务表和报告来源，停止应用，将 `.env` 中的 `POSTGRES_DB` 改为恢复库，再启动。原库保留供回退。

## 开发与架构

后端采用 Controller → Service 接口 → Service 实现 → MyBatis Mapper 三层架构，事务位于 Service 实现；PageHelper 提供数据库分页。前端按 `features / components / api / hooks / utils` 组织。

- [后端目录结构](.trellis/spec/backend/directory-structure.md)
- [前端目录结构](.trellis/spec/frontend/directory-structure.md)
- [数据库与接口合同](.trellis/spec/backend/database-guidelines.md)
- [日报版本删除接口](.trellis/spec/backend/report-deletion.md)
- [分页约定](.trellis/spec/backend/pagination.md)
- [一页架构说明](.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/architecture.md)

## 回归测试

后端测试使用真实 PostgreSQL 的隔离 schema `d9_backend_tests`：

```powershell
cd backend
mvn clean verify
```

浏览器回归使用 Playwright、`e2e` Profile、隔离 schema `d9_e2e` 和确定性模型替身：

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run lint
npm run build
npm run e2e
```

截图、视频和 trace 保存在 `frontend/test-results/`，HTML 报告在 `frontend/playwright-report/`。真实模型验收使用独立的 `live-acceptance` Profile 和 schema，调用次数记录在验收日志中。

## 功能限制与验收记录

- 日报生成完成或失败后可删除；周报目前保留全部历史版本。
- 大来源报告的真实模型耗时仍在观察，当前报告超时为 6 分钟。
- 连续五个工作日使用与人工耗时对比等待使用记录。

[五分钟演示](.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/demo.md) · [D9 验收记录](.trellis/tasks/archive/2026-09/09-15-d9-e2e-feedback/research/acceptance-log.md) · [本机交付验证](.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/delivery-validation.md) · [Linux 部署验证](.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/linux-deployment-validation.md)
