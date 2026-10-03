# AI 面试引擎技术设计

## 权威与真实复用

父 `../10-04-ai-interview/design.md` 第2/4/5/6节为完整数据/API/状态/执行合同；本设计落实引擎边界，不把research的旧候选覆盖父定稿。真实代码路径/符号/SDKJAR与javap证据见父 `research/backend-contracts.md`，该文顶部已标记定稿纠偏。

复用CurrentUser/Security/CSRF、ProblemDetail、PageQueries/PageResponse、UUID mapper、AgentScope配置与短事务模式。新InterviewController/Service接口/ServiceImpl/PersistenceService/Mapper/XML、DTO与entities按现有分层；事务放独立Spring持久bean，模型不持事务。Input/Report不是完整lease/recovery库，不改其旧行为，不接SpringAI/JPA/Boot4源栈。

## 1. Schema和身份边界

当前V18，依赖resume下一版后追加实际下一版（预定engine V20）；不改历史迁移。使用父定稿：
`interview_sessions`、`interview_questions`、`interview_answers`、`interview_jd_analyses`、`interview_ai_jobs`、`interview_evaluations`、`interview_operation_receipts`。owner非空、UUID、TIMESTAMPTZ、same-owner composite FK、owner/operation/requestId uniqueness、session/turn唯一。

turnIndex **0起点**，main=2g、follow=2g+1、parent=2g；generation恰好Ngroup/2N、非空且parent唯一。主体保存创建时方向/难度/题量、简历text/version/hash、JD原文/分析快照、question/rubric/model版本，不读取后来current形成结果。简历快照锁用resume子任务稳定接口；不存在任意client resumeId/owner字段。

每个HTTP/后台SQL绑定caller或持久化owner；foreign先404，不先泄露version/status，ADMIN无绕过。所有新private JSON/file/errors明确no-store,private，现社区filter不自动覆盖。异步以PG为权威，不依赖Redis业务锁/进程内Set。

## 2. API与receipt

父HTTP表是接口权威：parse202、JD状态GET/retry/DELETE；create、page/detail、generation/retry、draft、turn submit、complete、evaluation/retry、report GET、DELETE会话。DTO名字/属性保持父typed合同，列表用现有0based page/size及稳定排序，不发明恢复receipt GET。

receipt scope owner+operation+requestId；canonical payload hash包含明确操作/会话/轮次/配置/精确文本或SHA/expectedVersion，不能只查ID。相同ID同payload先返回原最小固定结果，然后GET当前权威；不同payload409。已成功重放优先于旧expectedVersion和后续被删JD，以支持丢ACK。create新ID独立建场，同ID已删除永不重建。

JD解析输入10k码点、精确原文不trim/归一换行；持久成功来源必须owner+direction+rawhash+SUCCEEDED相符。修改原文/方向立即无效，显式移除/确认取消DELETE原analysis正文/result并fence，当前面试快照独立。成功分析可重复多场create；create不consume、不隐式模型调用。

## 3. 状态、短事务与作答

- generation=PENDING/PROCESSING/SUCCEEDED/FAILED；READY前完整验证后一次写题单，成功后不可换题。
- answer=NOT_READY/READY/IN_PROGRESS/COMPLETED；只当前turn draft/submit，submit锁answer+session及receipt，原子SUBMITTED/清draft/推进version和turn。
- evaluation=NOT_STARTED/PENDING/PROCESSING/SUCCEEDED/FAILED；只有首次complete/最后submit或显式retry授权调度，不解锁COMPLETED答卷。
- jobs=PENDING/PROCESSING/SUCCEEDED/FAILED；PENDING是排队，不能混用研究QUEUED名。

session/current answer版本CAS；同锁顺序owner session→相关job/answer/group→receipt。重放成功submit只确认一次，不让迟到draft覆盖；旧标签页/错误轮次/状态明确409。空draft与空submit不新增最小长度：空SUBMITTED仍锁定/推进，需有效评估，不能当UNANSWERED。early complete把未提交轮冻结UNANSWERED/清draft，已答/未答由服务端状态统计，与最后submit竞争只创建一轮首次评估jobs。

