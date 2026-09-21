# AI Workbench

日报允许同一天手动生成多个版本，以保留已有编辑稿。成功或失败版本可通过站内“删除此版本”确认弹窗移除，生成中不能删除。删除仅隐藏指定日报，保留其他版本、来源快照和原工作记录/待办；未保存正文会在确认后丢弃。API 为 `DELETE /api/reports/{id}?version=当前版本号`，版本冲突返回 409；同一已删除日报重复删除返回 204（忽略过期的非负版本号）。删除后普通详情和来源分页返回 404，原 requestId 再次生成返回 409，需用新 requestId 手动生成。周报暂不支持删除。

个人 AI 工作台的本机应用。当前已支持项目创建、改名和归档，当天或历史工作记录的维护，带项目、备注、优先级、可选期限、乐观锁和完成记录联动的待办管理，以及带来源快照和历史版本的日报、自然周周报；同时保留后端健康检查、PostgreSQL/Redis 连接探针和显式触发的 DeepSeek 最小调用。

## 代码组织

后端采用常规三层架构，不使用 DDD：Controller 只依赖 Service 接口，事务和业务规则位于 `service/impl`，数据库查询位于 Mapper/XML。AI 编排服务与持久化服务仍是独立 Spring Bean，模型调用不占用数据库事务。

```text
backend/src/main/
├── java/com/aiworkbench/
│   ├── controller/          HTTP 接口
│   ├── service/             用例接口
│   │   └── impl/            业务实现、事务边界
│   ├── mapper/              MyBatis 接口
│   ├── dto/<feature>/       请求、响应和用例数据
│   ├── entity/<feature>/    持久化行投影
│   ├── enums/               状态、优先级和来源角色
│   ├── exception/           异常与 HTTP 错误转换
│   ├── common/              分页响应与查询作用域
│   ├── config/              配置、启动恢复、类型处理器
│   ├── ai/                  模型适配器与模型协议
│   ├── events/              WebSocket 与提交后通知
│   └── e2e/                 仅测试 Profile 使用的模型替身
└── resources/
    ├── mapper/              SQL XML
    └── db/migration/        不可变 Flyway 迁移

frontend/src/
├── App.tsx                  工作台编排与跨功能联动
├── features/
│   ├── capture/             一句话记录
│   ├── projects/            项目面板与搜索选择器
│   ├── records/             工作记录列表
│   ├── tasks/               待办管理
│   └── reports/             日报、周报与共享报告分页 hooks
├── api/                     按功能拆分的类型/请求及统一 HTTP 错误处理
├── components/              通用分页与折叠面板
├── hooks/                   实时状态订阅
└── utils/                   上海日期/时间和自然周计算
```

分页由 PageHelper Spring Boot Starter **2.1.1**（PageHelper **6.1.1**）执行，保持 MyBatis Starter **3.0.5**。`PageQueries.select` 将 API 的零起始页号转为 PageHelper 的一起始页号，紧贴目标 Mapper 查询启动分页，映射响应前释放 ThreadLocal，异常时也会清理。Mapper 不再手写列表分页 COUNT/LIMIT/OFFSET；报告全量来源查询不进入分页作用域。`reasonable=false` 保证越界返回空页，参数不合法返回 400。

报告历史和来源各有独立的 10/20/50 条页大小；来源编号按冻结顺序跨页连续。末页按钮禁用显示不可点击光标，请求中的报告分页显示“正在加载”。历史翻页和切换页大小会先保护未保存稿件，新生成版本会刷新总页数；过期的页/详情请求会取消。

## 本机启动

