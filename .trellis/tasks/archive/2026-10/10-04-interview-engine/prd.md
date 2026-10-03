# AI 面试引擎

## 目标、来源与依赖

交付账号私有的 JD 解析、固定文字题单、逐轮草稿/锁定提交、整场评估、历史与删除，使多场面试独立恢复且模型失败不伪装低分。来源为父 `../10-04-ai-interview/prd.md` 及不可变 R1 包；父 FR/INT-AC 是权威，不重新编号或修改正文。

依赖 `../10-04-interview-resume` 的 owner current读取、单调version、锁定快照接口完成并验收；再启动本子任务。UI骨架可并行，真实联调待引擎API稳定。父P-01/最新规划批准前保持planning；本文件不代表已实现/获批。

## 需求与范围

继承 FR-005～020、022～026；与 FR-001～004/021 通过简历快照/原件隔离交接：
- 四方向与三难度冻结，默认5、全部整数3–20主问，每主问一条预生成追问，恰好2N轮。
- create固定server current简历/version/hash快照或NONE，不收客户端替代正文/owner/resumeId；同requestId同payload一次，不同payload冲突；新requestId总是独立新场。
- 可选JD仅粘贴文本，用户显式解析，owner+direction+精确原文hash绑定；改任一字符/方向失效。成功分析可用于多场，创建只copy，不强制重解析；明确移除/取消才清分析。
- 当前轮草稿可存不推进；submit原子锁定/推进一次，空字符串不加最低长度、仍SUBMITTED并需有效评估；未提交交卷的轮次才UNANSWERED。
- 最后轮/提前交卷冻结并首次评估；未答按0且标未答；所有必要已提交轮次得到有效0–100/反馈后才发布后端等权总分。
- JD/出题/评估失败仅手动retry；SDK自动retry显式关闭。读列表/详情/report/轮询不能触发模型或续显式activity。
- 多场独立snapshot/题单/草稿/答案/进度，删除任意阶段单场擦正文，迟到token/create重放不得复活。
- 持久job、worker领取后lease、queue deadline、启动+周期过期转FAILED；不自动模型重试、不永久PROCESSING。
- JD10k、answer5k码点，max20main/40turn组合完整成功或明确失败，不截断/残缺题单/伪总分。

## 本子任务验收

全部目前NOT_RUN；父26INT-AC最终整合负责，不将fake质量或规划门禁写产品PASS。

| 引用INT-AC | 可判定结果与方法 |
|---|---|
| 001 | A/B/ADMIN真实HTTP+PG交叉读写无泄露/无写，异步owner与每条SQL均正确 |
| 002/015/017 | N3/5/20严格2N，全部3..20参数；非法结构不READY，四方向/三难度fixture一致 |
| 003/004 | 独立事务双submit/旧版本/丢ACKreceipt只推进一次；draft恢复不锁定、不覆盖已锁答案 |
| 005/006/007 | freeze竞争一次、UNANSWERED0；固定平均两位HALF_UP；坏JSON/错号/失败无完整总分，答卷可手动retry |
| 009/014 | server current expectedVersion原子快照；current改/删不改变已有场和retry |
| 010/016 | 用户文本只作数据无执行/抓取；真实AgentScope+本地计数HTTP证明无SDKretry、GET零调用 |
| 018/019 | JD方向/hash/owner/成功状态严格绑定，改文失效，失败不静默无JD；同成功分析多场copy |
| 020/021/022 | current与他场不受单场删除影响；各阶段删除/旧token/重放无法恢复敏感正文 |
| 023/024/025 | Unicode等号/+1、空submit仍锁定评分；queued/lease有效过期/实际重启仅失败不调模型；最大组合可控fake端到端 |
| 026 | 两场+第三场独立配置/保存/恢复，删一场不动其他，同createID重放无重复 |

## 非目标、约束与权限

不做语音/RAG/动态追问/第五方向/判题/独立服务/队列中间件/公开面试/自动模型retry/回收站/按天删/兼容层/默认禁用。实际云adapter/API申请/迁移与真实付费模型质量延期；本任务复用目标栈与自有代码/原型，不复制AGPL Java/Prompt/Skill。

P-01由父最后确认；父最终“批准并连续执行”若包括本地提交/归档收尾则遵循该授权，不重复问同一权限；当前尚未批准，不start/产品改动/提交/归档。新增产品范围/具体兼容/不可逆/云发布按独立明确授权。一般问题记父issues末尾汇报。
