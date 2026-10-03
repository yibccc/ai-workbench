# 后端真实代码核验与最低合同

核验日期：2026-10-04。范围为交接包 R1、当前 `backend/` 与对应 Trellis spec；只读核验，无实现、迁移执行、依赖安装、真实模型调用或产品测试。本文的新表、类型与接口是正式规划建议，不是已存在实现。

## 最终定稿覆盖研究建议

本研究保留原始代码事实和候选建议；正式父 design 是执行权威。经独立审阅，已定稿：turnIndex 为 0 起点；job 排队状态为 PENDING；统一 operation receipt；PUT 简历必须 mode=EDIT_CURRENT|PASTE，普通编辑保当前原件，显式粘贴替换才解除；成功 JD 分析同 owner/direction/exact hash 可用于多场，不因创建成功消费/清正文，仅明确改文/移除/取消才 DELETE；空 MD/草稿/提交不新增最低长度，空提交仍 SUBMITTED 并需有效评分反馈，未提交交卷才 UNANSWERED。下文 1 起点、QUEUED、consume/scrub、业务非空或空答400的候选建议已被覆盖，不得据此反改正式合同。

## 1. 已核实基础与复用边界

| 实际符号 / 路径与行号 | 本需求的处理 |
|---|---|
| `backend/pom.xml:10` / `:21` / `:22` / `:23` / `:25` | Boot 3.5.16 / Java 17 / AgentScope 2.0.3 / MyBatis 3.0.5 / AWS SDK 2.55.10。保留目标栈，不迁入 Boot 4、JPA 或 Spring AI。 |
| `backend/src/main/java/com/aiworkbench/security/CurrentUser.java:21` | 直接复用稳定 `userId`；DTO 不接受 ownerId。 |
| `backend/src/main/java/com/aiworkbench/security/ApiSecurity.java:58` / `:71` / `:74` | 直接复用 Session + CSRF：所有新业务请求 authenticated，写请求 X-XSRF-TOKEN；401/403 为 ProblemDetail。ADMIN 无业务 owner 绕过。 |
| `backend/src/main/java/com/aiworkbench/security/ApiSessionFilter.java:45`、`security/SessionActivity.java:48` / `:53` | 直接复用独立显式活动期限；GET、后台轮询、推送不能调用 touch。 |
| `backend/src/main/java/com/aiworkbench/exception/ApiExceptionHandler.java:18` | 复用 ProblemDetail advice；为面试新增带安全 code/currentVersion 的业务异常，不复用社区 AttachmentException 的领域错误码。 |
| `backend/src/main/java/com/aiworkbench/common/PageQueries.java:13`、`common/PageResponse.java:13` | 直接复用零起点分页；size 仅 5/10/20/50，查询稳定 ORDER BY created_at DESC,id DESC。 |
| `backend/src/main/java/com/aiworkbench/events/WorkbenchEventHub.java:33` | 可直接复用 after-commit owner 私有刷新信号。通知只提示重新 GET，不推简历/答案正文。 |
| `backend/src/main/java/com/aiworkbench/events/WorkbenchWebSocketConfig.java:46` | 当前命名 applicationTaskExecutor 通过 Boot builder 创建。可复用执行器构建能力；面试建议单独命名执行器 core/max=2，以免改变 input/report 的排队行为。当前未见显式 pool/queue 配置，不能声称已有业务并发为 2。 |
| `backend/src/main/java/com/aiworkbench/config/mybatis/UuidTypeHandler.java:15`、`resources/application.yml:86` | 直接复用 UUID handler 与 mapper XML 扫描；新行类型在 entity/interview、entity/resume，DTO 在 dto 对应目录。 |
| `backend/src/main/java/com/aiworkbench/storage/ObjectStorage.java:8` | 直接复用 put/open/delete；provider identity、owner 授权仍在业务层。新原件不得挂 community post。 |
| `backend/src/main/java/com/aiworkbench/storage/AttachmentValidator.java:17` / `:27`、`storage/AttachmentValidationWorker.java:32` | 可直接复用 stage 的 1 MiB MD 严格 UTF-8/控制字符校验和 SHA256；ResumeService 先限制扩展名为 md，另校验正文 20,000 码点及业务非空。现有 stage 同时接受 image/pdf，不可直接当简历类型白名单。 |
| `backend/src/main/java/com/aiworkbench/config/CommunityCacheControlFilter.java:21` | 现有 no-store, private 仅覆盖社区。新简历/面试/JD JSON 与原件响应须新增明确缓存匹配。 |

