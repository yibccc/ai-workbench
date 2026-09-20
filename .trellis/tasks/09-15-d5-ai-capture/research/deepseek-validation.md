# D5 DeepSeek 真实验收

- 验收时间：2026-09-20 00:40—00:42（Asia/Shanghai）
- 模型：`deepseek-flash`
- AgentScope Java：`2.0.3`
- 接口方式：AgentScope `OpenAIChatModel` + `DeepSeekFormatter`，非流式；关闭 AgentScope 原生 structured-output 开关，由提示词要求严格 JSON，并由 Jackson 和业务规则二次校验。
- 密钥来源：仓库根目录被 Git 忽略的 `.env`，仅注入后端进程；未记录或输出密钥。
- 混合输入：一条已完成的 D5 数据库迁移/替身测试事实，加一条次日下午六点前完成浏览器验收的高优先级待办，并指定已有活动项目“AI工作台”。

## 结果

真实请求先返回并持久化 `PROCESSING`，随后通过 `GET /api/inputs/{id}` 有限轮询取得终态。模型生成：

- 1 条工作记录，匹配已有活动项目“AI工作台”。
- 1 条高优先级待办，匹配同一项目，截止时间解析为 `2026-09-21T10:00:00Z`（Asia/Shanghai 18:00）。
- 最终状态 `SUCCEEDED`，记录与待办各自拥有可编辑实体 ID。

首次尝试暴露了本地校验问题：验收发生在凌晨，模型将用户明确声明的“今天上午已完成”解析到同日上午，本地原先以“晚于当前基准”为由拒绝。该批次按设计完整回滚并保留原文为 `FAILED`。移除这一不成立的机械推断后，对同一输入执行 retry；`referenceAt` 仍为首次的 `2026-09-19T16:40:31.364134Z`、时区仍为 `Asia/Shanghai`，`attemptCount` 从 1 增至 2，并成功整批落库。

## 限制

- 模型对事实/行动的分类仍是概率性结果；后端验证结构、字段上限、ISO 时间、活动项目映射、优先级回退和整批原子性，但不能凭当前时钟否定用户明确声明的事实。
- D5 只提供基础失败重试与单进程重复调度保护；宕机恢复、跨进程并发重试、撤销属于 D6。
- DeepSeek JSON 输出偶尔可能为空；当前会安全落为 `FAILED`，用户可沿用首次基准重试。

## 官方依据

- AgentScope Java 模型文档：DeepSeek 等兼容提供商使用 `OpenAIChatModel`、`DeepSeekFormatter`，并关闭不兼容的原生 structured-output 能力。
- DeepSeek JSON Output 文档：提示中明确要求 JSON、提供目标格式并为输出保留足够长度；应用仍必须执行自己的业务校验。
