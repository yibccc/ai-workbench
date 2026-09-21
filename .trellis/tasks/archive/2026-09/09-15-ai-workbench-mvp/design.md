# 个人 AI 工作台 MVP 技术设计

> 本文件由原开发计划迁移而来，保留技术选型、模块边界、数据与接口约定、验证方式和 D1—D10 的原始实施细节。后续执行以子任务为单位推进。

**Goal:** 两周内打通自然语言录入、待办管理、当天汇总及周报编辑复制流程，由用户每天集中验收。

**Architecture:** React + Vite 前端连接一个按业务划分模块的 Spring Boot 3 后端。PostgreSQL 保存业务数据与原文，Redis 辅助去重和调用控制；AgentScope Java 调用 DeepSeek，业务层验证 AI 输出并负责事务。

**Tech Stack:** React、Vite、Java、Maven、Spring Boot 3、MyBatis、PostgreSQL、Redis、AgentScope Java、DeepSeek 官方 OpenAI 兼容 API。TypeScript 作为前端实现建议。

---

- 版本：v0.1
- 编制时间：2026-09-13 16:55:50 +08:00
- 时间来源：会话时钟工具，UTC 转 Asia/Shanghai；当前未提供 mcp.server_time。
- 需求依据：[prd.md](prd.md)
- 当前状态：只完成规划；尚无应用代码、数据库迁移或已通过的运行测试。
- 编制前项目现状：目录仅有已安装技能和技能锁文件，尚未初始化 Git 仓库。
- 执行方式：AI 开发、检查和交付可体验增量；用户每天集中验收。
- 排期口径：D1—D10 为实际启动后的开发日，两周为目标窗口，不按本文日期自动开工。

## 1. 推进方式

每天由 AI 提供一个可以实际操作的增量、当天验证结果和明确的验收步骤。建议用户每天集中体验 15—30 分钟；这是计划建议，不是用户已经承诺的投入时长。

反馈分为“阻断核心流程”和“体验调整”。前者优先修复，后者在 D9—D10 集中处理。技术学习与求职展示通过真实实现的设计说明和演示场景体现，不另建展示型功能。

开发顺序为：**先验证技术组合 → 建立可靠业务数据 → 接入自然语言 → 生成可追溯报告 → 真实试用。**

D5 开始用实际记录试用，第二周继续每日记录。若工作日数量不足，G01 的连续使用观察延续到交付后，不伪记为已经通过。

## 2. D1 必须解决的技术关卡

### 2.1 Spring Boot 3 与 AgentScope Java

已核验的官方 v2.0.3 BOM 使用 Java 17、Spring Boot 4.0.4 和 Spring Framework 7.0.7。它不能证明与 Boot 3 一定不兼容，也不能证明最新 Starter 可以在 Boot 3 下直接运行。[官方 BOM](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.3/agentscope-dependencies-bom/pom.xml)

- [ ] 检查本机 JDK、Maven、Node.js、Docker 和端口条件，记录实际版本；缺失环境按实施阶段需要处理。
- [ ] 保留 Spring Boot 3，验证一个固定的 AgentScope Java 版本及必要模块。
- [ ] 检查 Maven 依赖树，确认 Spring 核心依赖未被意外抬升至不兼容版本。
- [ ] 验证后端启动、一次普通模型调用和一次结构化提取。
- [ ] 如果当前 Starter 无法满足 Boot 3，验证必要模型模块加显式 Bean 配置，或固定兼容的历史版本；候选均须使用对应版本官方资料。
- [ ] 只有编译、启动和模型用例通过后才记录最终坐标与版本；不以强行排除依赖替代验证。

输出文件：子任务 D1 的 `research/technical-validation.md`。内容为版本矩阵、依赖树结论、实际验证命令、结果与选定配置。若真实 API 凭据尚未配置，记录为“真实调用未验证”，继续开展可独立验证的本地业务功能；该调用仍是 AI 功能验收前的必过项。

### 2.2 DeepSeek 接入

