# 一页架构说明

单用户工作台，后端采用 Controller、Service、Mapper 三层架构，前端按 features、components、api、hooks、utils 组织。浏览器通过同源代理调用 Spring Boot，DeepSeek 凭据由后端读取。本机模式使用回环地址；Docker 模式由带认证的 Nginx 提供入口，后端通过容器内部网络访问。

```text
React features → HTTP controller → service 接口 → service.impl → MyBatis mapper/XML → PostgreSQL
                                           ↓
                                    AgentScope → DeepSeek
事务提交后 → WebSocket 状态通知 → 浏览器 GET 权威详情
```

- **持久化**：PostgreSQL 保存原文、项目、待办、记录和报告，数据库约束与事务保证数据一致性。Flyway 管理迁移，PageHelper 提供列表分页；报告生成读取所选周期的完整来源集合。
- **事务**：完成待办、自动完成记录和事件在同一短事务中提交；重开使自动记录失效且保留历史。乐观版本避免编辑覆盖。AI 编排与持久化是独立 Spring Bean：先提交原文/请求与快照，事务外调用模型，再短事务写整批结果。
- **幂等和恢复**：requestId 标识一次用户请求，数据库 token 和租约控制处理权。启动时回收过期处理任务，失败输入可手动重试。批次清单记录生成条目的初始版本，撤销前据此检查后续修改。
- **报告证据**：每次手动生成独立版本，日报指定上海日期、周报按自然周取数。来源文字、角色和项目归属保存为快照，校验来源别名后生成正文引用。周报通过数据库锁形成线性版本链，人工补充单独存储；日报软删除保留快照。
- **AgentScope 职责**：封装模型调用、提示和输出解析。业务服务校验 JSON、来源角色和字段约束，再将结果写入数据库。失败请求提供手动重试入口。
- **实时与隔离**：事务提交后通过 WebSocket 通知状态变化，浏览器用 GET 获取详情并在断线后同步。test/e2e 使用隔离 schema 和确定性模型替身；报告由用户手动发起生成。

备份采用 PostgreSQL custom archive，包含来源快照与迁移历史。恢复先在新库验证，再切换应用连接。143 来源报告的真实模型耗时仍待验证。
