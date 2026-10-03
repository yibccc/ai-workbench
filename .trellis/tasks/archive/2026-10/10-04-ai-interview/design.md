# AI 面试正式技术设计

## 状态、依据与边界

规划已按本地真实代码核对；P-01 信息架构仍待最终批准，实施尚未授权。需求与 26 个 FR/26 个 INT-AC 以 prd.md 为权威；研究：frontend-reuse.md、backend-contracts.md、storage-baseline.md。原型源码是直接迁入的 UI 源，PNG 为视觉参考，二者已发现差异，不要求同时复刻矛盾布局。

采用现有单体 Java 17 / Boot 3.5 / MyBatis / Flyway / AgentScope 与 React account root。复用已有对象存储、身份、错误、分页、对话框、Toast、HTTP 与 Markdown，不接入独立 ai-interview 服务，不复制它的 AGPL Java/Prompt/Skill。本机参考仓库 HEAD 已核对，只借助行为事实理解源需求。

## 1. 模块与复用表

| 边界 | 直接复用 | 面试专有工作 |
|---|---|---|
| 身份 | CurrentUser.requireId、现有 Security/CSRF、owner SQL/404 | 新 Controller/Service/Mapper 每条查询绑定 owner；后台从业务记录取 owner |
| 存储 | ObjectStorage.put/open/delete、RustFsObjectStorage、AttachmentValidator.stage、ValidatedAttachment | 当前简历与上传/清理业务，不调用 post 绑定附件接口 |
| 异步 | 现有短事务领取 → 事务外模型 → 短事务提交的模式 | 独立 interview_ai_jobs、token/lease/排队/重启恢复，不把 input/report 实现视为完整现成能力 |
| AI | 现有 AgentScope 配置与结构化解析经验 | InterviewAiGateway、四方向/三难度合同、无 SDK 自动重试、严格完整性校验 |
| 前端 | account root、RetainedView、focus controller、api/http.ts request、Dialog/Toast/Markdown/Pagination/SegmentedControl | features/interview、features/profile、api/interview、api/resume；原型内容转 JSX、作用域 CSS |
| 私有响应 | 现有认证与 ProblemDetail | resume/interview/JD HTTP 响应显式 no-store, private，不误认为社区缓存 filter 已覆盖 |

Controller → Service interface → ServiceImpl → Mapper 的现有分层保持，事务只在实现层。新增类/DTO 放本地对应目录，不把 feature 业务放入 App 巨型组件。

## 2. 数据模型与迁移

当前最新为 V18；实施前重查序号，预定 resume V19、engine V20，冲突时使用实际下一版，不修改任何历史迁移。全部新增表 owner 非空，采用现有 UUID 和时间映射、同 owner 复合 FK。

| 新表（正式规划名） | 核心字段/不变量 |
|---|---|
| user_resumes | user_id PK、version、markdown_text nullable、source_kind、current_object_id nullable、摘要/时间；缺正文表示无当前简历，version 单调增长 |
| resume_objects | id/user_id、immutable storage_key、original_filename、byte_size、sha256、status/token/lease/错误清理状态；不存 supplier URL |
| resume_write_receipts | owner/request_id/operation/payload_hash/result_version/minimal result；无正文；同 key 不同 payload=409 |
| interview_sessions | id/user_id/client_request_id/payload_hash、direction/difficulty/main_count、resume text/version/hash 快照、JD raw/analysis 快照、generation_status/answer_status/evaluation_status、current_turn/version、rubric/model 标识/时间 |
| interview_questions | owner/session、turn_index、MAIN/FOLLOW_UP、parent_main_index、非空 text、类别；unique(owner,session,turn)，一对一关联 |
| interview_answers | owner/session/turn、draft_text、submitted_text/submitted_at、locked、version；已锁定记录不能被 upsert 覆盖 |
| interview_jd_analyses | owner/id/request_id/payload_hash/direction/raw/hash/status/result、version；绑定一次显式解析的来源，不接受客户端任意分析 JSON |
| interview_ai_jobs | owner/id、JD_PARSE/GENERATION/EVALUATION_GROUP、target ID/group、input hash、attempt/token、PENDING/PROCESSING/SUCCEEDED/FAILED、queued_at/queue_deadline、started_at/lease_expires_at、safe error |
| interview_evaluations | owner/session/group、fixed question+answer+rubric hash、逐轮分数/反馈与有效性、token/时间；成功结果可原样复用 |
| interview_operation_receipts | owner/request_id/operation/payload_hash/result ID/turn/version；create/submit/complete/retry/delete 所需最小收据 |

