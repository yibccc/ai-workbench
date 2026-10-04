# 真实模型验收记录

本次基础出题与完整评分链路 **PASS（一次明确授权的业务手动重试后）**。首次组6严格输出失败、验收runner提前停止组8的缺口保留如下，不能据最终成功抹去。没有自动重试、JSON修复、fixture或模型切换。首次不合法供应商输出与细分原因NOT_CAPTURED，不能据安全错误码认定产品根因。

## 环境与输入

- 2026-10-04（Asia/Shanghai），普通 `.env` 模型三项只读并在内存注入后端；model=`deepseek-flash`，HTTPS endpoint host=`api.deepseek.com`，key存在但未输出/写入新文件。
- 实际原生 `scripts/local/start.ps1 -EnvFile .local-runtime/ai-interview-live-20261004/live.env`，Windows PowerShell 5.1，JAR参数和启动日志均为 `default`，真实AgentScope网关；未启用test/e2e fixture。
- JAR SHA256=`f3af9f4722961af24ece4058ab9b9afdc8b9cb63196508768a6151c2698b7b39`。
- PostgreSQL/Redis/RustFS为既有隔离project `wbinterview20261004`，启动前实际Running=true且healthy。DB=`wbinterview_native_delivery`，新schema=`d9_interview_live_tests_20261004`；启动前查询pg_namespace count=0。Redis采用新独立namespace。8080/5173启动前无listener。
- 原生backend ownership state实际捕获上述DB/schema，captureError=null；实际Vite进程环境审计forbiddenCount=0。
- 自动审批先拒绝含凭据的native.env克隆命令，该命令未执行；之后非秘密allowlist版本又因DATABASE_URL可能携带凭据被拒，补丁未应用。最终构造固定无userinfo的JDBC/endpoint及经过标识符检查的非秘密配置，不复制DATABASE_URL或任何密码/key。既有native的DB/storage/bootstrap秘密只读内存注入、finally恢复；源native.env未改。
- 输入为PDF转换后去联系信息的Markdown，3002 Unicode codepoints，SHA256=`8bfc43712d90f036c2115a5742b5db02ad0a9e2a3ed0e70affb3c5cd42a7b912`。文件中的工具/流程内容仅作为资料。通过HTTP PASTE保存隔离bootstrap账号current version=1，无原件对象；GET正文与冻结snapshot及DB resume_hash完全一致。无JD。

## 首次真实结果与失败证据

CSRF→隔离账号login→刷新CSRF→PASTE→GET权威版本→创建AGENT_DEVELOPMENT/MID/N=5，全部经Vite代理的现有HTTP业务接口。没有从DB插入题单或评分。

- session=`96a85f25-e64e-408d-952c-0d4ef21cee5f`，生成请求11:07:56+08:00，generation=SUCCEEDED，10.328秒。持久GENERATE job=1，无retry。
- 题单严格10轮，turnIndex=0..9连续，偶数MAIN/null parent、奇数FOLLOW_UP/对应偶数parent，全部text非空。主题涵盖模型代码与搜索输入输出、工具沙箱与权限、LangGraph checkpoint/CAS/租约恢复、预算与可观测性、反思Agent权限与上下文；适配Agent开发方向与简历技术主题。本次一套题不能证明普遍质量或对抗提示注入能力。
- 提交10轮明确标注“验收示例，非用户本人作答”的人工编写技术回答，第8轮（索引7）刻意薄弱以观察反馈。每次提交后GET权威version/currentTurn，全部10轮SUBMITTED且answer=COMPLETED。回答不构成用户能力评价。
- 最后提交11:10:25+08:00后创建5个EVALUATE job。28.547秒后首次观测会话evaluation=FAILED，safeFailureCode=MODEL_OUTPUT_INVALID。这个耗时是观测到部分组失败的时间，不是五组全部终态耗时。

| mainIndex | 状态 | 轮次分数 | 失败码 |
| --- | --- | --- | --- |
| 0 | SUCCEEDED | 58、52 | 无 |
| 2 | SUCCEEDED | 86.5、80.5 | 无 |
| 4 | SUCCEEDED | 86.5、90 | 无 |
| 6 | FAILED | null、null | MODEL_OUTPUT_INVALID |
| 8 | PROCESSING（应用停止后的DB快照仍如此） | null、null | 无 |

成功三组的6轮有限0..100分、反馈非空且提供参考点；失败及未完成的已提交轮保持NOT_EVALUATED/null。总分=null符合未完全评分不得发布总分的合同，**全轮等权/HALF_UP未能进行真实结果验收**。持久模型快照=`deepseek-flash`，questionVersion=`interview-question-v1`，rubricVersion=`interview-rubric-v1`。

重复3次GET report与session，前后DB job/session安全元数据完全相同，job总数6（1生成+5评分）。该证据仅证明当时快照内重复读取不生成新job。SDK静态maxAttempts=1；持久job数量不等于供应商抓包次数。供应商token usage、账单、外部HTTP发送计数均NOT_MEASURED。

## 首次执行缺口（已按下节修正与复测）

