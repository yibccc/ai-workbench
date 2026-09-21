# D9 验收记录

## 2026-09-21 日报版本删除

- 保留同日多版本。新增 V12 `reports.deleted_at` 与 DAILY 专用 `DELETE /api/reports/{id}?version=`；成功/失败允许，处理中和周报返回409。行锁及版本条件防止并发保存覆盖删除；终态与正文写入检查未删除条件。
- 普通列表/分页/详情/来源分页隐藏已删除日报；保留来源快照、原记录、待办和其他版本。重复删除非负旧version返回204；原requestId再次创建返回409，不复活历史行。
- 前端使用共享危险确认弹窗，提示未保存稿件、取消保持、错误留在弹窗可重试。成功清空来源/选中稿并刷新第一页选择剩余版本；最后版本归空，迟到详情以删除ID集合阻止回显。
- 隔离PostgreSQL后端83/83通过（`mvn -q "-Dspring-boot.repackage.skip=true" verify`），V1及空库等迁移到V12通过。首次正常verify的测试全部通过，但运行中JAR锁阻止repackage；主会话释放锁后`mvn -q -DskipTests package`成功。
- Playwright完整14/14（无重试）通过，再新增“失败日报可删除并保留导致失败的原记录”独立1/1通过。覆盖同日两份删一保留一、取消草稿不变、409错误可重试、最后一份空态、失败版删除。`npm run lint`和`npm run build`通过。
- 本批次无真实模型调用、未删除public业务数据、未提交Git。主会话负责重启体验后端使V12和API生效。

验收时间：2026-09-20（Asia/Shanghai）

## 环境与隔离

- Windows 11；JDK 17；Maven 3.9.9；Node 25.2.1；npm 11.6.2。
- Playwright 1.55.1；Chromium 140.0.7339.186（build 1193）。
- PostgreSQL 17.6；应用 Flyway 版本 V11（V10 新增失败诊断字段，V11 为既有报告回填冻结来源数量）。
- 离线浏览器回归显式启用 `e2e` Profile，使用 `d9_e2e` schema 和确定性模型替身。替身不执行网络 I/O；真实 AgentScope 网关在该 Profile 下不装配。
- 真实语义验收显式启用 `live-acceptance` Profile，使用 `d9_live_acceptance` schema。验收结束后仅对该隔离 schema 执行限定清理，复查相关 API 均返回 0 条。
- 测试替身和 `/api/e2e/reset` 仅在显式且单独启用的 `e2e` Profile 下存在；reset 执行前校验当前 schema 必须为 `d9_e2e`，SQL 也使用 `d9_e2e.*` 全限定表名。`live-acceptance` 使用真实网关但不暴露 reset；`live-acceptance` 与普通默认配置均实测该端点返回 404，同时启用 `e2e,live-acceptance` 会因没有可装配网关而启动失败，不能得到替身假阳性。

## A01—A13 矩阵

