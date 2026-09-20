# 技术设计

- 不引入 DDD；新增 `report` 功能包，包内组织 Controller、Service、Mapper、DTO/Row，并复用现有 AI 网关与 record/task 查询契约。
- 日期查询使用左闭右开区间，由 Asia/Shanghai 边界转换为存储时间。
- Report 保存周期、状态、正文、来源标识和内容快照。
- 模型返回来源标识，后端验证其属于本次候选集合。
- 报告生成使用独立请求标识和 PROCESSING/SUCCEEDED/FAILED 状态。
- 每次生成创建独立报告版本；失败只更新本次请求，不覆盖任何已有成功或已编辑草稿。