删除面试在短事务内 fencing jobs、删除题单/草稿/答案/JD/评估/简历正文快照及不可读业务主体；保留不含正文的最小 create/delete receipt/tombstone 防 requestId 重放重建。删除后已授权重放返回确定的 404/410 或删除确认，不生成新会话，不暴露正文。引用/receipt 不得意外阻止删除敏感正文。

当前简历删除清空正文/指针且 version+1，保留最小版本空壳避免 ABA；不能删行后新建回 version=0。用户没有可选历史简历版本。创建面试复制文本快照而非维持对原件引用；历史仅依赖快照，因此当前原件替换/删除后的对象可按有效引用清理。

## 3. 当前简历输入、原件与失败事务

1. 浏览器只允许 .md；先把 File 内容导入本地编辑器，保留原 File，用户显式保存才改变生效简历；选择文件/编辑都不即时 PUT。
2. 后端先白名单 .md，再复用 AttachmentValidator.stage(name,input) 的实际 bytes<=1,048,576、SHA、UTF-8/control 校验。ValidatedAttachment 自动关闭/清临时文件；头 BOM 只在正文解码时剥除，存储原字节不改写。
3. 导入的解码正文与最终 markdownText 均按 Unicode 码点 <=20,000；PASTE 同样使用共享 UTF-8/控制字符规则，不制造文件对象。提取现有 markdown 文本检查 helper，避免双套安全规则。
4. 前端 Array.from(text).length，后端 codePointCount；不以 JS string.length 或 maxlength 造成截断。错误保留 File/编辑文本/旧 current。
5. PUT 明确 mode=EDIT_CURRENT|PASTE，或 multipart import，均必须 expectedVersion/requestId；EDIT_CURRENT 绑定 server current，保 source_kind/current_object_id，原件不重传；显式 PASTE 或新 File 替换才解除旧原件引用。hash 包括 mode/source kind、原 file SHA（若有）、最终正文、expectedVersion。服务器私有 key 为 interview/resumes/<userId>/<objectId>.md，不包含用户文件名。
6. 原件不可变；最终文本可能经用户编辑，不要求与原件字节相同，数据库分别存 original SHA 与最终正文摘要，并明确原件下载仍为原始文件。
7. 导入短事务预约 resume_object/token → 事务外 put → 短事务检查 token/version/lease，原子切 current；put/CAS 失败保留旧 current，新对象进入耐久不可读清理状态。业务事务内不调用对象网络。
8. GET 原件经 owner/current 指针/READY 鉴权后 open/流式返回，无预签名/公开 URL/重定向；安全文件名、nosniff、attachment、no-store,private，未经鉴权不返回元数据。
9. 替换/删除将无引用旧原件转 DELETING，清理领取 token；引用存在、活动 put/replace 或未知写入结果时不删除。失败保留最小待清理状态；旧 put 迟到结果不能使 current/READY 复活。清理重试不属于模型重试，不按天删业务资料。

AC-023 的两个上限独立验证：file validator 的 1MiB 等号可通过字节门禁，但合法 UTF-8 每码点最多 4 bytes，超过20k正文仍须在完整 API 拒绝。不能承诺同时满足20k的正文一定能构造1MiB文件；完整导入需同时满足两个条件。这是验证层级澄清，不降低上限、不通过静默截断规避。

## 4. HTTP 合同与幂等

所有接口仅已登录 owner；ADMIN 无私有绕过，写入沿用 CSRF。UUID 资源越权先404，不先泄露状态冲突；验证400、版本/状态409、未配置503、模型失败明确 FAILED/安全错误，不把基础设施失败当未配置。