仅可参考模式，不能直接宣称复用完成：

- `service/impl/InputServiceImpl.java:69`：提交持久化 claim 后，用 TaskExecutor 在事务外调用模型；executor rejection 写 FAILED；worker 使用持久化 owner。
- `service/impl/InputPersistenceServiceImpl.java:29`：校验 lease > 模型超时；`:69` 短事务提交结果；`:95` 过期转 FAILED。
- `resources/mapper/InputMapper.xml:13`：owner/requestId 唯一插入；`:26` FAILED 手动 retry；`:32` token fencing。当前 markSucceeded **没有** lease_expires_at > now 条件。
- `config/InputRecoveryRunner.java:25`：只在启动执行一次过期扫描；当前未见定时 recovery，未过期的中断任务不能靠这一次扫描保证最终恢复。
- `service/impl/ReportPersistenceServiceImpl.java:70`、`resources/mapper/ReportMapper.xml:156`：report token+owner+未删除 fencing，可参考；report 无独立 lease/recovery。
- Input create 去重并未比对同 requestId 的不同 payload；面试不能沿用这一弱化语义。
- `ai/AgentScopeReportAiGateway.java:43` / `:68` / `:98` / `:169`：严格 JSON、输出截断检测、alias 映射、模型输入 JSON 数据封装是可参考模式；日报 DTO/prompt 不是面试结构，且 SDK 默认 retry 不能照抄。
- `service/impl/AttachmentPersistenceService.java:37` / `:123` / `:135`：原件 reserve/finalize/recover/cleanup 思路可参考，现有实现锁 post、使用社区配额及前缀，不能作为 resume service 直接调用。

本次 `rg` 未找到 interview/resume API、领域表或 gateway。最新 migration 为 `V18__community_attachments.sql`；实施前再次检查，优先追加 `V19__ai_interview.sql`，历史 V1–V18 不改。

## 2. AgentScope 隐式自动重试：必须消除的工程风险

已读取本机缓存 JAR 字节码，未联网或下载依赖：

```powershell
$coreJar = 'C:/Users/kira/.m2/repository/io/agentscope/agentscope-core/2.0.3/agentscope-core-2.0.3.jar'
$openAiJar = 'C:/Users/kira/.m2/repository/io/agentscope/agentscope-extensions-model-openai/2.0.3/agentscope-extensions-model-openai-2.0.3.jar'
javap -classpath $coreJar -c -private io.agentscope.core.model.ExecutionConfig
javap -classpath $coreJar -c -private io.agentscope.core.model.ModelUtils
javap -classpath $openAiJar -c -private 'io.agentscope.extensions.model.openai.OpenAIChatModel$Builder'
javap -classpath $openAiJar -c -private io.agentscope.extensions.model.openai.OpenAIChatModel
```

证据链：

1. `OpenAIChatModel.Builder.build` bytecode offset 76 调用 `ModelUtils.ensureDefaultExecutionConfig`。
2. `ExecutionConfig` static initializer 在 offset 20 使用 iconst_3，offset 24 设置 maxAttempts，offset 63 保存 MODEL_DEFAULTS；其 timeout 为 5 分钟。
3. `OpenAIChatModel.doStream` 调用 `ModelUtils.applyTimeoutAndRetry`；`ModelUtils` 对 maxAttempts > 1 应用 `Flux.retryWhen`，重试次数为 attempts-1。
4. 现有 `AgentScopeReportAiGateway.java:72` 只设 temperature/maxTokens，`:75` 用 blockLast 外层 deadline；现有 capture gateway 同样未显式关闭 SDK retry。**外层 blockLast 不等于关闭自动重试。**

新 `AgentScopeInterviewAiGateway` 的 JD/出题/分组评估全部明确设置：

```java
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;

GenerateOptions options = GenerateOptions.builder()
    .temperature(0.2)
    .maxTokens(operationResponseLimit)
    .executionConfig(ExecutionConfig.builder()
        .timeout(operationTimeout)
        .maxAttempts(1)
        .build())
    .build();
```

`OpenAIChatModel.builder().generateOptions(options)`；保留一致的外层 deadline。失败直接持久化安全错误码，不用 retryWhen、不修复 JSON 后自动重调模型、不回退到其他模型。新 gateway 不变更现有 capture/report 行为。

