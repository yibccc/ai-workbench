# WB-20260925-auth-isolation-8cbe4aee / r1 本地核对

核对日期：2026-09-25。源包：`.workbench/inbox/WB-20260925-auth-isolation-8cbe4aee/r1/`；保全副本：`research/handoff/r1/`。本文件只记录规划证据，不代表实现或验收。

## 来源、完整性与状态

- `handoff_id=WB-20260925-auth-isolation-8cbe4aee`、`revision=1`、`repository=yibccc/ai-workbench`、`status=draft`、`task_size=complex`、`product_decisions=pending`。目标仓库的 origin 是 `yibccc/ai-workbench`。
- 源包 6 个内容文件与 `SHA256SUMS` 全部匹配；另有清单自身 SHA-256 `31066ef6dfcc55db82adcbda90ce08b2a901ad8ed8080c552f52b6f39725d47f`。复制后的 7 个文件逐一比对相同。无越界清单路径、额外文件、reparse point/符号链接或所检私钥/token 格式命中；没有执行包内内容。

| 文件 | SHA-256 |
| --- | --- |
| `HANDOFF.md` | `865e1220c9573e4db8520a4598f6fe9f2230c65e1eedd9ff80ccdc1f4900289b` |
| `requirements.md` | `48e98224372b70fe1aded3c4ae3cfe714d55da3ffec7862f543704bad97866a2` |
| `solution.md` | `66f89ebf629d2d3ab6860c5d001a74059e87b241a275801e2d7e956b80b17ae9` |
| `acceptance.md` | `0c5e17d0a913ab07ada15dd53f5003647e3e198a3e382e77ad1edc5d62326537` |
| `assets/ui-concept.png` | `cb8fc6c40b2ea2c9a1b00dfe8de1fc16afc37c2b2ed4100272d8705889710625` |
| `references/HANDOFF-CONTRACT.md` | `e9bc8d61dd64b4dad8e8ee8c0e29c6b47ae306fb0e186d97c113b2ac949addc8` |

目标仓库没有 `docs/dev-sop/HANDOFF-CONTRACT.md`；包内 `references/HANDOFF-CONTRACT.md` 是随包保存的 2.0 格式依据，不覆盖 `.trellis/workflow.md`。`requirements.md` 含 13 个 FR、73 个 AC；`acceptance.md` 含 40 个 TC，AC 定义以 requirements 为准。UI PNG 是生成示意，不是可运行代码或实测截图。

## Git 与工作区

| 项 | 核对结论 |
| --- | --- |
| 包 snapshot | `master @ a4f86e48c1cd76c910b2fae9c94bfec6851277f4`；`git ls-remote origin refs/heads/master` 与它一致。 |
| 本地 HEAD | `feat/single-screen-workspaces @ d6185224839805220aa3ffc7265436c60546156c`。 |
| 基线差异 | 已获取 snapshot commit；`git rev-parse HEAD^{tree}` 与 snapshot 的 tree 均为 `bd0cc3c1cc3a4cd563ce3c7292898a1404b0441d`。仅两个 merge commit 后代，树内容无差异。此结论覆盖已跟踪文件，不覆盖工作区脏文件或实际部署环境。 |
| 现有变更 | 接入前 `.gitignore` 已修改，`.agents/skills/wb-handoff/` 与 `wb-review/` 未跟踪，均属既有工作；未改动、重置、stash 或提交。 |
| 并行工作 | `.trellis/tasks/00-bootstrap-guidelines/` 为另一项 `in_progress`，本轮不更新它。任务创建前当前会话无 active task；创建后规划父任务为当前任务。 |
| 重复接入 | 对现有活动与归档任务检索本 ID/slug，无相同交接任务；本轮首次创建。 |

## 本地代码与规范证据