| 方法/路径 | 输入与语义 |
|---|---|
| GET /api/me/resume | {exists,version,markdownText,sourceKind,originalFile?}；无简历仍给版本，不给云 key/URL |
| PUT /api/me/resume | {markdownText,expectedVersion,requestId,mode:EDIT_CURRENT或PASTE}；EDIT_CURRENT 保 server current 原件，显式 PASTE 替换才解除旧原件引用；CAS 生效 |
| POST /api/me/resume/import | multipart file + markdownText + expectedVersion + requestId；原 File 与编辑后正文双合同 |
| GET /api/me/resume/original | current READY 原件受保护下载；无原件404 |
| DELETE /api/me/resume | expectedVersion/requestId；单调版本，历史快照保留 |
| POST /api/interviews/jd/parse | {direction,jdText,requestId}；持久化解析输入并返回202分析状态 |
| GET /api/interviews/jd/{analysisId} | 纯读 status/result；不续显式活动 |
| POST /api/interviews/jd/{analysisId}/retry | {requestId,expectedVersion}；FAILED 用户显式重试 |
| DELETE /api/interviews/jd/{analysisId} | 移除/修改废弃草稿分析时清正文并 fence，保最小receipt |
| POST /api/interviews | {requestId,direction,difficulty,mainQuestionCount,useCurrentResume,expectedResumeVersion?,jdText?,jdAnalysisId?}；不接收 owner/resumeId/任意snapshot |
| GET /api/interviews/page | 现有0-based page/size合同；OWNER列表、纯读 |
| GET /api/interviews/{id} | 固定配置/题单/当前进度/草稿/状态；纯读 |
| POST /api/interviews/{id}/generation/retry | FAILED、requestId/expectedVersion；新 token，配置不变 |
| PUT /api/interviews/{id}/answer-draft | {turnIndex,answerText,expectedVersion,requestId}；只当前轮，不锁定/推进 |
| POST /api/interviews/{id}/answers/{turnIndex}/submit | {answerText,expectedVersion,requestId}；原子锁定+推进；最后轮自动冻结/首次评估 |
| POST /api/interviews/{id}/complete | {expectedVersion,requestId,early:true}；显式确认提前交卷，冻结并首次评估 |
| POST /api/interviews/{id}/evaluation/retry | FAILED、requestId/expectedVersion；只重试仍必要的失败/未成功组 |
| GET /api/interviews/{id}/report | 纯持久化读；缺必要评分 totalScore=null，分辨 UNANSWERED 与 NOT_EVALUATED |
| DELETE /api/interviews/{id} | 任意阶段本人手动删除；事务擦除敏感数据与fencing |

接口 DTO 均 typed，report 未完成不返回虚假0。创建分析校验 owner+direction+原文hash+SUCCEEDED；JD 任一字符或 direction 改变失效。无效/失败分析不能静默按无 JD；只有用户显式移除 JD 才无 JD 创建。创建只复制原文/分析快照，成功分析可用于同 owner+direction+exact raw hash 的多场创建，不因为创建成功而失效或增加解析调用；正文/方向变更、明确移除或确认取消草稿时 DELETE 原解析并清正文/结果、fence 迟到任务。独立分析是用户当前创建草稿原料，历史各场保存独立快照，互不依赖。不得按天自动清已保留面试。

同 owner+operation+requestId 同 payload 重放固定结果；不同 payload409。删除后的 receipt 保证 create重放不复活。重放检查先于过期 expectedVersion，已成功 submit 的原 requestId 可确认成功而不推进两次。草稿 requestId 与 submit requestId 分开。完整 hash 使用规范 DTO/UTF-8，不能只比较 requestId；不把现有 input/report 不同 payload 未比较的问题一并改造。

## 5. 会话状态与固定题单

- generation: PENDING → PROCESSING → SUCCEEDED|FAILED；FAILED 只显式 retry，SUCCEEDED 后题单不可替换。
- answer: NOT_READY → READY → IN_PROGRESS → COMPLETED；只有生成成功才能 READY；submit 当前轮CAS；最后轮/early complete 原子 COMPLETED。
- evaluation: NOT_STARTED → PENDING → PROCESSING → SUCCEEDED|FAILED；不返回可作答。部分组成功可持久化但不发布完整总分。
- 删除撤销所有执行权并擦除正文；迟到写入必须匹配未删除主体、token、PROCESSING、有效lease，零行即无权提交。