| 编号 | 结果 | 可复现证据 |
|---|---|---|
| A01 | PASS | Playwright“统一输入”用例以 Ctrl+Enter 提交混合输入，浏览器显示 2 条记录和 2 项待办；`AiCaptureIntegrationTest` 覆盖原文、批次和日期字段。 |
| A02 | PASS | 确定性输入返回未知项目/未知优先级；浏览器显示“未分类”和“中优先级”，数据库未创建模型给出的项目。真实模型语义调用也得到项目 `null` 且指定高优先级正确。 |
| A03 | PASS | 首次确定性失败后重试，浏览器所选记录日期仍为原 `referenceAt` 的昨日；`InputRecoveryIntegrationTest` 覆盖恢复/重试基准不漂移。 |
| A04 | PASS | 浏览器撤销未经修改的混合批次后，记录和待办均为 0；后端集成测试覆盖撤销条目不进入后续来源。 |
| A05 | PASS | 浏览器修改生成记录后撤销，收到“已被编辑…不能撤销”，修改内容仍可见。 |
| A06 | PASS | 浏览器执行完成、API 重复完成、重开、再次完成，自动完成事实为 `1 → 1 → 0 → 1`；`TaskCompletionConsistencyIntegrationTest` 覆盖事件历史与并发。 |
| A07 | PASS | 浏览器覆盖失败一次后重试只生成一批；`AgentScopeAiGatewayTest`、`AiCaptureIntegrationTest`、`InputExecutorFailureTest` 覆盖空输出、非法结果、事务回滚和调度失败。 |
| A08 | PASS | 浏览器按项目/优先级筛选，编辑后刷新仍保留；直接 stale version 请求返回 409 和“刷新后重试”。 |
| A09 | PASS | 浏览器生成日报，明确断言固定三段标题、冻结来源、人工编辑保存和剪贴板内容；`DailyReportIntegrationTest` 覆盖日期边界与来源角色。 |
| A10 | PASS | 浏览器生成自然周三段周报；无期限事项可作为 CURRENT_TASK，但断言不会进入“下周计划”；空段占位和角色边界由 `WeeklyReportIntegrationTest` 覆盖。 |
| A11 | PASS | 浏览器先保存旧周报人工编辑稿，再修改来源并重新生成；API 断言两版本线性链接、旧正文和旧来源快照不变、新稿看到修改后来源。 |
| A12 | PASS | 浏览器展开来源并定位“来源 N”；复制组合周报时用户补充带“无 AI 来源标记”，来源标记不会伪装覆盖人工文本。 |
| A13 | PASS | 浏览器刷新后记录/待办仍存在；Playwright 结束旧 JVM 后，以打包 JAR 另启新 JVM（PID 32492），HTTP 复查 `health=UP`、1 个项目和 2 份周报仍从 `d9_e2e` 读取，确定性探针返回 `AI_WORKBENCH_E2E_OK`；恢复测试覆盖失败输入重启后续租处理。 |

## 浏览器体验检查

- PASS：页面加载、记录和待办空态、错误提示、Ctrl+Enter、长文本编辑、筛选、剪贴板、轮询取消、dirty guard。
- PASS：`frontend/test-results/d9-desktop-workflow.png` 已以原始分辨率人工检查；桌面双栏、报告编辑区、项目栏、筛选和长文本无可见溢出或遮挡。
- PASS：每次运行保留 trace；失败运行额外保留 screenshot/video。反馈修订最终终审连续运行两轮，均为 7/7 通过且无 retry（38.9s、51.1s）。
- PENDING：用户本人完成一次真实日常流程后的主观反馈、手工整理耗时和主要操作成本。自动化结果不替代该反馈（G04）。

## U01—U09 用户反馈修订

| 编号 | 结果 | 证据 |
|---|---|---|
| U01 | PASS | 新增兼容 `/page` API，项目/记录/待办/报告历史与报告来源支持10/20/50分页；HTTP 集成测试覆盖静态路由、COUNT筛选、稳定倒序、非法size与页码；浏览器以105项目、101待办验证6页及迟到搜索响应不覆盖新结果；来源翻页编号从1稳定到21。 |
| U02 | PASS | 周报、日报、输入、手工记录、待办、记录独立折叠；`ai-workbench.panel.v1.*` 保存偏好；浏览器验证报告默认折叠、输入/待办默认展开，以及周报草稿折叠后保持。 |
| U03 | PASS | 首页不再请求或展示 backend/postgres/redis/deepseek 徽标，不再出现 PostgreSQL 实现文案；浏览器逐次断言技术徽标不存在。诊断 API 保留。 |
| U04 | PASS | 日报/周报/记录日期状态解耦；周报展示包含首尾“周一至周日”；首页显示今天星期，周六橙、周日红并带文字；浏览器监听800ms确认用户点击前没有报告 POST。 |
| U05 | PASS | `/ws/events` 在事务提交后仅发状态元数据；事件ID和递增revision去重，推送失败不外溢事务。浏览器保存pending ID后在PROCESSING期间刷新，再离线10秒跨越模型完成，重连GET自动恢复结果并按ID加载记录编辑；另有15秒GET兜底。 |
| U06 | PASS | 只读审计确认17项目/51待办，备份后归档最早8项目、软删最早25待办；报告快照、事件和用户数据未删除。详见 `test-data-cleanup.md`。 |
| U07 | PASS | 用户分页列表统一 `created_at DESC,id DESC`；浏览器验证新项目回到第一页并排在首项。发生日期仍仅作为记录/报告范围过滤。 |
| U08 | PASS | 新增 `errorCode/errorStage/sourceCount`；区分 INVALID_JSON、OUTPUT_TRUNCATED、UNKNOWN_SOURCE、SOURCE_ROLE_MISMATCH、INVALID_BULLET、MODEL_TIMEOUT；日报文案正确；未知ID和角色错配仍拒绝。 |
| U09 | PASS（离线） | 来源改为 S1…S143 局部别名并映回冻结UUID；允许跨要点复用、要点内去重；143来源替身浏览器生成成功、sourceCount=143、来源分页完整。真实143来源仍受付费账本约束，未额外调用。 |