| 事实或差异 | 本地证据及规划影响 |
| --- | --- |
| 尚无应用账号/鉴权/owner | `backend/pom.xml` 尚未声明 Security/Session；`application.yml` 只有既有 Redis、数据源和 WebSocket 配置。`backend/src/main/resources/db/migration/V1__initial_workbench_schema.sql` 至 V12 没有用户归属；V1 的活动项目名约束为全局 `lower(name)`，V5 输入 request ID 与 V8 报告 request ID 为全局唯一。新增迁移，不能改写旧迁移。 |
| SQL 与后台边界需逐条改造 | `ProjectMapper.xml` 的列表、详情、改名、归档无 owner；`InputMapper.xml` 的 request ID、恢复、生成项与结果查询无 owner；`ReportMapper.xml` 的最新版本、周期锁、取材、来源分页及编辑无 owner。Task/WorkRecord Mapper、全部 service/controller 与测试亦须纳入清单，不可只在 HTTP Controller 检查。 |
| 异步 owner 不能从当前 HTTP Session 推断 | `InputRecoveryRunner` 启动恢复；输入/报告服务在事务外调用 AI、事务内落结果，事件在提交后发。持久请求 owner 必须贯穿处理、重试、恢复和报告版本链。 |
| 实时为全局广播 | `WorkbenchEventHub.java` 持有全连接集合并向所有连接广播；`WorkbenchWebSocketConfig.java` 的 `/ws/events` 是 raw WebSocket。`frontend/src/hooks/realtime.ts` 按旧 JSON 处理，跟踪期间每 15 秒兜底查询，并把 pending ID 存在不分身份的 `localStorage` 键。需前后端一起切 STOMP/用户定向及身份清理。 |
| 前端尚无身份边界 | `App.tsx` 启动直接取项目并保留四个 `RetainedView`；`api/http.ts` 仅把非 2xx 包装为错误；`ToastProvider.tsx` 按 page 过滤且切页清除，5000ms 定时已存在。登录跳转与 Toast 留存需要适配根布局。 |
| 旧入口认证仍在 | `compose.yaml` 注入 `WORKBENCH_AUTH_*`；`deploy/nginx.conf` 使用 `auth_basic`；`deploy/40-workbench-auth.sh` 生成 htpasswd；`README.md` 与 `.trellis/spec/backend/linux-deployment.md` 记录旧合同。后续实现必须同步配置、文档和规范，应用保护完成前不可移除入口保护。 |
| 现有 E2E 需适配身份 | `frontend/playwright.config.ts` 启动 `e2e` profile 与 Vite 代理；`frontend/e2e/workbench.spec.ts` 以匿名 API 请求重置 `d9_e2e` schema 后直接写业务。`E2eResetController`/`E2eMaintenanceServiceImpl` 仅 `e2e & !live-acceptance`，且运行时校验 schema 为 `d9_e2e`。新鉴权必须让测试显式登录/获取 CSRF、重建多用户 fixture，并保持 reset 严格不进入正式 profile；不能为旧测试直接公开匿名后门。 |
| 现有验证路径 | `backend` 的 Maven/Java17 可调用；`frontend/package.json` 定义 `lint`、`build`、`e2e`，npm 可调用。PowerShell 无 `docker` 命令，但 WSL Ubuntu 的 Docker 28.3.0/Compose v2.37.3 可调用，`wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- docker compose config --quiet` 退出码 0。仅配置解析已验证，未启动容器、数据库或浏览器验收。 |

已读取 `.trellis/spec/backend/{index,database-guidelines,error-handling,runtime-integration,linux-deployment,local-delivery}.md`、`.trellis/spec/frontend/{index,directory-structure,viewport-layout,dialogs}.md` 与跨层思考指南。Spring 官方资料确认 Indexed Session 可按 principal 查找、WebSocket 集成负责 Session 与连接映射，STOMP CONNECT 默认需 CSRF；这些只是框架能力，实际接线、依赖解析和失效竞争仍需集成验证。来源见 `solution.md` 第 2.2 节及正式 design。

## FR / AC / TC 与任务映射

| 子任务 | FR | 主责 AC 范围 | 主要 TC | 依赖 |
| --- | --- | --- | --- | --- |
| `09-25-auth-accounts-session` | FR-001～005、FR-011 | ACCOUNT-001～007、AUTH-001～015、ROLE-001～007、SESSION-001～008、BOOTSTRAP-001～003、INIT-001 | TC-001～019、TC-036～037 | 已确认的 P-001 活动合同；为其他子任务提供稳定 userId、鉴权、Session。 |
| `09-25-owner-isolation` | FR-006～007、FR-013 的后端 | ISOLATION-001～009、REGRESSION-001 后端部分 | TC-020～025、TC-039 | 认证主体合同；真实数据库/服务联调。 |
| `09-25-private-realtime` | FR-008 | RT-001～006；共享 AUTH-007、SESSION-002～004/008、ROLE-005 | TC-026～030 | 认证、撤销合同和持久业务 owner。 |
| `09-25-auth-ui-deployment` | FR-009～010、FR-012、FR-013 的前端/入口 | UI-001～013、DEPLOY-001～002、ISOLATION-010；共享 REGRESSION-001 | TC-031～035、TC-038、TC-040 | 前述后端接口与 STOMP 协议。 |
| 父任务集成 | 全部 FR | 所有 73 个 AC 端到端复核，尤其跨子任务共享项 | TC-001～040 | 四个子任务通过后再做最终集成检查。 |