## 4. AI gateway与禁止隐式retry

`InterviewAiGateway`：parseJd、generateQuestionSet、evaluateGroup，四方向/三难度模板自行在目标工程实现；仅考察Agent主题不引入tool/RAG运行时，React不默认Vue，全栈跨层业务。用户简历/JD/答案放明确JSON/data边界，不提升system角色，不抓链接/执行代码。

真实SDK AgentScope2.0.3默认maxAttempts=3；新gateway明确：
`GenerateOptions.executionConfig(ExecutionConfig.builder().timeout(operationTimeout).maxAttempts(1).build())`。
import来自 `io.agentscope.core.model.ExecutionConfig/GenerateOptions`；builder实际用法实施时对本机JAR核验。外层blockLast不是禁retry。所有SDK/HTTP层均需一次attempt一次实际发送；fakeHTTP计数5xx/429/断流/timeout/坏结构，发现内部重试即修配置，不自动换模型/修JSON再调。

出题严格完整2N整体验证；坏结构FAILED，不补假题/少题成功。方向/难度/简历/JD固定快照用于全阶段，JD只方向内重点，不把“3–7”变产品固定条数。输出截断/容量拒绝明确失败，不降低20题/正文上限。

## 5. 分组job、lease与恢复

主问+追问一组一job；两轮均未提交的组后端标UNANSWERED0，无模型；任一SUBMITTED（包括空字符串）需模型有效分与反馈，未提交轮由server填0。每组成功结果固定question+answer+rubric/model hash，只重试必要失败/未成功组，成功组不重跑，无多余汇总调用。

专用executor初值core/max=2、queue40；单次model timeout PT4M、PROCESSING lease PT5M、PENDING queue deadline PT45M、expiry扫描30s，可按实际容量证据调整并记录，不是feature flag。PENDING保存queue_deadline，worker真正开始时CAS领取PROCESSING/token/lease；enqueue20组时不能都起5分钟lease。

初次用户parse/create/complete或显式retry是调度来源，replay/GET不调度。排队拒绝/超时、安全模型失败转FAILED；启动与周期recovery只把expired PENDING/PROCESSING标FAILED/撤执行权，不自动模型重试。活lease不误恢复，后续扫描保证新JVM不会永久卡住。

终态短事务必须同时匹配owner、未删除主体、job/input hash、PROCESSING/token、lease>DB now；题单/组result/状态变化同事务，零行失权回滚所有子写。过期、retry新token、delete都使旧成功/失败写入无权。没有外部分布式队列/leader或全局锁。

## 6. 报告与删除

每SUBMITTED结果稳定turnID、有限0..100、非空具体反馈；多/少/错ID/NaN/越界令该组FAILED。UNANSWERED按0并标未答；系统失败分数null/NOT_EVALUATED。全部必要评分齐备才持久化 `sum/(2N)`，BigDecimal两位HALF_UP，scoreSum/totalTurns与评分规则版本可追溯；否则totalScore=null。综合反馈从有效组组织，GET不重算/调模型。

DELETE任意阶段owner场，短事务锁session、使全部jobs失权、删除题单/草稿/答案/JD/评估/简历text快照、主体业务不可读；保无正文minimal receipt/tombstone防create重放复活。别场/current/可复用独立JD草稿不受该场快照删除影响。旧task不得UPSERT回主体；同owner重复delete可确认，foreign仍安全404。模型已发送可能自然结束，不能承诺撤回远程费用。

## 7. 验证、容量与非破坏回退

真实PG/Redis服务验证owner/receipt/并发/token；真实SDK+local fake HTTP证明底层无retry；20main/40turn/最大input用synthetic gateway成功/明确失败两路径。真实模型质量/费用/context延迟需后来授权，不能把fake写真实质量通过。详情/列表/report/轮询counter为零新增调用且不touch activity。

继承父schema guard/no-key/隔离资源要求，commands见implement。只新增schema和业务模块，不改原input/report/社区约束；回退代码保新表/receipt/私有原件，不自动删表/清桶/恢复日常数据、不补旧版兼容或默认禁用。未解决一般问题送父issues，真正范围/兼容/不可逆决定按授权边界处理。