执行门禁必须调用**真实 AgentScope 2.0.3 SDK + 本地 fake HTTP endpoint 或计数 HttpTransport**：注入 429/5xx/连接断开/超时，并证明一个 attempt 最多发送一次请求。仅 mock InterviewAiGateway 不能证明此合同。另测试 input_data 含角色声明、HTML/script/命令/URL 时无工具调用/抓取；输入作为 JSON 数据，与四方向/难度约束分开。

## 3. 建议领域表与不可变字段

表名统一 lower_snake_case；ID UUID；user_id FK user_accounts ON DELETE RESTRICT；时间 TIMESTAMPTZ。所有资源表 `(id,user_id)` 唯一，子表使用同 owner 的 composite FK，读写 SQL 同时绑定 owner。以下可写入同一个追加 migration，不建立通用中间件。

| 建议表 | 最低数据与约束 |
|---|---|
| `user_resumes` | user_id PK；version 单调递增；nullable markdown_text/current_object_id；source_kind、content_sha256、updated_at。每用户只有一个 singleton 槽位，无正文表示未设置。删除只清正文/对象指针并 version+1，保留无私有正文槽位；后续保存不重置为 0，避免 ABA。原件操作/清理 ledger 由存储规划单独定义。 |
| `interview_jd_analyses` | id/user_id/request_id/request_fingerprint；direction、原文、精确 UTF-8 SHA256、analysis JSON；status/version、安全错误、时间。结果来源固定服务端 analysis，不接受客户端解析结果。独立解析是创建表单草稿，需显式取消/替换/消费后的正文清理。 |
| `interview_create_receipts` | user_id+request_id 唯一；interview_id、canonical request fingerprint、created_at/deleted_at。没有简历/JD/答案正文。用于响应丢失重放与删除后拒绝复活；不得以删除会话时顺便删除 receipt 的方式允许同 key 重新创建。 |
| `interview_sessions` | id/user_id；固定 direction/difficulty/main_count；resume 文本/version/hash 快照，JD 原文/hash/analysis 快照；generation_status、answer_status、evaluation_status、current_turn、version；model/prompt/rubric/scoring_rule 标识；created/completed 时间。客户端不传 owner、替代简历正文或旧 resumeId。 |
| `interview_questions` | session_id/user_id/turn_index UNIQUE；group_index、kind MAIN/FOLLOW_UP、parent_main_turn、正文与主题。推荐 1 起点轮次：main=2g-1，follow=2g，parent=2g-1；CHECK 数学关联，复合 FK parent 同场同 owner；进入 READY 后不更新题单。 |
| `interview_answers` | 同场/owner/turn FK 与唯一；state DRAFT/SUBMITTED/UNANSWERED；draft_text、submitted_text、version、submit_request_id、submit_fingerprint、submitted_at。SUBMITTED 和 UNANSWERED 冻结；submit_request_id 的唯一范围为 session；草稿不推进。 |
| `interview_evaluation_groups` | session/owner/group 唯一；input_hash、rubric_version、status、两轮规范化结果/反馈、safe_error；两个逐轮结果必须对应固定题单。有效结果不可被 retry 随意覆盖；系统失败结果 score=null，不能写用户 0。 |
| `interview_ai_jobs` | operation JD_PARSE/GENERATION/EVALUATION_GROUP；同 owner 资源引用；status QUEUED/PROCESSING/SUCCEEDED/FAILED，attempt/token；queue deadline、lease_expires_at、input_hash、安全错误、时间。保存元数据而非第二份正文；统一 lease/fencing/recovery，避免三套不一致实现。 |

create_receipts 可与 scrub 后的 session tombstone 合并，但分表更易保证删除正文、FK cascade 和最小 receipt；不需要另加通用任务框架。原件 ledger 不指向历史 session：历史仅复制简历文本与摘要，不读取原对象，因而 current replacement/deletion 不破坏历史。

数据库 CHAR_LENGTH 与 Java codePointCount、前端 Array.from(text).length 对 Unicode 码点保持一致。简历正文 20,000、JD 10,000、答案 5,000；非空判定可用 isBlank，但原文及 digest 不 trim、不规范化换行、不截断。严格拒绝无效编码/NUL；业务限制以原文为准。文件上限与正文上限分别验证。

## 4. JD 显式解析与创建来源绑定

建议 API：

