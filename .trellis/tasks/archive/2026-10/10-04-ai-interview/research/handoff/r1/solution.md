# solution — AI Workbench AI 面试整合方案

## 1. 方案状态

- 已选方案：**工作台原生集成**。
- 本文件不是实现补丁，也不是 Trellis 最终执行计划。
- 所有“候选”路径、表名和 API 名称都必须由本地代理结合当前仓库再次核实。
- P-01 导航/个人中心信息架构仍未确认，因此 UI 路由层保持 `draft`。

## 2. 已核实事实

### `ai-workbench@cf6ca832bdfffe13628489a99e80bbd3bbe9940a`
- `backend/pom.xml`：Spring Boot 3.5.16、Java 17、Maven、MyBatis、Flyway、Redis/Spring Session、AgentScope。
- 后端目录规范：Controller → Service 接口 → Service 实现 → MyBatis Mapper；事务位于 Service 实现。
- AI 输入/报告处理已有“持久化请求 → TaskExecutor 事务外模型调用 → 短事务提交结果”模式。
- identity spec：业务资源绑定稳定 `userId`，ADMIN 无私有业务 owner 绕过。
- 已有 `ObjectStorage` 与 `RustFsObjectStorage`，可复用 `put/open/delete`。
- Markdown 附件已有严格 UTF-8/控制字符校验和 1 MiB 文件上限。
- 当前社区附件 API 绑定 postId/稿件版本，不能直接当用户简历接口。
- 前端采用 account root + RetainedView；focus controller 位于账号根。
- 当前主导航只有 5 个工作区；账号菜单无“个人中心”。
- `backup.ps1` 实际只备份 PostgreSQL custom archive；未见 RustFS 对象备份。

### `ai-interview@4341b0597466b2a9ce8a72967552c1a020878324`
- 文字面试有固定题单、答案提交、历史、异步评估参考。
- 主问题数请求允许 3–20。
- 追问在题目生成阶段预生成，不是回答后的动态追问。
- 评估服务会把主问题+追问作为组进行分批评估。
- JD 接口接收 `jdText` 并提取考察分类。
- `frontend` Skill 同时覆盖 React/Vue；`ai-agent-dev` 可作为 Agent 主题参考。
- 未见独立 full-stack Skill。
- 统一评估 Prompt 写死 Java 后端专家角色，不能原样用于四方向。
- 失败/映射缺失会产生 0 分降级结果；本需求明确不采用该评分语义。
- `LICENSE` 为 GNU AGPL-3.0。

## 3. 已选架构

### 3.1 单体原生集成
不保留 `ai-interview` 的 Boot 4 / Java 25 / JPA / Spring AI 运行时。  
在 `ai-workbench` 内按现有 Java 17 + Boot 3.5 + MyBatis + AgentScope 实现。

### 3.2 不新增通用任务中间件
JD 解析、出题、评估优先复用现有 TaskExecutor + 持久化执行权/状态思路，不引入 Kafka、额外 Agent、中间队列或独立 worker。具体 executor/并发/timeout 需本地核对。

### 3.3 面试专用 AI gateway
**候选实现**：在已存在的 `backend/src/main/java/com/aiworkbench/ai/` 下增加面试专用 gateway，而不是迁入源 Spring AI Service。

三类模型操作：
1. JD 解析；
2. 题单生成；
3. 分组评估 + 汇总。

共同要求：输入来自持久化快照；用户文本视为不可信；结构化输出必须校验；不静默截断；模型调用不持 DB 事务；失败不自动追加模型调用。

## 4. 数据合同（候选）

以下名称均为候选，不是已存在事实。

### 4.1 `user_resume`
建议：`user_id` 唯一、`version`、`markdown_text`、`source_kind=PASTE|MD_FILE`、可选 `object_key/object_size/object_sha256/original_filename`、时间戳。  
不建立用户可选简历版本库。

### 4.2 `interview_session`
建议保存：`id/user_id/client_request_id`、方向、难度、主问题数、resume snapshot、JD 原文/分析、generation/evaluation status + token/attempt、current turn、答卷状态、模型/评分规则标识、时间戳/版本。

删除后若需防重放，可保留最小 tombstone/receipt；不得保留可正常读取的已删除正文。

### 4.3 `interview_question`
建议：session、turn index、`MAIN|FOLLOW_UP`、parent main index、question text、category/topic metadata。进入可作答前严格校验 N main + N follow-up + 2N turns + 一一关联。

### 4.4 `interview_answer`
建议同时表达当前草稿、已提交文本、locked/submitted、时间、提交 requestId/expected progress。已锁定轮次不可覆盖。

### 4.5 评估结果
可选“组结果 + 会话汇总”或“逐轮规范化结果 + 汇总 JSON”。需求只要求：有效组结果可复用；失败/未评估与真实 0 分可区分；历史读取不重跑模型。

## 5. 接口合同（候选命名）

### 当前简历
- `GET /api/me/resume`
- `PUT /api/me/resume` — body 候选：`{markdownText, expectedVersion}`
- `POST /api/me/resume/import` — multipart `.md`, `expectedVersion`, `requestId`
- `DELETE /api/me/resume`

### JD
- `POST /api/interviews/jd/parse` — `{direction, jdText, requestId}`