N∈全部整数3..20，默认5。generation一次生成完整N主问+N追问、turnIndex0..2N-1、非空且一对一parent；验证整场后同事务入库，不少题成功/补假题/自动追加模型。追问不依赖未来回答。方向四值JAVA_BACKEND/REACT_FRONTEND/AGENT_DEVELOPMENT/FULL_STACK，difficulty JUNIOR/MID/SENIOR；创建后冻结。

## 6. AI 网关、任务执行权与恢复

### 模型合同

JD只在已选方向提取有证据的重点；3–7条是参考提示词值，不是产品要求，不强制成固定条数。四方向自行按已确认主题实现：React不默认Vue，全栈用跨层场景，Agent仅考察主题。资料/回答以明确data delimiter输入，不运行命令、不调用tools/链接抓取、不让用户文本替代system规则。

AgentScope 2.0.3 的默认 ExecutionConfig 是 maxAttempts=3，照抄 gateway 会隐式retry。面试必须通过 GenerateOptions.executionConfig(ExecutionConfig.builder().timeout(operationTimeout).maxAttempts(1).build()) 显式一attempt，具体import/位置以研究和本地JAR核实。blockLast(timeout)不足以禁重试。假 HTTP/transport计数证明坏结构/超时/5xx/断连一次尝试不产生第二次模型HTTP；不得仅mock自有gateway就声称SDK无retry。

出题严格JSON整体校验；评估每组主问+追问一调用，结果逐轮有稳定turn ID、0..100有效分和非空反馈；多/少/错ID/NaN/越界/坏结构全部该组FAILED，系统失败不是0。两轮都未答的组由后端标未答=0，无模型调用；部分未答只给未答轮0，模型评所有 SUBMITTED 轮次（包括空字符串），不新增未批准的答案最低长度；空草稿可暂存，空提交仍锁定推进且需有效评分/反馈，可真实0，不能自动当成 UNANSWERED 或系统失败。

### 最小耐久job与并发

interview_ai_jobs统一JD_PARSE/GENERATION/EVALUATION_GROUP三操作。专用Interview TaskExecutor建议core/max=2，队列容量40，单次model timeout初值PT4M、处理lease PT5M，排队期限初值PT45M，expiry扫描30s；作为可调整运行参数，不是功能开关，实施时可按真实配置能力细化并记录。

lease从worker真正领取开始，不能在20组enqueue时都开始计时；先持久化PENDING/queue_deadline，worker短事务CAS为PROCESSING分配token+lease，再调用模型。排队拒绝或期限已过记录FAILED并待手动retry。20组成功结果逐组存，避免一个长job跨80分钟lease；不额外调用模型做综合汇总，综合反馈可由持久化组反馈组织。

调度只能由初次用户parse/create/complete，或显式retry授权。一轮初始评估可正常执行其已调度组；失败组不自动再调度。过期lease/排队、中断启动扫描只标FAILED，不在recovery中重新调模型；新retry发新attempt/token，成功组以question+answer+rubric hash复用。

每个终态提交事务同时检查owner、未删除、PROCESSING/token、lease仍有效，插入组/题单与状态变化原子；旧token成功/失败写入零行不得覆盖。启动+周期expiry解决长运行进程卡死，区别于当前只有启动input扫描/无report lease的模式；不扩大改造范围到现有流程。

### 评分与最大输入

每预定轮次等权，总分=sum(逐轮有效分或未答0)/(2N)，后端BigDecimal固定两位HALF_UP并保存评分规则版本；全部必要已答评分有效才生成totalScore。参考答案/优势/改进来自有效组，不据失败编造反馈；读历史不重算/调模型。

用假gateway与假HTTP完成20主问/40轮/最大正文组合、结构错误/容量拒绝/超时/fencing；允许完整成功或明确FAILED，无静默截断。实际供应商context/output limit、延迟/费用/质量未验收，须未来获真实调用授权后测试；不降低产品20题/正文上限或以默认禁用门禁回避。