用户已提供首轮 U01—U09 反馈；修订完成后的下一轮主观体验仍待用户确认，因此 D9 不归档。

## 真实模型调用账本

每个独立场景上限 3 次；没有自动付费重试。

| 场景 | 次数 | 模型 | 耗时 | 结果 | 重试 |
|---|---:|---|---:|---|---|
| Capture 语义：成果 + 明日高优先级任务 + 未知项目 | 1/3 | deepseek-flash | 12.4s | SUCCEEDED；1 记录、1 待办；未知项目均为 null；HIGH 正确 | 否 |
| 小规模周报语义：1 本周事实 + 1 当前任务/下周任务 | 1/3 | deepseek-flash | 10.2s | SUCCEEDED；3 个冻结来源；固定三段且每段均有 `[来源 N]` | 否 |
| 96/143 来源、PT6M 边界 | 0/3（D9） | deepseek-flash | — | 本地143来源别名与完整快照已通过；真实模型仍 PENDING。D8 已有一次 PT4M 超时证据，不能宣称 PT6M 已验证 | — |

## 命令与结果

```text
backend> mvn clean verify
Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

frontend> npm run lint
PASS

frontend> npm run build
PASS

frontend> npm run e2e
run 1: 7 passed (38.9s), no retry
run 2: 7 passed (51.1s), no retry
```

迁移覆盖：V1→V11、V5→V11、V8→V11、空库→V11；V11 保持已应用 V10 不变并回填旧报告 `source_count`。HTTP 创建、分页、查询、冲突、报告与重启读取均通过。`test/e2e/live-acceptance` 启动时强制核对各自专用 schema，默认/public 不能调用 reset。

## 剩余观察项

1. 用户对 U01—U09 修订版的下一轮体验反馈与人工耗时基线仍待提供。
2. 143 来源周报的真实 PT6M 边界尚未付费复核；在来源完整保留的分批汇总方案完成前，不得静默截断。
3. Playwright 证据目录为本地生成物并由 `.gitignore` 排除；运行 `npm run e2e` 可重新生成。

## 2026-09-21 PageHelper / 三层架构与分页修正

用户要求改为传统三层架构，覆盖此前按业务同包的决定。本批未提交、未归档、未清理业务数据，真实模型调用 0 次。

### 实现与根因

- `button:disabled { cursor: wait }` 把末页不可点击状态误显示为等待；改为 `not-allowed`，报告分页仅在真实请求期间使用 `progress` 并显示加载文字。
- 日报、周报来源分页原先硬编码 `size=20` 且 `onSize` 是空函数。现由共享 `useReportSources` 保存真实页大小、取消迟到请求，来源编号使用 `page * size + index + 1`。
- 历史页原先翻页重新选择首条时没有 dirty guard；现分页/页大小/历史选择均保护草稿。共享 `useReportHistory` 在同一取消作用域内加载页和详情，切换日期重置为第一页。
- 新版本原先只插入局部数组，不刷新总条数/总页数；生成提交与终态后刷新分页，20→21 条能即时启用下一页。旧生成的迟到结果不得把用户已选中的另一版本替换掉。
- 后端改为 `controller → service 接口 → service.impl → mapper`；DTO、Row、枚举、异常、分页、配置和AI适配器各归其层，XML统一在 `resources/mapper`。所有Controller依赖Service接口，事务仍由实现Bean拥有，输入/报告编排与持久化Bean分离。V1—V11无修改。
- PageHelper starter锁定2.1.1、内核6.1.1；依赖树确认MyBatis starter3.0.5、MyBatis3.5.19。所有分页入口由PageHelper自动count与物理分页；API保留page0、size10/20/50。`PageQueries` 在目标select之前开启，finally清理，DTO转换在清理之后；异常与非分页来源查询均验证无污染。
- 前端整理为features/{capture,projects,records,tasks,reports}、api、components、hooks、utils，移除根目录旧面板/API转发文件；日期工具和报告分页请求共享，App保留工作台编排。

