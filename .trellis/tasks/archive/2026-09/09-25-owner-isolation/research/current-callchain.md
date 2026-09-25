# 归属改造的当前调用链核对（实施前只读）

本文件基于当前 HEAD 的服务与 Mapper 静态读取。认证子任务正在提供 `CurrentUser`，其精确签名以实施完成后的契约为准。

| 路径 | 当前调用链/SQL 风险 | 归属改造核对点 |
| --- | --- | --- |
| 项目 | `ProjectServiceImpl` 的 `create/list/page/get/rename/archive/requireActive` 进入 `ProjectMapper.xml`；该 XML 的所有读写均无 user_id 条件。 | 列表及 PageHelper 总数、详情、改名/归档、活动项目验证一并带 owner；他人 ID 应先得 404。 |
| 待办与记录 | `TaskServiceImpl` 有 `create/list/page/get/update/delete/complete/reopen/updateCompletionResult/events`，同事务写 `WorkRecordMapper` 自动完成记录及 `TaskMapper` 事件；`WorkRecordServiceImpl` 另有记录 CRUD。 | 对任务/记录主行、项目关联、任务事件和自动完成记录一同约束 owner；保留版本、幂等与事务边界。 |
| AI 输入 | `InputServiceImpl.create/retry` 后调用 `schedule/process`；`process` 通过项目列表解析模型项目名。`InputPersistenceServiceImpl` 的 `createOrGet/succeed/fail/revert/recoverExpiredProcessing` 以及 `InputMapper.xml` 的 request ID、生成项和撤销 SQL 均无 owner。 | 异步 `process` 不应从当前 HTTP Session 取 owner；应从持久 input 取 owner，再获取该用户的项目列表。`requestId` 唯一约束/查找按用户，生成记录/待办继承 input owner，恢复后也保留。 |
| 报告 | `ReportPersistenceServiceImpl.prepare` 目前用仅含 `periodStart` 的周报 advisory lock、全局 `findLatest`、全局候选来源；`ReportServiceImpl` 异步 `process` 读取冻结来源。`ReportMapper.xml` 的列表、来源分页/计数、编辑/删除仍无 owner。 | 锁键、版本链尾、request ID、五类来源选择、报告内容/来源 API 均按 owner；事务外 AI 取冻结来源时以已持久 report owner 为依据。 |
| 事件 | `InputPersistenceServiceImpl` 和 `ReportPersistenceServiceImpl` 在多处调用 `WorkbenchEventHub.publishAfterCommit(kind,id,state)`，目前事件没有 owner 且全连接广播。 | 与实时子任务约定 owner 参数来自持久 input/report；提交后才发。不能只给 HTTP Controller 加 userId 参数而漏掉后台完成/失败。 |

实施复核范围还包括全部对应 Mapper Java 接口、DTO/entity 行映射和现有 PostgreSQL 集成测试；这里只标关键交叉点，不将局部搜索结果当作全调用链已验证。
