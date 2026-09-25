---
handoff_id: WB-20260925-auth-isolation-8cbe4aee
revision: 1
status: draft
repository: yibccc/ai-workbench
snapshot_branch: master
snapshot_commit: a4f86e48c1cd76c910b2fae9c94bfec6851277f4
code_verified: true
task_size: complex
product_decisions: pending
---

# ai-workbench：登录、用户管理与私有空间交接

整理日期：2026-09-25。分类为complex，是因为既定范围同时触及认证/会话、数据库归属、异步报告、实时协议、UI与部署，不表示扩展产品范围。

**已确认选择在本revision冻结；包整体仍为draft，仅因P-001“空闲活动分类”尚未清楚。** `product_decisions: pending`不撤销其他已确认选择。`code_verified: true`只指下面明确列出的相关代码/规范已静态核对，不代表全库审计、执行或测试。`ready-for-local-review`即使后续使用也只表示可交本地核对，不能作为编码许可。

## 1. 合同、实际产物与使用边界

已实际读取Library中的`ai-workbench-dev-sop-v2.zip`内`ai_workbench_sop_v2/repo/docs/dev-sop/HANDOFF-CONTRACT.md`，版本2.0。按该真实字段格式组织。本轮仓库固定快照的`docs/dev-sop/HANDOFF-CONTRACT.md`读取返回404，因此不声称合同已经在目标仓库安装；本地如有不同合同版本应报告差异。

本次用户明确不需要最终本地执行计划，其约束优先于合同中可选的实施建议。本包不生成Trellis task.json、implement.jsonl、check.jsonl，也不创建任务、覆盖AGENTS/spec、写实现代码、清库、提交/推送/部署或付费模型调用。

| 真实相对路径 | 职责 | 可作为何种依据 |
| --- | --- | --- |
| HANDOFF.md | 元数据、文件清单、决定/未知、证据和交接门禁。 | 交接入口；不是本地任务状态。 |
| requirements.md | 13项FR、73项AC、范围/非目标、不得破坏行为。 | AC唯一定义来源；P-001部分draft。 |
| solution.md | 已选方案、证据、数据/接口/状态/兼容/回滚影响和本地核实项。 | 待本地核实适配的设计输入；不是补丁或执行计划。 |
| acceptance.md | 40个TC的前置、操作、预期、层级及73项AC覆盖索引。 | 验证计划；无执行结果。 |
| assets/ui-concept.png | 本轮用户已看过的UI生成示意图原文件副本。 | 视觉参考，不是运行截图、真实数据或UI源码。 |
| references/HANDOFF-CONTRACT.md | 实际读取的合同2.0原文副本。 | 格式/交接约束来源，不覆盖本地现有规范。 |
| SHA256SUMS | 上述6个内容文件的SHA-256。 | 内容完整性；不包含自身，避免递归摘要。 |


合同源文件SHA-256：`e9bc8d61dd64b4dad8e8ee8c0e29c6b47ae306fb0e186d97c113b2ac949addc8`。所有包内文件均为普通文件；无脚本、可执行载荷、符号链接、数据库备份或密钥。UI图中示例文本不作为真实个人工作数据。

此前用户提出过UI代码/可预览诉求；实际对话只产生了生成图，没有可验证的UI源码附件或浏览器预览。这是交付边界，不能写成“UI已经实现”。本次按最新请求冻结设计，不借机补写实现代码。

## 2. 决策登记

分类：`product`为产品行为；`technical`为技术选择。状态：`selected`已选、`proposed`待选、`delegated`本地局部细化。以下依据来自本轮对话，不引用用户未说过的授权。