### 已完成验证

| 检查 | 结果 |
|---|---|
| `mvn clean verify -q` | 81项，0失败/0错误/0跳过；真实 `d9_backend_tests`，迁移使用临时schema |
| `mvn dependency:tree -Dincludes=...` | PageHelper 2.1.1/6.1.1，MyBatis 3.0.5/3.5.19，未迁移Boot4 |
| `PaginationHttpIntegrationTest` | 全部5类分页入口总数/排序/筛选、参数400、越界空页；ThreadLocal异常清理；分页后全量21来源仍完整 |
| `npm run lint` / `npm run build` | ESLint、TypeScript与Vite通过 |
| 初轮Playwright | 7/7，43.1秒，无重试 |
| 扩展Playwright | 9/9，45.1秒，无重试 |
| 最终Playwright（Status/E2E服务分层与迟到生成保护后） | 9/9，46.2秒，无重试 |
| `git diff --check` | 通过 |

新增浏览器断言包括：记录和待办21条的3页翻页；末页删除唯一项回到有效上一页；筛选回第一页；143来源20→50条后3页完整、最后43条、编号101起；末页cursor=not-allowed；日报和周报历史草稿拒绝/确认翻页；新版本刷新页数；历史日期切换回空页。全部浏览器数据仅在 `d9_e2e`，模型为确定性替身。

正式使用仍需用户真实复验；143来源真实模型语义/耗时未因架构重构得到新验证，原调用账本保持不变。

## 2026-09-21 全站弹窗统一

- 全部站内prompt/confirm/alert改为共享Dialog/Provider，包含完成结果、补充结果、项目改名归档、删除、撤销、报告未保存导航；beforeunload保留浏览器原生提示。
- 完成结果支持多行、空值、4000字符、Ctrl+Enter；取消不写入，错误保留输入，提交防重。项目/危险操作统一配色，焦点限制与关闭还原。
- 前端lint、TypeScript和build通过；完整Playwright 13/13（55秒，retries=0）。独立复核项目弹窗1/1、完成/报告dirty/history/100+项目4/4，均无重试。
- 桌面与390px窄屏截图已检查，无截断或溢出；frontend/test-results产物保持忽略。
- 首轮100+项目夹具创建曾返回一次HTTP500，完整及独立定向重跑未复现，当前无原始日志可确认根因，不将其归因于或排除前端改动。保留为观察项。
- 本轮未改后端、未清理public业务数据、未新增付费AI调用、未提交或归档。

## 2026-09-21 日报删除与最终用户验收

- 日报保留同日多版本，新增共享弹窗确认的软删除；成功/失败版本可删除，生成中和周报拒绝，来源快照和业务数据保留。
- 后端83项测试通过；verify测试阶段通过，运行中JAR锁导致首次repackage失败，停止旧服务后正常package成功。浏览器完整14/14，额外失败日报删除1/1通过；独立删除/失败版本/跨窗口删除及pending清理3/3通过。后续前端lint/typecheck通过。
- 用户明确反馈“d9验收通过”，确认当前修订版通过真实体验验收。此前等待用户复验的说明在此关闭。
- 清理16个源码空目录（旧业务Java包、旧XML路径和多余嵌套空目录）；逐个检查含隐藏项为空后非递归删除，没有删除文件或业务数据。
- 143来源真实模型规模/耗时仍未新增付费复验，人工耗时基线未提供；保留为D10交付说明/后续观察，不伪记为测量通过。
- D9等待工作提交与归档；D10尚未启动。