## 7. 原型迁入与 P-01 待批准 UI

采用原型各screen内容区/卡片顺序/.card/.two/.form-section/.option-grid/.chips/.row等真实布局及业务CSS转JSX；不迁root/body/global reset、顶栏/侧栏/static focus、演示tabs/hash脚本/mock数据。为interview/profile限定class作用域，复用现有页面header+workspace-scroll；不加原型1180px全页cap破坏真实壳层。

P-01批准后 navigation PageId新增interview，App同账号根新增retained工作区；profile作为账号菜单辅助hash视图，不成为第七导航，不重建focus。routes现在以任意'/'判community，使用profile辅助hash并明确isCommunityView判断，避免个人中心误判。移动repeat(5)和E2E五项断言按真实六导航更新，原五项顺序/草稿/筛选保持。

主界面覆盖列表/新建/作答/报告/个人简历/删除；完整3–20、2N动态轮次、JD失效/失败/显式retry、未答0/评估未完成null、多个未完成分开新建/继续、401清旧账号状态与延迟请求取消。dirty导航/提前交卷/删除用现有Dialog；取消不写；失败保留输入。提交丢ACK保同requestId读服务端，不能盲换新ID再次推进。换号按account root key清草稿/轮询/文件Blob，被动轮询不刷新activity。

## 8. RustFS及未来阿里云切换

当前仅ObjectStorage的RustFS实现，StorageProperties强制rustfs/pathStyle=true，compose也固定这些值；未来不是只换endpoint。所有业务只依赖逻辑key/原字节/size/SHA与put/open/delete，不保存supplier URL、桶名/endpoint在业务表、公开地址；owner与引用鉴权在业务层，不放adapter。

本轮沿用私有桶与应用身份最小Put/Get/Delete，增加interview/resumes/*资源，保community/attachments/*现有权限，不授予全桶/管理。initialize-storage.py目前严格拒policy_shape mismatch；实施明确核验目标桶/应用身份及原policy，提供显式canonical policy更新维护动作，不吞异常/自动宽权限或创建兼容fallback。隔离环境先验证新旧前缀/匿名/ADMIN Deny；日常资源变更可逆且只限所需前缀。

未来（延期）增加Aliyun adapter与provider配置，迁同key字节并核对length/SHA/DB引用，所有必要对象就绪后一次切配置；不双读/双写/自动迁移/旧provider回退。OSS配置细节到申请时再核验，参考官方[Use AWS SDKs to access OSS](https://www.alibabacloud.com/help/en/oss/developer-reference/use-aws-sdks-to-access-oss)。当前代码不能因未申请阿里云而禁用本地RustFS功能。

## 9. 运输、备份与恢复

deploy/nginx.conf默认1m不足以承载合法文件加multipart；仅resume import设client_max_body_size 2m，业务file1MiB/正文20k仍精确。现有Servlet21MiB/22MiB已够，不扩大所有API限制。

backup.ps1当前只备份PG；不能声称覆盖原件。resume子任务新增或扩展明确对象备份/恢复命令，先PG与RustFS引用清单协调快照，导出指定prefix原字节、key/size/SHA与manifest，再向新隔离库/桶恢复并比对；对象读取用应用身份，桶/IAM操作用隔离管理身份，不泄漏密钥。业务在线删除不描述为历史备份同步删除。不能指向public、日常app卷或覆盖已有恢复库/桶；物理对象清理与测试资源精确处理。

## 10. 验证/回退与权限

工程AC映射见acceptance-map，commands见implement。批准前不start、不产品修改；批准后每子任务implement→独立check/修复→集成再下一依赖，同一范围无需反复确认。问题写issues，需改变范围/兼容/不可逆的才及时停依赖动作，其余交付末尾确认。

只新增schema，不改历史/旧业务数据；代码回退保新增表与私有原件/最小policy授权，不创建旧版运行回退路径、删除恢复或自动迁移。破坏性删表/清桶另行明确批准。commit/push/PR/部署/真实付费调用按明确授权，不把规划创建权限扩大解释。