### 面试
- `POST /api/interviews`
- `GET /api/interviews/page`
- `GET /api/interviews/{id}`
- `POST /api/interviews/{id}/generation/retry`
- `PUT /api/interviews/{id}/answer-draft`
- `POST /api/interviews/{id}/answers/{turnIndex}/submit`
- `POST /api/interviews/{id}/complete`
- `POST /api/interviews/{id}/evaluation/retry`
- `GET /api/interviews/{id}/report`
- `DELETE /api/interviews/{id}`

关键约束：客户端不能上传 ownerId 或任意 resume snapshot；create 使用服务端 current resume + expected resume version；submit 只能提交 server current turn；report GET 纯读取；retry 显式；delete 后旧 token 无权写回。

## 6. 状态合同

### 生成
`PENDING -> PROCESSING -> SUCCEEDED | FAILED`  
FAILED 只手动 retry；retry 新 token；SUCCEEDED 后题单固定。

### 作答
`READY -> IN_PROGRESS -> COMPLETED`  
submit 原子完成保存+锁定+推进；提前交卷/最后一轮完成进入 COMPLETED；不可回到可编辑。

### 评估
`NOT_STARTED -> PENDING -> PROCESSING -> SUCCEEDED | FAILED`  
交卷自动首次评估；FAILED 只手动 retry；失败不解锁答卷；SUCCEEDED 历史 GET 纯读取。

### 删除
任一阶段可删除。正常业务立即不可读；已发出的模型调用可自然结束，但提交结果前必须重新校验 token/tombstone；不承诺远端请求一定未产生费用。

## 7. AI 规则

### 四方向
- Java 后端：Java、Spring、数据库、缓存/消息、服务工程、项目取舍。
- React 前端：JS/TS、React、状态、浏览器/网络、性能、工程化；不默认考 Vue。
- Agent 开发：LLM 调用、tool、context、RAG/评估、错误恢复、成本/延迟；仅作为考察主题。
- 全栈：用业务场景串 UI、API、鉴权、数据、部署、故障，不机械 50/50 抽题。

### 固定追问
追问不能假装知道未来回答。可问真实场景、边界、取舍；避免“你刚才提到……”之类依赖尚未存在回答的措辞。

### 评估
主问题+追问作为上下文组，每轮独立评分。未答=0；系统失败=未评估。全部必要结果有效后才发布总分。

## 8. 幂等与并发

- 创建：`(user_id, client_request_id)` 唯一；同 key 同 payload 重放返回原会话，不同 payload 冲突。
- 简历：`expectedVersion` 防静默并发覆盖；创建面试校验用户确认的 resume version。
- 答案：提交检查 server current turn + requestId/version；成功后锁定；旧请求不得再次推进。
- AI 任务：每次有 token/attempt；更新必须匹配 active token；retry 替换 token，delete 使 token 无权提交。

## 9. RustFS

已核实可复用：`ObjectStorage`、`RustFsObjectStorage`、MD UTF-8 校验与 1 MiB 限制。  
不能直接复用社区 `/api/me/posts/{postId}/attachments` 业务接口。

**候选**新前缀可类似 `interview/resumes/<userId>/...`，但具体 key/policy 尚未核验。只授予必要 Put/Get/Delete，不授予 admin，不创建浏览器公开 URL，不改变社区前缀行为。

## 10. 前端接入

### 已核实
- `App.tsx` 负责 account root 与 retained workspace；
- `navigation.ts` 当前只有 5 个主工作区；
- `AccountMenu.tsx` 无个人中心；
- spec 要求 feature 逻辑进 `features/`、API 进 `api/`；
- focus controller 保持 account root 生命周期。

### 候选路径
- `frontend/src/features/interview/`
- `frontend/src/features/profile/`
- `frontend/src/api/interview.ts`
- `frontend/src/api/resume.ts`

P-01 未批准前，不固定 navigation/routes/个人中心入口。

## 11. 兼容与回滚

- 不新增未经批准的兼容层、旧路由兼容或 feature flag。
- 建议只新增表/索引/FK 的 Flyway migration，不修改历史 migration。
- 不自动改写现有记录、待办、专注、报告、社区数据。
- 回退代码时先保留新增表和 RustFS 私有对象；旧版本应忽略新增表。任何破坏性清理另行批准并先备份。

## 12. 许可风险

- `ai-interview@4341b0597466b2a9ce8a72967552c1a020878324` 为 GNU AGPL-3.0。
- `ai-workbench@cf6ca832bdfffe13628489a99e80bbd3bbe9940a` 根目录未找到 `LICENSE`。
- 在许可审查完成前，不建议直接复制 `ai-interview` 的 Java 实现、Prompt、Skill 或 reference 文本。
- 可先参考行为/接口事实，由目标栈独立实现。
- 若复制/派生，先明确许可适用与网络服务源代码提供义务。本包不构成法律意见。

## 13. 本地待核实

1. 本地当前 branch/commit。
2. P-01 UI 信息架构。
3. AGPL/目标仓库许可。
4. Flyway 版本号与表命名。
5. executor/timeout/lease/recovery。
6. 20 主问题 + 最大输入实际模型容量/延迟/成本。
7. AgentScope 结构化输出可靠性和当前模型配置。
8. RustFS 新前缀 policy。
9. PostgreSQL + RustFS 联合备份/恢复。
10. 四方向 Prompt/参考资源来源和验收样例。
11. JD focus 项数量/展示文案。

## 14. 本包没有做的事

未实现 API/表/迁移；未调用真实模型；未验证 40 轮；未改 RustFS policy；未运行测试；未创建 commit/PR/Trellis task。