- `POST /api/interviews/jd/parse {direction,jdText,requestId}` -> 202 `{analysisId,status,version,rawHash}`；仅此次明确动作授权首次模型调用。
- `GET /api/interviews/jd/{analysisId}` -> 持久化 status/result，纯读取。
- `POST /api/interviews/jd/{analysisId}/retry {expectedVersion,requestId}` -> 202，仅 FAILED；同原文/方向，更新 job token；响应丢失重放不再调用。
- `DELETE /api/interviews/jd/{analysisId}` -> 204；owner 后清原文/结果，失效所有关联 token；可保留 id/owner/request fingerprint 的最小取消 receipt。
- 面试 create 提交 `jdAnalysisId + jdText` 或明确无 JD；服务端重新计算**当前表单精确文本** hash，要求 analysis 同 owner、SUCCEEDED、direction 相等、rawHash 相等。只传 analysisId 而不绑定当前表单原文不足以验证修改失效。

用户编辑 JD 任一字符或修改 direction，前端立即使旧 analysis 不可用于创建；服务端仍独立核对 hash/direction，不依赖 UI。分析失败保留原文与错误，不静默忽略、不切方向；只有明确移除 JD 才走无 JD 创建。

最小清理策略：创建事务先锁 analysis、复制原文与分析到 session，再消费该独立创建草稿并 scrub 正文/结果；原 session 的快照永久独立。响应丢失先查 create receipt，再核对 consumed JD，因此成功重放仍返回原 session。取消/移除 JD 明确 DELETE；新解析替代旧解析时前端发旧 analysis 的取消命令；没有用户确认的保留日数或自动模型 retry。若希望一次分析用于多次新建，需保留其显式草稿生命周期并单独 release，不能在创建后隐式重调模型。两种策略选一写入 design，不留下含糊的独立 JD 原文残留。

## 5. 创建、题单、作答与整场评分

### 创建与固定题单

`POST /api/interviews {requestId,direction,difficulty,mainQuestionCount,resumeMode,expectedResumeVersion?,jdAnalysisId?,jdText?}` -> 201 会话。

1. 先 owner/requestId receipt：相同 canonical payload 返回原成功/失败会话；不同 payload 409。同 key 已删除不得生成新会话，返回安全 409 INTERVIEW_DELETED。新的 requestId 总是创建新场，不能以 resume/配置查回旧场。
2. resumeMode=NONE 不带替代正文；CURRENT 锁 server singleton 并校验用户确认的 expectedResumeVersion，复制内容/version/hash。失配 409；非 owner 资源先 404。
3. 原子保存配置与资料快照、创建 GENERATION job；事务结束后调度。模型调用不持有 DB 事务。
4. 新 `InterviewAiGateway` 暴露 parseJd、generateQuestionSet、evaluateGroup。对四方向和三难度明确 enum；React 只以 React 为默认，Agent 仅主题，全栈用跨层场景；不可使用写死 Java 的外部评分 prompt。
5. 题单输出恰好 N 个 group，每组一 MAIN 与一 FOLLOW_UP，非空、唯一映射、总 2N；少/多/空/重复/错误 parent 全部 FAILED，不存残缺 READY。事务锁 session+job，重新校验 execution right，然后一次写全 questions/answers 并置 READY。
6. `POST /api/interviews/{id}/generation/retry {expectedVersion,requestId}` 只允许 FAILED，沿用同快照；SUCCEEDED 后不换题。

### 草稿、提交、交卷

- `PUT /api/interviews/{id}/answer-draft {turnIndex,text,expectedVersion}`；锁 owner session/current answer，要求当前 turn 且未提交、未交卷；仅保存草稿/version，不推进。
- `POST /api/interviews/{id}/answers/{turnIndex}/submit {requestId,text,expectedVersion}`；先找该场 submit receipt。相同 turn/text fingerprint 重放返回当前持久化进度，允许旧 expectedVersion；不同 payload 409。无 receipt 时锁 session，再校验 server current turn/version/长度，原子写 SUBMITTED+清草稿+推进一次。
- 旧轮、旧标签页、迟到草稿不能覆盖已锁定文本。SQL 条件含 owner/session/turn/state/version；不能先查询后无条件 update。
- 最后轮 submit 在同事务冻结答卷，并只创建一次首次评估 group jobs。`POST /api/interviews/{id}/complete {requestId,expectedVersion}` 提前交卷先锁 session，所有未提交轮次设 UNANSWERED，草稿不偷偷变为提交答案；清未答草稿并冻结。重复 complete 不新增评估调用。
- 提前确认 UI 显示已答/未答数量；服务端计算本场 2N，并明确 UNANSWERED=0。

### 分组评估与平均