| 编号 | 类型/状态 | 准确摘要 | 确认依据 |
| --- | --- | --- | --- |
| DEC-001 | product / selected | 少量受邀熟人/同事；由应用管理员创建账号；不开放自助注册；需要 1 个普通权限测试账号。 | 用户：“我邀请的少量熟人或同事……暂不开放任何人自助注册，以及一个测试账号”。 |
| DEC-002 | product / selected | 用户名+密码；不要求邮箱/手机号；无邮件验证、验证码登录、自助找回或记住我。 | 用户对“用户名+密码，无邮箱/手机号，无自助找回”回复“同意”；后续明确无记住我。 |
| DEC-003 | product / selected | 用户名全局大小写不敏感唯一，创建后不可修改；无独立显示名。 | 用户对用户名规则回复“确认”。 |
| DEC-004 | product / selected | 管理员创建/重置时直接设置普通密码；用户可自改；不强制首次登录/重置后改密。 | 用户：“Spring Security 管理员设置普通密码，用户登录后可自行修改，但不强制。” |
| DEC-005 | product / selected | 密码 8–64 个字符；无字符种类组合、无定期强制改密；允许空格、粘贴、密码管理器；本地弱密码 denylist；不接外部泄露查询。 | 用户：“8-64，其他确认”；对本地 denylist 回复“同意”。 |
| DEC-006 | product / selected | 失败登录限流/等待但不自动禁用账号；成功后清除该账号的失败登录状态。 | 用户：“8-64，其他确认”；随后确认该摘要及本地 denylist。 |
| DEC-007 | product / selected | 固定 ADMIN/USER，允许多管理员；可调整角色；至少保留 1 个启用管理员；满足该约束时允许禁用/降级自己。 | 用户分别对多管理员、角色管理、可操作自己回复“确认”。 |
| DEC-008 | product / selected | 账号列表、创建、启用/禁用、重置密码、调整角色；不删除账号；重新启用保留个人数据。 | 用户确认用户名/账号边界与最小 UI 方案。 |
| DEC-009 | product / selected | 所有业务数据完全私有，项目也私有；管理员不获得跨用户业务读写权限；他人业务资源按不存在返回 404，管理权限不足返回 403。 | 用户：“按你推荐来”；后续确认私有边界及“他人资源404”。 |
| DEC-010 | product / selected | 允许多端同时登录；退出仅撤销当前会话；同一 userId 的多个会话访问同一私有空间。 | 用户：“允许多端同时登录”，并确认后续会话规则。 |
| DEC-011 | product / selected | 禁用、管理员重置密码、角色变化均撤销目标用户全部会话；自改密码保留当前会话、撤销其他会话。 | 用户确认全设备失效、角色变化失效、自改密码当前设备保留等规则。 |
| DEC-012 | product / selected | 连续 7 天无活动过期；正常使用续期；没有记住我。活动事件分类尚未确认，见 P-001。 | 用户：“无‘记住我’选项，会话连续 7 天无活动后过期”。 |
| DEC-013 | product / selected | 现有库全是未投用测试数据；不迁移/认领这些数据；首次投入多人使用前从干净状态开始。 | 用户：“这个还没有投入使用，数据库现在全是测试数据，我认为直接清空数据库开始”。这是用户陈述，非网页实查数据库。 |
| DEC-014 | product / selected | 仅当用户表为空时，通过部署配置初始化首个管理员；已有任意用户时不新增、不覆盖、不重置；测试账号走正常建号流程。 | 用户对部署配置初始化方案回复“确认”。 |
| DEC-015 | technical / selected | 采用 Spring Security + Spring Session Redis indexed；替代旧 Nginx Basic Auth；保持现有反代/HTTPS及内部健康检查边界。 | 用户选 Spring Security，并确认移除旧 Basic Auth；后续接受整体 Session 方案。 |
| DEC-016 | technical / selected | STOMP + Spring 内置 Simple Broker + /user 定向消息；保留握手 /ws/events；订阅 /user/queue/workbench-events；业务写入仍用 HTTP；不引入 RabbitMQ、SockJS、业务广播 topic。 | 用户：“我觉得改成STOMP Message Broker吧……”，随后确认所述方案。 |
| DEC-017 | technical / selected | 主业务实体用 user_id 直接归属，子记录继承父归属；无 workspace/tenant、RLS、独立用户数据库。 | 用户确认隔离方案 A 及整体方案；不增加共享空间。 |
| DEC-018 | product / selected | 四个既有业务工作区不重构；桌面侧栏底部账号入口、手机顶栏账号入口；用户管理为辅助页，不加第五个主业务导航。 | 用户确认 UI 方案并要求代码/预览；之后确认生成图。图中额外装饰不新增需求。 |
| DEC-019 | product / selected | 登录失效检测后立即回登录页；过期/撤销提示及临时操作反馈沿用现有 5 秒 Toast；无新的失效页、常驻条或阻断模态框。 | 用户：“像这种seesion过期的提示都采用现在的5s弹窗”，并确认立即跳转规则。 |
| DEC-020 | product / proposed | P-001：后台轮询、自动重连/订阅、STOMP 心跳、纯推送接收是否刷新空闲期限，尚无明确分类决定。 | 没有明确确认依据；不按框架默认值替用户选择。 |
| DEC-021 | technical / delegated | 密码编码器及参数、限流阈值/窗口、弱密码表内容、组件/类与新端点命名、锁/撤销协调、依赖实际解析及验证命令由本地核实细化。 | 仅限兑现已确认行为的局部细化；影响产品、兼容或风险时须回到用户，不能借此加功能。 |