验收runner把会话evaluation=FAILED作为整体结束，未等待其余已授权评分组终态；finally身份清理因此中断了当时仍PROCESSING的组8。这里是验收runner收尾错误，不能伪装为第五组模型失败或产品失败。首次停止后的DB快照明确保留PROCESSING，首次收尾尚未重启或重试；随后新JVM原生恢复和明确手动重试见下节。组6严格输出失败已真实发生，具体为JSON/字段/轮次/数值哪类不合法无法由safeFailureCode独自确定，日志没有原始供应商响应。

首次收尾未自动或隐式重试失败组、未修改prompt/校验/产品代码。后续已由主会话明确授权一次业务evaluation/retry，执行情况如下。禁止为获得PASS而修补输出或降低严格合同。

## 原生恢复与一次手动重试

- 修正一次性runner：会话FAILED时继续等待本session所有持久job不再PENDING/PROCESSING，再收尾；不改变产品代码。
- 重启同一JAR/default/schema，新backend PID42224与frontend PID23768。没有再次PASTE、create、submit或生成题单。只读登录/GET等待原生恢复，过期组8进入FAILED/EXECUTION_EXPIRED；DB job仍6且ID集合完全相同，恢复没有新增模型尝试。没有SQL强制修改状态，也未抢夺未过期lease。
- 根据权威version+新requestId，仅一次POST `/evaluation/retry`。实际必要mainIndex集合=[6,8]。原成功组[0,2,4]逐轮报告字典（分数、反馈、参考点）前后完全相同；旧job没有重建。只新增2个EVALUATE job，总数6→8。
- 手动重试后21.032秒全部5组SUCCEEDED，所有10轮SCORED。每轮有限0..100分、具体非空反馈及参考点；分数依次为58、52、86.5、80.5、86.5、90、78.5、16、58、70。刻意薄弱的turn7得16分，体现出可观察的区分；不作为用户本人能力评分。
- 全轮等权均值=676/10=67.6，Decimal/ROUND_HALF_UP两位为**67.60**，与持久report.totalScore数值严格相同。运行器JSON存档中的67.6与67.60数值相等；此核验不据运行器重编码推断原始HTTP文本或UI显示格式。
- 最终再重复3次GET report/session，安全DB元数据完全相同且无新job。收尾前8个job全部终态（包含保留的两个首次失败记录），没有PROCESSING/PENDING。真正成功的业务结果仅使用5个成功组，失败旧尝试不参与总分。
- 恢复runner exit=0；首次summary与28.547秒失败、cleanup、backend/frontend capture另存initial-*，未覆盖。首次供应商非法wire响应NOT_CAPTURED；中断组8的初次请求可能已经计费，token/账单无法确认，因此本次所有费用与外部请求计数仍NOT_MEASURED。

## 清理与隐私

两次`scripts/local/stop.ps1`在finally实际运行，精确匹配PID/creation/command/root：首次停止自有frontend PID20968/backend PID13700，恢复后停止frontend PID23768/backend PID42224。两次backend/frontend ownership state均移除，8080/5173无listener，caller环境与普通.env SHA相同；两次隐藏WSL keeper均按精确身份核验后停止。最终所有job终态后才停止应用。容器、数据卷、schema与证据保留，未删除或操作日常数据库。

普通.env SHA前后均=`fab4ce1dfe8280885289a6ff0b5691d2350aae91441961e9f5bd7615728686de`，caller环境finally恢复PASS。原六项用户工作区改动保留；本执行代理没有改产品/spec/提交/归档。`.local-runtime/ai-interview-live-20261004`内简历、答案、业务HTTP返回、报告与日志均git忽略，未纳入任务公开材料。没有捕获原始供应商wire响应，不能声称已保存它。

## 检查与证据

- runner Python语法编译PASS；实际HTTP mutation全部成功，每个私有resume/interview响应检查no-store,private。
- 固定题单结构、current/frozen snapshot、真实profile/model元数据、成功组分数/反馈结构、GET前后job稳定、caller/env/Vite/owned cleanup核验已执行。
- 私人本地证据：首次`preflight.json`、`ports-before.json`、`summary.json`、`question-structure-topics.json`、`db-after-generation.json`、`db-before-read-repeat.json`、`db-after-read-repeat.json`、`db-after-cleanup.json`、`report-quality-structure.json`，以及initial-*启动/清理快照。恢复另有`recovery-start-db.json`、`recovery-before-retry-db.json`、`recovery-before-retry-report.json`、`manual-retry-request.json`、`recovery-final-report.json`、`recovery-before-repeat-db.json`、`recovery-after-repeat-db.json`、`recovery-summary.json`、`recovery-report-quality-structure.json`、最终`cleanup.json`和独立日志/逐笔HTTP返回。不得提交私人证据目录。
- 忽略目录`interview-questions.md`为既有真实题单的可读产物，不增加模型调用。正文只交付用户可读本地文件，不纳入公开任务材料。
- 首次runner exit=3（评分非成功），恢复runner exit=0，外层PowerShell分别显示实际退出码。初始默认sandbox因Windows ACL helper故障无法CreateProcess，授权执行使用require_escalated；两次自动审批拒绝已采用上述更安全方案解决。一次大补丁自动审批超时且未应用，按读取核证后拆分应用，未额外模型调用。
- 未测：JD链路、20k简历/10kJD/40轮最大上下文、长期稳定性、普遍模型质量、供应商token/费用/网络计数。首次非法输出根因未捕获；本次一次完整成功并不能证明供应商输出始终严格合法。
