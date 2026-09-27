# AI Workbench

个人 AI 工作台，用于记录工作、管理待办，以及生成可编辑、可追溯的日报和周报。支持 Windows 本机运行和 Linux Docker 部署。

## 功能

- 一句话拆分工作记录和待办，实时展示 AI 处理结果，支持失败重试和批次撤销。
- 项目管理、历史日期补记、待办筛选与完成结果记录。
- 手动选择日期生成日报，按周一至周日生成自然周周报。
- 报告保留历史版本和来源快照，支持编辑、人工补充与复制。日报可删除指定版本，原工作记录保留。
- 工作记录、待办任务、专注、工作汇报、项目管理五个工作区；列表分页、项目搜索和按需编辑抽屉。
- 专注页支持每日重复待办、净计时与 15 秒微休息；结束后按业务日期保存投入并接入日报和周报来源，不自动完成待办。
- 专注会话从明确开始到暂停、结束或净目标到期持续按服务器时间计时；切换页面、关闭页面或设备睡眠不会自动暂停。停工时需手动暂停或结束，微休息只在可见页面实际触发时计入。
- 受邀账号登录，每名用户独立拥有业务数据；管理员可创建、启停账号、重置密码和修改角色。

工作记录是默认入口，可切换 AI 快记与手工记录。待办以列表为主，新建和编辑在右侧抽屉中完成；手机上抽屉全屏展示。已访问的工作区保留草稿与筛选状态。工作汇报集中管理日报和周报，桌面并列展示正文与来源，窄屏将来源放在正文之后。

## Linux Docker 部署

安装 Docker Engine 和 Compose v2，在仓库根目录执行：

```bash
test -f .env || cp .env.example .env
chmod 600 .env
# 编辑 .env，设置数据库密码、首位管理员引导账号密码和 DeepSeek API Key
docker compose up -d --build --wait
```