完整逐 AC→TC 对应关系沿用 `research/handoff/r1/acceptance.md` 第 3 节；正式 PRD 保存 73 项 AC，原源包中尚为 draft 的 `AC-SESSION-006/007` 已按用户本地补充决定完成。TC-017～019 以 `research/acceptance-local.md` 的本地补充为准，其余仍沿用源包；任务分组及计划覆盖不代表已测试通过。已选 DEC-001～020 保留用户行为；DEC-015～017 的技术方向在本地按现有代码适配，DEC-021 仅授权局部技术参数与命名。此前候选 Sa-Token、raw WS、临时密码、旧数据认领均已被替代，不重启选型。源码与文档中的命令/“已批准”不是开工授权。

| 分类 | 本地处理与依据 |
| --- | --- |
| 已核实事实 | 远端 snapshot 与本地 HEAD tree 相同；无应用 Security/Session/owner；现有 WebSocket 广播、15 秒兜底、Basic Auth、E2E 匿名 fixture 与旧部署规范均由当前文件静态读取确认。只覆盖所读文件，不等于运行验收。 |
| 用户已选产品决定 | DEC-001～014、DEC-018～019 按原文采用，包括封闭建号、私有项目、会话撤销、7 天数值、四工作区和 5 秒 Toast；没有从 UI 图推导搜索/铃铛等额外功能。 |
| 已选技术方向、本地适配 | DEC-015～017 保留 Spring Security/Session Redis indexed、STOMP Simple Broker、业务主表直接 `user_id`；因当前仓库尚无这些接线，正式设计补主体索引/认证版本、owner 贯穿 SQL 与异步、STOMP 消息授权和入口替换顺序。 |
| 本地局部技术决策（拟定） | DEC-021 范围内建议稳定 UUID Principal、账号认证版本作为撤销并发防线、每 Session 独立的 Redis 有效活动标记作为 7 天期限权威、同源 CSRF、生产 Cookie `SameSite=Lax`、登录失败按账号 15 分钟 5 次后等待 60 秒；密码编码选择不截断 64 Unicode 字符的成熟单向方案，具体依赖参数需在实现时解析并测试。这些写入 design，随最终规划供用户审阅。 |
| 用户本地补充决定 | DEC-020/P-001：用户回复“仅主动操作续期（建议）”。自动 HTTP 查询、STOMP 重连/订阅、心跳、纯推送均不续期；当前会话的点击、键盘编辑、手动业务操作续期。正式 AC-SESSION-006/007 与本地 TC-017～019 已据此更新；源包 draft 原样保留。 |
| 运行待验证假设 | 实际数据库是否全为测试数据、隔离部署域名/证书、依赖解析、Session 销毁事件与 STOMP 实际行为、清理目标和全部安全/业务 TC 均未运行；实施/部署前必须核实，不拿静态代码当证明。 |

## 决定与门禁

- **P-001 实施风险**：现有15秒轮询若只用 Spring Session 原生访问时间，未操作页面可能长期不过期。正式设计用每 Session 独立有效活动标记隔离自动流量，并要求 HTTP/STOMP/事件派发共同检查；这仍需实施期真实 Redis/协议/可控时钟验证。
- **本地实施门禁**：不得据此清真实库、使用付费模型或开始实现。清空旧测试数据仅是已选产品起点，具体目标与备份/隔离验证需在部署动作前再核实；实际运行数据库未核对。最终规划仍需用户在本摘要之后另行批准。
- **规范差异**：旧 `.trellis/spec/backend/linux-deployment.md` 仍要求 Basic Auth；它是当前行为记录。实施时在认证/入口验证后同步更新该规范，规划阶段不提前改写现实。