- N 个 group 的模型任务各自独立 lease/token；每次调用只包含该主问+追问、其两轮固定答案、资料/方向/难度快照、rubric。已提交但真实评分为 0 与系统 FAILED 明确区分。
- 全组未答可以本地写两条 UNANSWERED/0，无模型请求；部分未答只要求模型返回有效作答 alias 的结果，未答轮服务端填 0。有效答案必须每轮恰好一个可映射评分 0–100 与非空反馈；缺题/错号/重复/非有限或非法分数整个组 FAILED。
- 逐组短事务提交 SUCCEEDED 结果；成功组 input_hash 固定，包括题单、答卷与 rubric/model 配置。`POST .../evaluation/retry {expectedVersion,requestId}` 只为未成功组创建新 attempt；不重调已有成功组，不解锁答卷。
- 后端从持久化全部 2N 轮计算 `scoreSum / totalTurns`，保存评分规则 equal_turn_mean_v1；用 BigDecimal，展示可 round(2, HALF_UP)，原始 scoreSum/totalTurns 保持可追溯。总分仅在每个必要组有效时发布；否则 totalScore=null，错误组 score=null，答卷保持 COMPLETED。
- 综合反馈/优势/改进从已持久化组反馈组织展示，不增加额外综合汇总模型调用。
- `GET /api/interviews/page`、`GET /{id}`、`GET /{id}/report` 均纯读；FAILED 可返回状态与可用分组结果，但不能伪装完整成功总分。查询、刷新、恢复已成功组都不新调模型。

## 6. job、lease、队列、恢复与删除 fencing

默认建议：面试专用 executor 并发 2；单模型操作 deadline 4 分钟；**PROCESSING** lease 5 分钟；定时过期扫描例如 30 秒，启动同样扫描。所有正值与 lease > deadline 在配置绑定时 fail-fast。出题/评估的输出 token 限制独立，不能沿用 report 4096 并宣称 40 轮一定可用。

排队与执行必须分开：

1. 显式 create/parse/complete/retry 短事务保存 QUEUED job。只有新 claim 的调用者调度；replay/GET 不调度。
2. worker **真正开始执行**时从 QUEUED 原子 claim PROCESSING，才设置 active token 和 `lease_expires_at=now+5m`。不能入队 20 组就同时开始 5 分钟 PROCESSING lease。
3. QUEUED 使用独立有界 queue deadline；每组最多一次当前 attempt，executor 队列容量有限。队列拒绝或等待超时记录 QUEUE_REJECTED/QUEUE_TIMEOUT 并转 FAILED，不永久排队。最大 20 组的计划不能要求所有 group 等在共享 executor 里直到 lease 自动续命。
4. 可采用内存一次最多 dispatch 2 个组、成功后 dispatch 下一组，减少长期排队；同时必须持久化未 dispatch 组及对中断的失败归属。不得把持续调度下一个从未尝试组与失败组自动 retry 混为一谈。
5. 宕机恢复、queued deadline 或 lease expiry 只将未确认成功任务标记 FAILED/INTERRUPTED 并使旧 token 失效，不自动生成新模型调用。startup 尚未过期的 PROCESSING 由后续定时扫描保证最终失败；保持现有单后端部署边界，不引入分布式 leader。

成功/失败/取消/删除统一同锁顺序：先 owner resource，再 job/group。最终提交需同时检查 `user_id/resource_id/job_id/status=PROCESSING/token/input_hash/deleted=false/lease_expires_at>dbNow`。任何不满足都不写结果；子表插入与 session READY/评估组 SUCCEEDED 属同一事务。删除/retry 不能与陈旧完成互相覆盖。

`DELETE /api/interviews/{id}?version=`：所有阶段都允许 owner 删除。短事务锁 session，失效 jobs，删除 question/answer/group results/JD与resume快照，receipt 标记 deleted；当前 resume 与其他场不动。普通 GET 列表不可读，旧模型完成只能发现无资源/失效token并放弃，禁止 UPSERT 重新建回；same create requestId 重放不得复活。再删相同 owner receipt 可 204；foreign/unknown 为同样安全 404。不以报告现有“只可删终态 DAILY”作为面试删除限制。

原件读取/清理另遵守存储规划：owner 后打开、no-store/private/nosniff、不生成公开/预签名 URL；删除失败保留最小清理 key/token/error 以显式恢复，不宣称已物理删除。恢复 PostgreSQL 与 RustFS 的一致性分别验证；在线删除不意味着历史备份删除。

