# 技术设计

- 不引入 DDD；新增 `input` 功能包，包内放置 Controller、Service、Mapper、DTO/Row，并通过现有 `ai` 网关以及 `record`、`task` 持久化能力完成批量落库。
- `InputEntry` 状态为 PROCESSING、SUCCEEDED、FAILED、REVERTED，并保存原文、请求标识和时间基准。
- AI 调用位于数据库事务外；合法结果用短事务整批落库，禁止半批数据。
- `WorkbenchAiGateway` 定义业务接口，`AgentScopeAiGateway` 隔离 SDK 和错误映射。
- 前端用有上限轮询查询处理状态。
