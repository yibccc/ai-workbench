# 真实模型验收独立复核

状态：PASS（一次明确的业务手动评分重试后）。真实基础链路及隔离/隐私/清理已独立核证；首次模型结构失败与验收 runner 提前停止均如实保留。此结论不代表首次全部成功或普遍模型质量通过。

## 静态核查

- 已完整读取原生注入保存文件、check.jsonl、PRD、design、implement 及三份适用 spec。
- `AgentScopeInterviewAiGateway` 的 profile 为 `(!test & !e2e) | live-acceptance`；`DeterministicInterviewAiGateway` 仅为 `(test | e2e) & !live-acceptance`。既定 start 明确启动 `default`。
- 真实网关每次 `GenerateOptions` 设置 SDK `maxAttempts(1)`、操作超时，无工具、修复调用或 fallback。
- start 将模型配置保留于后端进程环境；启动 Vite 前过滤 DEEPSEEK、DB、storage、bootstrap 等变量，finally 恢复 caller。stop 以 PID、创建时间、完整命令、仓库根路径验证身份。
- `git check-ignore` 确认本次简历输入、原始 report 及 runtime env 路径均被忽略。当前 Git 状态仅含原六项改动和新任务目录。
- 独立记录 JAR SHA、普通 .env SHA、原五个未跟踪文档 SHA 和既有规则文件删除状态，供运行后比较。未输出配置值。

## 首次真实运行

- preflight 使用只读查询证实新 schema 原先不存在，三个既有隔离容器 Running/healthy，8080/5173 原先空闲。新 live.env 不含凭据；运行身份记录确认 JDK17/JAR/default、目标隔离数据库和 schema，captureError=null。
- 实际 Vite Node 进程环境审计 forbiddenCount=0。JAR、普通 .env、简历输入 SHA 与独立基线一致；业务 GET 证实 PASTE 无伪原文件，面试快照正文/版本与该输入完全一致。
- 一次 GENERATE job 成功，固定 AGENT_DEVELOPMENT/MID、5 主问和 5 预生成追问；独立重算连续 0..9、类型和父索引，全部合法。冻结模型为 deepseek-flash，题单约 10.328s 可读。
- 10 条回答都有明确“验收示例，非用户本人作答”标签；业务 submit 后权威 GET 验证每轮进度，最后提交冻结整张答卷并创建五组评估。
- 首次结果为三组 SUCCEEDED、mainIndex=6 为 FAILED/MODEL_OUTPUT_INVALID、mainIndex=8 尚为 PROCESSING。0..5 为 SCORED，六个评分有限且在 0..100；6..9 为 NOT_EVALUATED/null。总分和 overallFeedback 保持 null 符合合同，不能将未评分轮算成 0，也不能以已成功六轮平均替代全轮总分。
- 三次 report/session GET 前后 DB snapshot 完全相同，仍为六个 job。代码只读路径无模型调用；job 数不等于供应商 HTTP 抓包次数。
- 首次约 28.547s 是请求至首个可读失败的时间，不是五组全部完成的时间。usage、billing、externalRequestCount 均 NOT_MEASURED。
- 首次 cleanup 证实 caller 环境恢复、精确身份 stop 完成、PID state 与端口清理、keeper 已停止，普通 .env 前后 SHA 一致。数据库和私人本地证据保留。

## 明确手动重试后的最终证据