### 2.1 已被替代的探索内容

普通密码取代“临时密码/必须首次改密”，因此无must_change_password状态或强制改密页面；8–64取代15–128；清空旧测试数据取代旧数据归属/回填迁移；Spring Security取代Sa-Token候选；STOMP取代继续扩展raw WebSocket handler；5秒Toast取代Session过期常驻条/页面。

保留原AC编号，改变后的唯一正文在requirements。未经批准的“每用户额度/密钥”“团队空间”“公开注册”“审计后台”“通知中心”等不因讨论公网而成为需求。

## 3. 待决项、技术未知与风险

| 标识 | 分类 | 内容 | 接入处理 |
|---|---|---|---|
| P-001 | 产品待决，阻塞相关空闲边界冻结 | 7天数值与无记住我已定；无人操作时后台HTTP轮询、自动重连/订阅、STOMP心跳或纯推送接收是否刷新期限未定。 | 保留draft；本地先说明事实影响，再仅就活动分类取得用户决定；AC-SESSION-006/007相关部分与TC-019据此更新，不默用框架默认值。 |
| L-001 | 本地核实 | 工作区HEAD、未提交变更、活动任务、已有规范/合同版本。 | 只读核对并记录差异；不能清理/stash/切任务来掩盖冲突。 |
| L-002 | 本地核实 | Security/Session/STOMP实际依赖解析、indexed索引、销毁监听、CSRF/Cookie与撤销竞争。 | 保留已选框架，验证真实授权后果；不写“加依赖自动完成”。 |
| L-003 | 本地核实 | 服务/controller/mapper全调用链、子表归属、AI上下文/恢复、唯一约束和版本锁。 | 补读并按既定owner隔离验证；不改原业务规则。 |
| L-004 | 本地局部细化 | 密码编码参数、denylist来源/大小、有限限流阈值/窗口、输入计数/编码、具体接口/组件命名。 | 不捏造已确认数值或路径；用户可见规则若需增加/改变则回用户，不用delegated偷选产品行为。 |
| L-005 | 本地核实 | 清库目标、Flyway新结构、初始化配置接线及旧auth构建脚本清理。 | 用户同意清旧测试数据不是授权现在操作任意库；不得改写已应用迁移。 |
| L-006 | 本地核实 | Toast切页清除、旧身份草稿/pending/迟到响应、STOMP重连/轮询与过期分类。 | 保留现有UI结构，在身份边界适配，不新建通知体系。 |
| L-007 | 实际交付缺口 | UI参考图已有，代码/运行预览未提供。 | 把图作为设计输入；后续代码只在本地获实施授权后产生并验证。 |

主要风险：漏掉某条API/SQL/子关联造成越权；Session删除与角色/密码变更竞争；HTTP已下线但STOMP仍接收；清错库；旧无owner版本连接新多人库；生成图的装饰被误扩成需求。对应验收与回滚限制分别在requirements、acceptance、solution中明确。

## 4. 仓库快照与静态读取范围

仓库：`yibccc/ai-workbench`。冻结时通过GitHub连接器读取`master`分支对象，HEAD为`a4f86e48c1cd76c910b2fae9c94bfec6851277f4`，commit时间为2026-09-24T14:50:09Z；该commit对象的tree为`bd0cc3c1cc3a4cd563ce3c7292898a1404b0441d`。**metadata里的snapshot_commit是commit，不是tree或文件blob。**