1. 复制 `.env.example` 为 `.env`，本地数据库默认值可直接使用；如需模型探针，再填写 `DEEPSEEK_API_KEY`。
2. 启动依赖：`docker compose up -d --wait`。Windows 用户也可在 WSL Ubuntu 中进入仓库的 `/mnt/<盘符>/...` 路径后执行同一命令。
3. 在仓库根目录用 PowerShell 将 `.env` 注入当前进程，再启动后端：

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^\s*[^#][^=]*=' } | ForEach-Object {
     $name, $value = $_ -split '=', 2
     [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
   }
   Push-Location backend
   mvn spring-boot:run
   ```

   `mvn spring-boot:run` 本身不会自动读取根目录 `.env`；上述注入确保后端与 Compose 使用同一组数据库端口、口令和 DeepSeek 配置。结束后可在另一个终端继续后续步骤。
4. 启动前端：`cd frontend && npm ci && npm run dev`。
5. 打开 <http://localhost:5173>；后端状态接口为 <http://localhost:8080/api/status>，Actuator 健康接口为 <http://localhost:8080/actuator/health>。

后端启动时由 Flyway 自动执行数据库迁移。项目接口位于 `/api/projects`，工作记录接口位于 `/api/records`，待办接口位于 `/api/tasks`。待办列表可按 `status`、`projectId`、`unassigned`、`priority` 和 `due`（`ALL/OVERDUE/TODAY/UPCOMING/NONE`）筛选；更新和删除必须携带当前 `version`，过期版本返回 409。记录列表的 `date=YYYY-MM-DD` 与待办期限筛选均按 `Asia/Shanghai` 解释。项目归档后历史记录和待办仍保留原归属，但不能再把新数据关联到该项目。

待办状态只能通过 `POST /api/tasks/{id}/complete` 和 `POST /api/tasks/{id}/reopen` 修改，不能通过通用编辑接口修改。完成待办会在同一数据库事务中生成一条自动工作记录；重复完成请求不会重复生成记录。`PUT /api/tasks/{id}/completion-result` 可补充完成结果，`GET /api/tasks/{id}/events` 可查询状态历史。重开或删除待办只会使当前自动完成记录失效，既往结果和事件保留，手工工作记录不受影响。

AI 统一输入使用 `POST /api/inputs`：后端先保存原文、客户端 `requestId`、Asia/Shanghai 解析基准和 `PROCESSING` 状态，再在事务外调用 DeepSeek。用 `GET /api/inputs/{id}` 读取状态与生成条目；失败后可调用 `POST /api/inputs/{id}/retry`，并沿用首次解析基准。同一 `requestId` 始终指向同一批次，相同文本使用不同 `requestId` 时会创建不同批次；PostgreSQL 处理令牌和租约保证并发重发、重试或服务重启后最多生成一批结果，Redis 不参与最终正确性。默认模型调用超时为 4 分钟、处理租约为 5 分钟；启动时会拒绝“租约不长于模型超时”的配置，避免仍在执行的有效请求被错误回收。未被编辑、完成、重开或删除的成功批次可通过 `POST /api/inputs/{id}/revert` 整批撤销，重复撤销幂等；原文和批次历史继续保留。AI 只关联已有活动项目，无法匹配的项目保持未分类。

手动日报使用 `POST /api/reports { reportType: "DAILY", date, requestId }` 创建独立版本，并通过 `GET /api/reports/{id}` 查询处理状态；`GET /api/reports?date=YYYY-MM-DD` 查询当天全部历史版本，`PATCH /api/reports/{id} { content, version }` 显式保存编辑后的正文。生成时会冻结所选上海本地日内的有效工作记录和当天到期的未完成待办，后续修改源数据不会改变旧版本来源；每个 AI 要点必须引用冻结来源，正文中的“来源 N”与核对列表逐项对应。同一来源可支撑多个要点，同一要点内的重复引用会规范化去重；未知来源或错误分段角色会使新版本失败且不覆盖旧稿。人工编辑后界面会明确说明引用关系不再自动适用于修改内容；切换日期、历史版本或重新生成前会提示未保存修改。前端复制的是当前编辑区文本，不会隐式保存。

自然周周报使用相同创建接口并传入 `reportType: "WEEKLY"`。任意输入日期都会按 `Asia/Shanghai` 归一到所在周的周一，周期为 `[本周一 00:00, 下周一 00:00)`，`periodEnd` 是排他边界。固定输出“本周完成、进行中与阻碍、下周计划”；下周计划只允许引用期限明确落在下一自然周的未完成待办，无期限待办不会成为自动承诺。每次重新生成都会创建新版本并通过 `previousReportId` 指向上一版本，旧正文、项目名和来源快照保持不变。用户补充通过 `PATCH /api/reports/{id}/manual-additions` 单独保存，复制时会明确标记为“用户补充（无 AI 来源标记）”。报告生成单独使用 `REPORT_AI_TIMEOUT`（默认 6 分钟）和 `REPORT_AI_MAX_TOKENS`（默认 4096），不改变统一输入的 4 分钟超时与 5 分钟租约关系。

项目、记录、待办、报告历史和报告来源同时提供兼容分页入口，页号从 0 开始，支持每页 10/20/50 条。工作台默认每页 20 条，并按新建时间稳定倒序；项目选择器支持搜索。功能区可独立折叠且保留草稿和后台处理，输入与待办默认展开、报告默认折叠。日报与周报日期互相独立，报告只会在用户点击后手动生成。

AI 输入和报告状态通过同源 `/ws/events` 推送已提交的状态元数据，正文和结果仍通过详情 API 获取。断线时客户端指数重连，并以低频 GET 兜底；刷新会恢复仍在处理的实体 ID。推送失败不影响数据库事务，也不会自动重试付费模型调用。

报告失败响应包含脱敏的 `errorCode`、`errorStage` 和 `sourceCount`，可区分 JSON、输出截断、未知来源、来源角色、无效要点和超时。模型提示使用本报告内的短来源别名，服务端再映射回冻结 UUID；不会为缩短提示而静默丢弃来源。

Compose 端口由 `.env` 中的 `POSTGRES_PORT` 与 `REDIS_PORT` 控制，后端使用同名变量连接本机映射端口。默认分别为 5432 和 6379；若本机确有不可移除的端口冲突，可在 `.env` 中改为其他未占用端口，容器内部端口无需修改。

后端、PostgreSQL 与 Redis 都只监听 `127.0.0.1`，避免开发口令或可产生费用的模型探针暴露到局域网。如未来需要从其他设备访问，应先补充认证与网络访问控制，而不是直接扩大监听地址。

模型调用不会随普通健康检查自动发生。配置凭据后，手动执行：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai/probe
```

## 回归测试

后端全量测试使用本机真实 PostgreSQL：

```powershell
cd backend
mvn clean verify
```

浏览器回归使用 Playwright 与显式 `e2e` Profile。该 Profile 使用隔离的 `d9_e2e` schema 和确定性模型替身，不读取 DeepSeek 密钥、不产生付费调用，也不会清理正常开发数据。Maven 集成测试使用独立的 `d9_backend_tests` schema：

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run lint
npm run build
npm run e2e
```

失败时截图、视频和首次重试 trace 写入 `frontend/test-results/`；HTML 报告写入 `frontend/playwright-report/`，两者均为可重建的本地文件。`live-acceptance` Profile 只用于显式真实模型语义验收，并使用独立的 `d9_live_acceptance` schema；不要把测试 Profile 用于日常或部署启动。

D9 的 A01—A13 矩阵、真实模型调用账本、复现命令与仍待用户反馈的项目记录在 `.trellis/tasks/09-15-d9-e2e-feedback/research/acceptance-log.md`。