## 7. HTTP 错误与验证计划

| 条件 | 建议结果 |
|---|---|
| 未登录 / 失效session / CSRF无效 | 401 / 401 / 403，沿用现有 Security |
| foreign UUID（包括 ADMIN）、不存在资源 | 404，先 owner 再状态/版本，不泄露状态 |
| 非法方向/难度/N/空答/码点+1/无效UTF8 | 400 ProblemDetail，文件 bytes+1 为 413 |
| 版本冲突、JD失效/方向不符、已锁轮次、同key不同payload | 409，安全 code/currentVersion；不覆盖、不推进 |
| 模型未配置、超时、截断、结构错误、queue拒绝 | 持久化 FAILED 与安全错误；不伪分、不静默降级；只显式retry |
| 全部有效评分 | SUCCEEDED，稳定固定总分；GET调用计数不增长 |

沿用实际测试基础：

- `input/InputRecoveryIntegrationTest.java:78` / `:97` / `:122`：独立事务双 claim、过期恢复、旧 token 迟到。
- `input/InputExecutorFailureTest.java:29`：executor 拒绝后 durable FAILED。
- `owner/BusinessOwnerIsolationIntegrationTest.java:108` / `:157` / `:310` / `:516`：A/B/ADMIN、真实HTTP、安全404、async持久化owner。
- `ai/AgentScopeReportAiGatewayTest.java:35` / `:40` / `:49` / `:56` / `:75`：严格 JSON、围栏完整性、alias、最大输入不截断；新增 interview SDK transport计数，不能仅 gateway mock。
- `community/AttachmentConcurrencyIntegrationTest.java:70` / `:93` / `:114`：不同payload幂等、迟到put、durable cleanup failure；简历用独立业务 fixture。
- `security/AuthenticationIntegrationTest.java:69` / `:403`：CSRF/认证及自动请求不延长 idle。

实施后新增：InterviewAiGatewayContractTest、InterviewHttpIntegrationTest、InterviewConcurrencyIntegrationTest、InterviewRecoveryIntegrationTest、InterviewMigrationIntegrationTest、ResumeIntegrationTest；名称可调整，AC 不减少。重点固定2N=6/10/40、错误题单、答卷响应丢失、不同payload replay、两个标签页、每组模型调用计数、无额外汇总调用、部分组失败、所有阶段删除、expiry之前/之后迟到、简历删除ABA、JD原文一字符变更、非法方向及最大码点/bytes组合。

实际工具入口：mvn 为 `D:/APPS/apache-maven-3.9.9/bin/mvn.cmd`。README.md:184 要求新隔离 schema；`config/IsolatedProfileSchemaGuard.java:33` 允许的自定义test schema是 `d9_[a-z0-9_]+_tests_[0-9]{8}`。需预先证明专用 schema 不存在，并使用独立 PostgreSQL/Redis 实例或项目认可隔离环境，不读/打印用户 .env 密钥。

```powershell
# 在已验证的独立 PG/Redis 与 Java 17 环境中；示例schema需确认全新
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:TEST_PORT/TEST_DB?currentSchema=d9_interview_tests_20261004'
$env:WORKBENCH_TEST_SCHEMA = 'd9_interview_tests_20261004'
$env:DEEPSEEK_API_KEY = ''
# 凭据/Redis端口由合成测试环境提供，不复制用户日常配置
mvn -s maven-settings-aliyun.xml '-Dtest=InterviewAiGatewayContractTest' test
mvn -s maven-settings-aliyun.xml '-Dtest=Interview*IntegrationTest,Resume*IntegrationTest' test
mvn -s maven-settings-aliyun.xml clean verify
```

工作目录为 `E:/projects/workbench/backend`；Surefire 已在 pom.xml:131 设置 test profile。TestAiGatewaysConfiguration 当前只有 capture/report/probe 替身，新面试需加入 no-network test gateway；e2e 添加 deterministic Interview gateway 并扩展 E2eMaintenanceService 的新领域重置，隔离 guard 不放宽。迁移验证需 fresh V1→V19 与真实 V18→V19，断言旧迁移 checksum 和既有数据不变。

最大组合先用 fake gateway/offline fixtures 验证 20主问/40轮/最大简历JD答案，无截断/残缺/伪分。真实模型质量、容量、延迟与成本尚未验证，不以offline通过替代；真实付费调用另需授权。此次只读核验未执行任何上述产品验证命令。