Compose 启动 PostgreSQL、Redis、Java 后端和 Nginx 前端，构建所需的 Java、Node 均由镜像提供。后端镜像构建使用 [阿里云 Maven 公共镜像](https://developer.aliyun.com/mirror/maven)，配置在 `backend/maven-settings-aliyun.xml`；本机 Maven 构建慢时可在 `backend/` 运行 `mvn -s maven-settings-aliyun.xml clean verify`。首次构建还需要访问 Docker 镜像源和 npm。

从无账号版本升级且旧库已有业务行时，先备份并在隔离环境制定旧数据归属；迁移会拒绝无归属业务行，不能自动分配给首位管理员，也不能用旧应用访问已启用多人归属的新库。

专注功能的 V15/V16 增量迁移随新版后端执行；启动后可直接创建重复规则和专注会话。V16 移除旧版睡眠失联待确认状态，已发布的 V15 脚本保持不变。数据库迁移会保留原有记录，升级前仍应按现有备份流程保护数据。

默认入口为 <http://127.0.0.1:8088>。首次启动前，在 `.env` 同时设置 `WORKBENCH_BOOTSTRAP_USERNAME` 和 `WORKBENCH_BOOTSTRAP_PASSWORD`，空账号表会创建首位管理员。确认该账号可登录后，从 `.env` 删除引导密码；已有账号时引导配置不会修改任何密码。后续用户由管理员在工作台账号菜单的“用户管理”中创建。应用会话仅由明确用户操作续期，连续 7 天无主动操作后需重新登录。

临时按 IP 直接使用 HTTP 时，在 `.env` 设置 `APP_BIND=0.0.0.0`、`APP_PORT=8088`、`WORKBENCH_WS_ALLOWED_ORIGINS=http://<主机IP>:8088`、`WORKBENCH_COOKIE_SECURE=false`，再从同一网络访问 `http://<主机IP>:8088`。此入口的密码和会话流量未经传输加密；取得域名和证书后按下文改用 HTTPS。

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
WORKBENCH_COOKIE_SECURE=true
```

执行 `docker compose up -d --build --wait` 应用配置。WebSocket Origin 与浏览器入口的协议、主机、端口一致；多个入口用逗号分隔。Caddy 会处理 HTTPS 证书和 WebSocket 转发；使用其他代理时需转发 Host、X-Forwarded-Proto 和 Upgrade 头。正式 HTTPS 入口启用 Secure Cookie；本机 loopback HTTP 保持 `WORKBENCH_COOKIE_SECURE=false`。

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
| `WORKBENCH_BOOTSTRAP_USERNAME` / `WORKBENCH_BOOTSTRAP_PASSWORD` | 仅空账号表首次启动时创建管理员；成功后移除引导密码 |
| `WORKBENCH_COOKIE_SECURE` | HTTPS 入口设为 `true`；loopback HTTP 开发为 `false` |
| `APP_BIND` / `APP_PORT` | Docker 入口绑定地址与端口，默认 `127.0.0.1:8088` |
| `WORKBENCH_WS_ALLOWED_ORIGINS` | WebSocket 允许的完整入口地址；开发默认 5173/15173，Compose 默认 8088 |
| `POSTGRES_PORT` / `REDIS_PORT` | 数据库宿主机端口，默认 5432/6379 |

模型凭据放在后端环境变量中；前端的 `VITE_` 变量用于可公开的构建配置。AI 输入和报告生成会调用付费模型，健康检查只检查服务状态。

## Windows 本机运行

准备 Java 17、Maven、Node.js，以及 Docker。本机验证环境为 Maven 3.9.9、Node 25.2.1、npm 11.6.2；Node 版本约束见 [frontend/package.json](frontend/package.json)。

以下命令在仓库根目录 PowerShell 执行，每条成功后继续：

多人版首次连接现有数据库前，先确认目标 `public` schema 的旧业务行已经过授权处理；V14 会拒绝给无归属的旧行编造 owner。下面的构建命令不处理业务数据，不能直接用启动命令跳过这一步。

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Docker 位于 WSL Ubuntu 时，按实际仓库位置调整 --cd
wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- docker compose up -d --wait postgres redis
Push-Location backend
mvn -s maven-settings-aliyun.xml -DskipTests package
Pop-Location
Push-Location frontend
npm ci
npm run build
Pop-Location
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1
```

若 Docker CLI 安装在 Windows，依赖启动命令为 `docker compose up -d --wait postgres redis`。

首次启动前也须在 `.env` 设置首位管理员引导账号密码，登录成功后移除引导密码。打开 <http://127.0.0.1:5173>，通过应用登录。本机模式运行打包 JAR 和 Vite 开发服务器，模型配置由启动脚本从 `.env` 加载；日志和进程记录保存在 `.local-runtime/`。

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
- [专注规则、计时与报告来源合同](.trellis/spec/backend/focus-routines.md)
- [专注页面与账号级控制合同](.trellis/spec/frontend/focus-page.md)
- [日报版本删除接口](.trellis/spec/backend/report-deletion.md)
- [分页约定](.trellis/spec/backend/pagination.md)
- [一页架构说明](.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/architecture.md)

## 回归测试

后端测试使用真实 PostgreSQL 的隔离 schema；执行迁移测试前先核对目标 schema 是否全新，已有无归属业务行的旧 schema 不可直接复用。须显式设置指向同一新测试 schema 的 `TEST_DATABASE_URL`（JDBC URL 的 `currentSchema`）和 `WORKBENCH_TEST_SCHEMA`，不能指向 `public` 或复用日常业务 schema：

```powershell
cd backend
mvn -s maven-settings-aliyun.xml clean verify
```

浏览器回归使用 Playwright、`e2e` Profile、隔离 schema `d9_e2e` 和确定性模型替身。先启动**全新独立 Compose 项目**的 PostgreSQL/Redis 卷和非默认宿主端口，并显式设置 `E2E_DATABASE_URL`（loopback、独立库、`currentSchema=d9_e2e`）、`POSTGRES_USER`、`POSTGRES_PASSWORD` 和 `REDIS_PORT`（非 6379）；测试配置缺少这些值会拒绝运行。不要复用日常 `ai-workbench` 持久卷。

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