- 原 schema、JAR、default 在新 JVM 中恢复；旧的 mainIndex=8 job 原生转为 FAILED/EXECUTION_EXPIRED。只读恢复前后仍为原六个 job，未直接修改 SQL lease/state，也未自动调用模型。
- 仅一次业务 `evaluation/retry`，新增恰好 mainIndex=6、8 的两个 EVALUATE job；合计一条 GENERATE 和七条 EVALUATE 历史 job。新 job 的 inputHash 与各自原尝试相同，冻结模型/简历 SHA 未变。
- 原成功组 0、2、4 的 job 完整字典及 0..5 每轮报告完整字典均未变化，题单、简历快照、方向/难度与已冻结答卷也保持。没有重新生成题目或重提回答。
- 最终五组均 SUCCEEDED，十轮均 SCORED、有限 0..100 且反馈非空。独立使用 Decimal 读取与计算：分数为 `[58,52,86.5,80.5,86.5,90,78.5,16,58,70]`，和为 676.0，十轮等权平均、两位 HALF_UP 为 **67.60**，与真实 report 一致。这是验收示例回答的分数。
- 明确手动重试请求至全部 job 终态/可读报告约 21.032s；首次的 28.547s 失败观察仍单独保存。最终八个历史 job 均为 SUCCEEDED/FAILED 终态；报告/会话再读三次，DB snapshot 完全相同，零新增 job。
- 最终 cleanup 证据全部通过；独立查询再次确认 8080/5173 无监听、backend/frontend 身份文件不存在。普通 .env/JAR 摘要、原五份未跟踪文档摘要和既有规则删除状态与独立基线完全一致。
- 初次证据、重试证据和原始业务正文都在忽略目录分开保留。独立扫描本次 11 个可提交的新/改动路径，实际凭据、简历/回答/反馈全文命中均为 0；Git 追踪的 `.local-runtime` 文件数为 0。

## 本次样例的内容观察

已核阅脱敏技术片段，未只凭 topics 正则作质量结论。五主问覆盖候选生成/执行/独立评测接口、工具权限和宿主核验、持久执行权/恢复、评估/预算/泄漏、专家上下文隔离；追问分别细化噪声绑定、临时授权、状态冲突和副作用、样本/停止条件、权限继承。方向及项目语境一致，可按设计回答；多子点问题偏具体工程深度。题目所讨论的简历流程没有被当成本次执行指令。

十条有效反馈具体指出回答覆盖内容和缺项：候选版本绑定/终止条件、诊断噪声消歧、权限/沙箱边界、临时授权审计、事件重放、checkpoint 权威性与幂等、评估泄漏和预算停止、专家配置的权限继承。明确薄弱的预算回答得 16；偏通用原则但遗漏一次性 hook/渐进披露的回答得 58，反馈说明未覆盖的题干要点，存在内容区分度。主问的多子点和项目术语使本样例更偏工程深度，不能从这一次成功推断普遍评分稳定性。本观察不代表用户能力评估、全方向质量或最大上下文容量通过。

## Findings (fixed)

- File：`.local-runtime/ai-interview-live-20261004/runner.py`（忽略目录，执行代理修改）。
- Issue：首次 poll 将 session FAILED 当作并行评分全部结束，提前停止仍 PROCESSING 的组 8。
- Fix：执行代理补充 all-jobs 终态判定；独立读代码并以最终八个历史 job 全终态、三次 GET 稳定和收尾证据核证。修复不增加服务/SDK 自动重试；仅主明确授权的一次业务手动 retry 补齐两个未成功组。
- 本审查代理仅维护 check-report，没有修改产品代码、配置或私人运行证据。

## Findings (not fixed)

- mainIndex=6 首次 MODEL_OUTPUT_INVALID 的具体供应商输出细因未诊断：原供应商 wire/失败正文 NOT_CAPTURED，安全业务响应不足以判断是索引、JSON token 或其他结构项。后续同输入手动重试成功，首次 FAILED 记录仍保留。缺少确定根因，且更改产品 prompt/解码合同超出本轮验证范围，因此未改产品或添加兜底；没有剩余阻止本次基础链路验收完成的已确认产品缺陷。
- usage/账单 NOT_MEASURED，外部 HTTP 次数 NOT_MEASURED/未抓包；最大上下文、真实 JD、全方向和大规模质量均未测。上述为测量边界，不能由 job 计数或本次样例补推。

## Verification

- Lint：PASS，`frontend/npm.cmd run lint`，exit 0。
- TypeCheck：PASS，`node node_modules/typescript/bin/tsc -b --noEmit`，exit 0。
- 静态隔离与隐私：PASS。
- Tests：本次真实基础链路 PASS（一次明确手动 retry 后）；独立题单/快照/数学/复用/GET 稳定/清理断言均通过。
- 本地验收脚本语法：三个 Python AST 和三个 PowerShell parser 检查 PASS；`git diff --check` PASS。一次只读自动审批超时后重试成功，无遗留阻塞。
- 没有产品源码变更，未重跑原整套测试。新增真实验收 spec 段准确、无产品行为变更。