分支证据：[master分支对象](https://api.github.com/repos/yibccc/ai-workbench/branches/master)；[固定commit](https://github.com/yibccc/ai-workbench/commit/a4f86e48c1cd76c910b2fae9c94bfec6851277f4)。

### 4.1 本次固定commit复读

下表每个文件均使用完整commit作为请求ref读取，日期2026-09-25。只确认所列范围。

| 编号 | 文件/固定来源 | 读取范围 | 文件blob SHA | 用途 |
| --- | --- | --- | --- | --- |
| R-01 | [AGENTS.md](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/AGENTS.md) | 全文 | c9c4c666e4818cde897d46e81483a9c233205f46 | Trellis工作规范入口；不包含实施授权。 |
| R-02 | [.trellis/workflow.md](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/workflow.md) | 1–240行 | 2456a33cfbedd63ae58136b95e9770ed897008b1 | 规划/任务创建/实施授权边界；未执行任何生命周期动作。 |
| R-03 | [.trellis/spec/backend/database-guidelines.md](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/spec/backend/database-guidelines.md) | 1–150行；探索阶段另读过部分后续章节 | bd01d31e1610305bd9c69974439b4ef545befc79 | 不可改写迁移、时间、关联、MyBatis、待办版本合同。 |
| R-04 | [.trellis/spec/frontend/directory-structure.md](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/spec/frontend/directory-structure.md) | 全文 | 8c5491976102967477119e9dd2b0212cf707cb83 | 四工作区、保留页、Toast、分页和未保存/本地错误语境。 |
| R-05 | [backend/pom.xml](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/backend/pom.xml) | 全文 | 95eb13478b2d0378e395975f93e7646e9e636934 | 声明Boot3.5.16/Java17与现有依赖；非effective POM。 |
| R-06 | [frontend/src/components/ToastProvider.tsx](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/frontend/src/components/ToastProvider.tsx) | 全文 | 582e6e113460a4924739526e3b9828fb696b0685 | 5000ms计时、page绑定、切页清理及body portal。 |
| R-07 | [frontend/src/hooks/realtime.ts](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/frontend/src/hooks/realtime.ts) | 全文 | 7e4a5829dd543372ac58171080a7d9966f3c50ed | raw WebSocket、15秒查询、去重重连、不分账号的pending存储。 |
| R-08 | [.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/architecture.md](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/architecture.md) | 全文 | 40941df8bf361ca085377a15724f4f78d279a64a | 原业务事务、AI、恢复、报告与部署职责。 |


另只读了该commit的`.trellis/tasks`与`archive/2026-09`目录元数据，发现`00-bootstrap-guidelines`与归档目录，并读取上列D10架构说明。没有声称完整审阅所有任务，也没有依据远端目录判断本地是否正在做别的任务。

### 4.2 探索阶段历史master读取（不伪装为固定ref）

这些请求当时使用master。下面有blob SHA的内容可追溯到对应文件版本；本次没有逐个用固定commit复读，因此不把所有历史读取伪装成一致快照。代码事实仅限当时实际内容，本地须核对与目标HEAD差异。

| 编号 | 历史文件 | 读取ref / blob SHA | 说明 |
| --- | --- | --- | --- |
| H-01 | README.md | master / [31634aa79f84b219b5c41f919bf99da0082cca74](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/31634aa79f84b219b5c41f919bf99da0082cca74) | 四个工作区、Docker/Windows部署及既有回归说明；说明不等于测试已执行。 |
| H-02 | .trellis/spec/backend/directory-structure.md | master / [3382222df619d7aa1e1a5400755e58106cc4137a](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/3382222df619d7aa1e1a5400755e58106cc4137a) | Controller→Service接口/实现→Mapper；事务与分页边界。 |
| H-03 | compose.yaml | master / [c5ce5e7efc8202e6daeb72eae757cc6dd1d69f7a](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/c5ce5e7efc8202e6daeb72eae757cc6dd1d69f7a) | PostgreSQL17.6/Redis7.4.2镜像声明、应用与共享入口凭据配置。 |
| H-04 | deploy/nginx.conf | master / [6c33db112ff61a069c91ff8f923ad660ed3cad40](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/6c33db112ff61a069c91ff8f923ad660ed3cad40) | Basic Auth及API/WS反代、内部health；未测试真实入口。 |
| H-05 | backend/src/main/resources/mapper/ProjectMapper.xml | master / [8d1d2a093dd8c3a9f743b310fdc908928be97955](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/8d1d2a093dd8c3a9f743b310fdc908928be97955) | findAll/findById/rename/archive等无owner条件。 |
| H-06 | backend/src/main/resources/mapper/ReportMapper.xml | master / [2405416b2f5c2fdc2dec9ffa1bd80918728aedd4](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/2405416b2f5c2fdc2dec9ffa1bd80918728aedd4) | 报告请求、候选来源、版本锁/链、来源分页/软删除。 |
| H-07 | backend/src/main/java/com/aiworkbench/events/WorkbenchEventHub.java | master / [05299fbcb12f73529b86c1e0849b085e92791282](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/05299fbcb12f73529b86c1e0849b085e92791282) | 维护全连接集、after-commit通知与全体广播。 |
| H-08 | backend/src/main/java/com/aiworkbench/events/WorkbenchWebSocketConfig.java | master / [1784644da30a91012fb63f08a2e72a4879567f68](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/1784644da30a91012fb63f08a2e72a4879567f68) | /ws/events及Origin校验；不是STOMP。 |
| H-09 | frontend/src/App.tsx | master / [07411a47193059818d41e6a743fa28ebe0ef3456](https://api.github.com/repos/yibccc/ai-workbench/git/blobs/07411a47193059818d41e6a743fa28ebe0ef3456) | 四工作区组成、RetainedView与ToastProvider位置。 |


另在探索阶段以master实际读过下列内容，但相关可见输出未保留各文件blob SHA，故单文件版本标为未知，不补造：

- `backend/src/main/resources/mapper/TaskMapper.xml`、`WorkRecordMapper.xml`、`InputMapper.xml`全文。
- `backend/src/main/resources/db/migration/V1__initial_workbench_schema.sql`、`V2__task_management.sql`、`V4__task_completion_consistency.sql`、`V5__ai_capture_inputs.sql`、`V6__capture_recovery_and_revert.sql`、`V8__daily_report_versions.sql`、`V9__weekly_report_versions.sql`、`V10__report_failure_diagnostics.sql`、`V11__backfill_report_source_count.sql`、`V12__daily_report_soft_delete.sql`。其中部分早期输出有blob，本表不依赖这些补录；不声称读过V3/V7全文。
- `frontend/package.json`、`frontend/src/main.tsx`、`frontend/src/api/http.ts`、`frontend/src/components/layout/navigation.ts`、`AppShell.tsx`、`frontend/src/components/DialogProvider.tsx`、`Icon.tsx`；`frontend/src/styles.css`的1–260行及320行至文件尾。
- `backend/src/main/resources/application.yml`、`application-e2e.yml`。

这些文件名按其父目录解析；例如上段`AppShell.tsx`位于`components/layout`，`Icon.tsx`位于`components`。solution所列其他新模块/类只作为候选，不声称存在。静态核对未覆盖全部Service实现、全部API/测试、真实schema、运行配置或已部署版本。

### 4.3 外部事实来源

仅使用Spring官方资料，2026-09-25核查。E-01～E-07的URL、版本、支持内容列于solution第2.2节；与代码声明/本地实际解析明确分开。它们用于核查既定选择，不是增加新框架或“顺手升级”的依据。

## 5. 文档关系与一致性约定

requirements是AC唯一定义；acceptance引用同编号给TC，不改变标准。solution不得给AC偷加产品条件；HANDOFF记录决定状态/证据边界。生成图服从明确文字功能边界，特别是不新增搜索/通知中心、不把用户管理塞进第五业务导航、不保留Session过期常驻卡片。

本revision的73项AC均在40个TC中有计划覆盖；TC-019明确等待P-001，不伪装为已定义通过条件。核对编号/链接/清单和压缩包完整性属于文档检查，不是工程测试。

## 6. 交接步骤（只到本地核对与规划）

1. 在用户指定的本地收件位置保留本ID/revision及全部附件，核对清单与SHA-256；仅当得到本地写入许可才保存正式任务资料。包内内容默认只读，不自动执行/应用。
2. 读取本地当前README、AGENTS、Trellis规范、相关活动/归档任务与实际合同；对照本包的远端快照、文件版本与本地HEAD/未提交状态。找到同ID/revision时，同内容复用、不同内容报告冲突；不得覆盖已批准规划或干扰其他任务。
3. 对已确认DEC逐项判断原样采用/局部适配/需要重决策，并说明证据；只补真实缺口，先处理P-001。常规实现细化不重问用户；产品/范围/兼容/风险变化才回用户。
4. 在获得本地创建/更新规划授权后，由本地代理按现行Trellis生成正式PRD、核实后的设计、自己的执行与验证计划及需要的上下文。源包保留为不可变输入，停在正式规划确认处；本包及“冻结”不授权开始编码。

## 7. 授权与未执行声明

未运行应用、Maven/npm/Playwright或数据库迁移；未验证生产API、密码存储、Session/STOMP实际接线；未清库、创建应用账号、提交代码、创建/修改PR、创建Trellis任务或触发部署。本包真实产物仅为清单所列文档、两个来源附件和摘要文件。