DeepSeek 官方目前给出的 OpenAI 兼容地址为 `https://api.deepseek.com`，官方入门示例使用 `deepseek-flash`。实施时再次核验并固定实际可用模型名，不从文档示例中猜测账户可用性。[官方入门](https://api-docs.deepseek.com/)

AgentScope 当前 DeepSeek 集成使用 `io.agentscope:agentscope-extensions-model-openai` 和 `deepseek:<model>`，该写法适用于对应版本，不保证历史版本 API 相同。[官方集成](https://java.agentscope.io/v2/en/integration/model/deepseek)

- [ ] 模型地址、模型名和凭据由后端环境配置提供。
- [ ] 验证所选版本的结构化输出方式。OpenAI 兼容不等于支持全部结构化输出参数。
- [ ] 用混合输入样本验证“一个工作记录 + 一个待办”，并检查日期、项目和优先级。
- [ ] 对模型返回值执行结构与业务校验；JSON 格式成功不等于字段含义正确。DeepSeek 的 JSON 输出也要求相应提示和参数。[JSON 输出说明](https://api-docs.deepseek.com/guides/json_mode/)
- [ ] 简单提取优先验证关闭思考的调用方式，以减少额外过程；具体参数以所选 SDK 和官方接口实测为准。
- [ ] 普通测试使用可控的模型替身；真实 API 只用于明确的集成验收，不要求每次回归联网调用。

## 3. 文件与模块划分建议

以下路径为实施阶段将创建的文件，不代表文件已经存在。后端建议根包为 `com.kira.workbench`。

| 路径 | 职责 |
|---|---|
| `frontend/package.json`、`frontend/src/main.tsx`、`frontend/src/App.tsx` | 前端启动与页面入口 |
| `frontend/src/features/capture/CapturePanel.tsx` | 统一输入、处理状态和撤销入口 |
| `frontend/src/features/records/RecordList.tsx` | 记录列表、补记与修改 |
| `frontend/src/features/tasks/TaskPage.tsx` | 待办管理与筛选 |
| `frontend/src/features/projects/ProjectManager.tsx` | 项目创建、改名和归档 |
| `frontend/src/features/reports/ReportPage.tsx` | 日报、周报、来源和版本编辑 |
| `frontend/src/lib/api.ts` | HTTP 请求和统一错误映射 |
| `backend/pom.xml`、`backend/mvnw.cmd` | 固定依赖与 Windows Maven Wrapper |
| `backend/src/main/java/com/kira/workbench/WorkbenchApplication.java` | 后端启动 |
| `backend/src/main/java/com/kira/workbench/input/InputService.java` | 输入状态、批次提交和撤销 |
| `backend/src/main/java/com/kira/workbench/task/TaskService.java` | 待办状态变化与关联完成记录 |
| `backend/src/main/java/com/kira/workbench/record/WorkRecordService.java` | 记录、发生日期、有效性 |
| `backend/src/main/java/com/kira/workbench/project/ProjectService.java` | 项目生命周期 |
| `backend/src/main/java/com/kira/workbench/report/ReportService.java` | 范围取数、生成、快照与版本 |
| `backend/src/main/java/com/kira/workbench/ai/WorkbenchAiGateway.java` | 业务使用的提取和报告生成接口 |
| `backend/src/main/java/com/kira/workbench/ai/AgentScopeAiGateway.java` | 固定版本 SDK 适配与异常转换 |
| `backend/src/main/resources/mapper/InputMapper.xml` 等按对象命名的映射文件 | MyBatis SQL |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | 初始表、约束和索引 |
| `backend/src/main/resources/prompts/extraction.txt`、`daily.txt`、`weekly.txt` | 三种功能的提示和输出约定 |
| `compose.yaml`、`.env.example`、`.gitignore` | 本机数据库、Redis 和配置示例 |
| `README.md`、D1 `research/technical-validation.md`、D9 `research/acceptance-log.md` | 启动、技术证据与体验结果 |

业务对象对应的 Controller、Mapper 和 DTO 放在同一业务包内，并使用一致的对象名。实现时按职责拆分，不将所有业务堆入统一工具类。

数据库迁移工具建议使用 Flyway；具体依赖与 Spring Boot 3 在 D1 一起固定。Docker Compose 用于本机 PostgreSQL、Redis 的可复现启动；先核实 Windows 环境是否可用。

## 4. 数据与接口约定建议

### 4.1 数据边界

- PostgreSQL 表建议为 `input_entries`、`projects`、`tasks`、`work_records`、`task_events`、`reports`。
- 业务标识采用 UUID；记录使用 `occurred_at` 和 `created_at` 区分发生与录入。
- 日期范围使用左闭右开区间，业务时区固定为 Asia/Shanghai。
- `tasks.status` 使用 `PENDING`、`COMPLETED`；`priority` 使用 `HIGH`、`MEDIUM`、`LOW`。
- 输入处理状态使用 `PROCESSING`、`SUCCEEDED`、`FAILED`、`REVERTED`；保存原文和进入 PROCESSING 为同一动作。
- 为输入请求标识建立唯一约束；为同一待办的当前有效自动完成记录建立唯一性约束。
- 完成、重开和删除写状态事件，保存必要历史；报告保存来源快照，不依赖随后可能被修改的源文本。
- 业务修改使用版本字段识别并发冲突；批次撤销检验相关条目是否发生后续变更。
- Redis 用于短期请求去重和 AI 调用并发控制；数据库约束和事务才是防止重复落库的最终保证。
- AI 调用在数据库事务外执行，拿到合法结果后用短事务写入完整批次；重启后将已无执行者的 PROCESSING 输入转成可重试失败状态。

### 4.2 HTTP API 草案

| 方法与路径 | 用途与关键约定 |
|---|---|
| `POST /api/inputs` | 接收 `text`、`requestId`，先保存原文，返回输入标识与处理状态 |
| `GET /api/inputs/{id}` | 获取处理状态、错误和生成条目，前端使用有上限的轮询 |
| `POST /api/inputs/{id}/retry` | 复用原文、基准时间和输入标识；成功批次不重复处理 |
| `POST /api/inputs/{id}/revert` | 整批撤销；存在后续变更时返回冲突，不覆盖用户新改动 |
| `GET /api/records`、`POST /api/records` | 查询记录，以及补记或 AI 失败时手动录入 |
| `PATCH /api/records/{id}`、`DELETE /api/records/{id}` | 修改或撤销手工记录；自动完成记录的有效性通过待办状态管理 |
| `GET /api/tasks`、`POST /api/tasks` | 筛选待办、手动新增 |
| `PATCH /api/tasks/{id}`、`DELETE /api/tasks/{id}` | 编辑、删除，携带版本信息 |
| `POST /api/tasks/{id}/complete`、`POST /api/tasks/{id}/reopen` | 幂等状态转换，同步关联记录 |
| `GET /api/projects`、`POST /api/projects`、`PATCH /api/projects/{id}` | 项目列表、创建、改名、归档 |
| `POST /api/reports` | 接收类型和周期；生成新版本，返回任务状态与报告标识 |
| `GET /api/reports`、`GET /api/reports/{id}` | 列出版本、查询处理状态、正文与来源快照 |
| `PATCH /api/reports/{id}` | 保存用户编辑的正文，携带版本信息 |

报告生成也需要 PROCESSING、SUCCEEDED、FAILED 状态和请求标识；同一请求重发复用同一结果，主动“重新生成”使用新标识形成新版本。长时间调用有超时上限，失败保留原有草稿。

## 5. 两周任务与每日验收

### D1：验证技术组合，建立可启动骨架

涉及：`backend/pom.xml`、`backend/mvnw.cmd`、`frontend/package.json`、`compose.yaml`、`.env.example`、D1 `research/technical-validation.md`。

- [ ] 记录本机运行环境并初始化项目结构、Git 和忽略规则。
- [ ] 完成第 2 节版本与调用验证，固定依赖，避免浮动版本。
- [ ] 启动前后端、PostgreSQL、Redis，建立健康检查。
- [ ] 生成无凭据的配置示例，写清启动命令和本机访问地址。

**AI 验证：** 依赖解析、后端启动、前端构建、数据库和 Redis 连接、最小模型调用。  
**用户验收：** 能打开本机页面，看到运行状态，了解已验证与未验证项目。  
**退出标准：** 技术路线有证据；若模型验证未完成，明确记录阻塞，D2—D4 可继续，D5 AI 验收前必须解决。

### D2：数据库、手工记录与项目

涉及：`V1__initial_schema.sql`、`ProjectService.java`、`WorkRecordService.java`、对应 Mapper、`RecordList.tsx`、`ProjectManager.tsx`。

- [ ] 创建业务表、输入请求唯一约束、完成记录约束与时间范围索引。
- [ ] 实现项目创建、改名、归档，以及手工记录新增、补记、修改、删除。
- [ ] 用数据库集成测试验证历史日期、项目归档后历史归属、刷新持久化。
- [ ] 为今日工作台接入真实记录列表，建立空状态和表单反馈。

**用户验收：** 创建一个项目，录入当天和昨天的工作，修改后刷新仍正确。  
**覆盖：** F02、F04；A03 的手工日期部分、A08 的持久化部分。

### D3：待办管理

涉及：`TaskService.java`、任务 Mapper/DTO、`TaskPage.tsx`。

- [ ] 实现标题、备注、截止日期、优先级和项目的新增、编辑、删除。
- [ ] 实现状态、项目、优先级、截止日期筛选。
- [ ] 写入默认优先级和输入校验，处理版本冲突。
- [ ] 验证无截止日期、逾期、高优先级、未分类和归档项目历史待办。

**用户验收：** 把当天真实待办录入，调整优先级与项目，再筛选检查。  
**覆盖：** F03、F04；A02 的默认字段部分、A08。

### D4：完成记录与状态一致性

涉及：`TaskService.java`、`WorkRecordService.java`、任务事件与关联记录 Mapper、相关页面操作。

- [ ] 先写状态转换集成用例，验证重复完成会导致重复记录的失败情形。
- [ ] 用事务和唯一约束实现完成、重开、再次完成及删除联动。
- [ ] 保存事件和历史结果，展示可补充的完成记录。
- [ ] 验证当前有效完成记录数量序列为 1、1、0、1，并检查并发点击。

**用户验收：** 完成一个真实待办、补充结果、重开再完成，核对工作记录。  
**覆盖：** F05；A06、A13 的业务状态持久化部分。

### D5：统一输入与 AI 自动拆分

涉及：`InputService.java`、`WorkbenchAiGateway.java`、`AgentScopeAiGateway.java`、`extraction.txt`、`CapturePanel.tsx`。

- [ ] 定义提取输出的记录、待办、日期、项目和优先级结构。
- [ ] 先保存原文与解析基准，调用模型，校验后事务保存完整批次。
- [ ] 接入处理状态查询、自动保存反馈和生成条目编辑入口。
- [ ] 用固定样本验证混合输入、多个条目、无日期、未知项目和无紧急程度。
- [ ] 用真实 DeepSeek 服务验证至少一个混合输入场景，记录所用版本和结果。

**用户验收：** 连续输入数条真实工作内容，检查拆分和项目匹配，开始每日使用。  
**覆盖：** F01、F04、F09；A01—A03。

### D6：撤销、重试与故障恢复

涉及：`InputService.java`、输入及业务 Mapper、`CapturePanel.tsx`、模型适配的错误映射。

- [ ] 验证整批撤销、重复撤销，以及条目后续编辑后的冲突保护。
- [ ] 通过模型替身模拟超时、空输出、非法日期或结构，检查不产生半批业务数据。
- [ ] 实现失败重试、重复请求去重、并发重试竞争保护。
- [ ] 验证数据库事务回滚、服务重启后失败输入恢复和手动录入入口。
- [ ] 检查 Redis 辅助状态丢失时，数据库约束仍防止重复结果。

**用户验收：** 撤销一次录入，体验一次模拟失败与重试，确认原文和后续编辑都保留。  
**覆盖：** F01、F09；A04、A05、A07、A13。

### D7：手动当天汇总

涉及：`ReportService.java`、报告 Mapper/DTO、`daily.txt`、`ReportPage.tsx`。

- [ ] 先实现按本地日期取数和来源快照，验证跨日与补记。
- [ ] 定义带来源标识的报告输出结构，后端核验标识属于本次来源集合。
- [ ] 实现手动生成、状态查询、超时失败、正文编辑、保存和复制。
- [ ] 检查空记录、仅有计划、部分进展和已失效完成记录的输出。

**用户验收：** 当晚生成汇总，逐条核对来源，修改并保存。  
**覆盖：** F06、F10；A09、A12。

### D8：周报与版本保护

涉及：`ReportService.java`、`weekly.txt`、`ReportPage.tsx`、报告版本与来源快照存储。

- [ ] 实现自然周选择、三段结构和明确下周计划的筛选。
- [ ] 复用来源校验，检查历史周期、项目改名及重复成果的合并表述。
- [ ] 实现新版本生成、旧稿访问、修改来源后的快照保护。
- [ ] 对比一次手工整理和工作台生成加修改的实际耗时，记录结果。

**用户验收：** 用真实记录生成一份周报，编辑后复制；补录一条记录，再生成并核对两个版本。  
**覆盖：** F07、F08、F10；A10—A12、G02—G03 的观察。

### D9：完整流程回归与使用反馈

涉及：核心模块测试、`frontend/tests/workbench.spec.ts`、D9 `research/acceptance-log.md`。

- [ ] 将 A01—A13 整理为可复现验收清单，自动化覆盖状态、来源、日期和持久化。
- [ ] 用浏览器端到端用例走通录入、改错、完成、汇总、周报和复制。
- [ ] 本地确定性回归使用模型替身；对真实模型复核语义质量。
- [ ] 修复用户实际使用中的阻断问题，再处理输入和列表的主要操作成本。
- [ ] 检查核心页面加载、空状态、错误提示、键盘提交和长文本编辑。

**用户验收：** 从打开页面到得到周报完整走一遍，反馈最影响日常使用的操作。  
**覆盖：** A01—A13 回归、G04。

### D10：本机交付与验收收尾

涉及：`README.md`、D1 `research/technical-validation.md`、D9 `research/acceptance-log.md`、D10 `research/architecture.md`。

- [ ] 整理 Windows 启动、停止、配置和 PostgreSQL 数据保留方式。
- [ ] 验证服务重启；提供并演练数据库备份与恢复命令，不新增备份产品页面。
- [ ] 写一页架构说明，解释事务、幂等、来源快照与 AgentScope 的职责。
- [ ] 准备采用脱敏或示例数据的五分钟演示路径。
- [ ] 对照验收矩阵记录通过、失败和需继续观察的项，不把尚未完成的真实试用标为通过。

**用户验收：** 按 README 自行重启、录入和生成报告，确认下一周愿意继续使用。  
**覆盖：** A13—A14、G01—G05；G01 若未积累足够工作日，继续观察并明确状态。

## 6. 测试与验证约定

对事务、幂等、时间边界、状态联动和版本保护，先写能暴露错误的行为用例，再实现最小功能并验证。文案、排版等低影响修改通过实际页面检查，不增加镜像实现的测试。

建议的后端关键测试文件：

- `backend/src/test/java/com/kira/workbench/input/InputProcessingIntegrationTest.java`
- `backend/src/test/java/com/kira/workbench/input/InputRevertIntegrationTest.java`
- `backend/src/test/java/com/kira/workbench/task/TaskCompletionIntegrationTest.java`
- `backend/src/test/java/com/kira/workbench/report/ReportGenerationIntegrationTest.java`

测试数据使用可控时钟，不依赖运行当天碰巧是哪一周。数据库事务与唯一约束在 PostgreSQL 测试环境验证，不用内存数据库替代相关行为。

实施阶段建立脚本后的验证命令如下；这些命令本次未执行：

| 工作目录 | 命令 | 期望结果 |
|---|---|---|
| 项目根目录 | `docker compose config` | 配置解析通过 |
| `backend` | `.\mvnw.cmd dependency:tree` | 检查选定依赖树，无未处理的兼容冲突 |
| `backend` | `.\mvnw.cmd verify` | 业务测试与构建通过；仅配置 verify 不代表自动包含未绑定的集成测试 |
| 项目根目录 | `npm.cmd --prefix frontend ci` | 按锁文件安装成功 |
| 项目根目录 | `npm.cmd --prefix frontend run build` | 类型检查与生产构建通过；脚本需包含对应检查 |
| 项目根目录 | `npm.cmd --prefix frontend run test -- --run` | 前端必要行为测试通过 |
| 项目根目录 | `npm.cmd --prefix frontend run test:e2e` | 核心浏览器流程通过 |

后端关键数据库用例在测试命名及 Maven 生命周期中明确绑定，确保 verify 实际执行；前端测试工具和浏览器测试工具在实施时固定版本并写入锁文件。真实 DeepSeek 调用单独记录，不伪装为离线测试结果。

## 7. 验收对应与完成标准

| 需求 | 主要任务 | 验收依据 |
|---|---|---|
| F01 统一输入与撤销 | D5、D6 | A01、A04、A05、A07 |
| F02 工作记录与日期 | D2、D5 | A03、A09 |
| F03 待办管理 | D3、D4 | A06、A08 |
| F04 项目归类 | D2、D3、D5 | A02、A08 |
| F05 完成记录联动 | D4 | A06 |
| F06 日报 | D7 | A09、A12 |
| F07 周报 | D8 | A10、A12 |
| F08 草稿保护 | D8 | A11 |
| F09 失败恢复 | D5、D6 | A07、A13 |
| F10 来源与事实边界 | D7、D8、D9 | A09—A12 |

一个任务完成需要同时满足：实现已落盘、必要检查已通过、操作路径可演示、已知限制已记录。用户每日反馈纳入下一次交付；本次文档交付不代表这些研发任务完成。

最终验收记录包含：所用版本、检查时间、执行命令、结果、真实体验反馈和仍需观察的指标。检查时间使用可用的精确时钟工具记录，不凭估计填入。

## 8. 排期调整规则

- D1 技术兼容性影响 AI 适配时，先推进 D2—D4 的可靠本地业务功能，保留 Spring Boot 3 约束。
- 临近两周时，优先减少视觉装饰、非必要交互和额外展示材料，不牺牲原文保存、完成联动、失败重试和报告版本保护。
- 固定关键范围内的未完成项明确报告，不将通用聊天、自动化、外部同步等候选功能插入当前排期。
- 第一周结束检查是否已能真实录入并管理待办；第二周结束检查是否已能产出可提交周报。
