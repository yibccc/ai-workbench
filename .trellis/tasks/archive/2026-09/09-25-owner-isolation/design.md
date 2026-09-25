# 用户归属技术设计

## 已核实代码与数据模型

当前 `V1__initial_workbench_schema.sql` 的 projects/todo_items/work_records/capture_inputs/reports 均无 user_id；V1 项目活动名唯一为全局 lower(name)，V5 输入 request ID 与 V8 报告 request ID 是全局唯一。`ProjectMapper.xml`、`InputMapper.xml`、`ReportMapper.xml` 的查询、写入及报告取材不带 owner；`InputRecoveryRunner` 在启动时恢复超时处理。`ReportPersistenceServiceImpl` 用全局周期找 previous report；`WorkbenchEventHub` 目前全局广播。新增**后续** Flyway 迁移，绝不改 V1～V12。

主表 `projects`、`capture_inputs`、`todo_items`、`work_records`、`reports` 增加非空 `user_id` 并建 owner+常用排序/状态索引。用户表来自认证子任务；空白业务数据起点允许迁移直接建立非空约束，无旧数据回填。项目活动名唯一改为 `(user_id, lower(name))` 的部分唯一索引；输入/报告幂等唯一改为 `(user_id, request_id)`；报告周期版本锁键和终点/前驱查询包含 owner。项目关联与报告前驱能用复合键/外键时在数据库加同 owner 约束；polymorphic 生成项、来源快照仍由归属受限的父对象查询与 Service 校验。保留历史已归档项目和软删除关联语义。

| 路径 | 必须传递/核验的 owner |
| --- | --- |
| `ProjectController/Service/Mapper` | 创建、详情、活动/归档列表与分页、搜索计数、改名/归档和项目 picker 均按 userId。 |
| `TaskController/TaskService/TaskMapper`、`WorkRecordController/Service/Mapper` | 所有列表/详情/分页/状态转换/事件/软删除/记录编辑带 owner；projectId 必须属于当前用户且满足原活动/历史规则。 |
| `InputController/InputService/InputPersistenceService/InputMapper` | 提交、request ID 查找、处理 token、结果、retry/revert、生成子项和恢复都从持久 input 的 owner 派生；异步线程不依赖 SecurityContext。 |
| `ReportController/ReportService/ReportPersistenceService/ReportMapper` | 同周期锁/previous 链、取材、来源分页及总数、编辑/删除、错误诊断均带 owner；快照与源实体同属本人，完整来源不受 UI 分页限制。 |
| 事件发布 | after-commit 输入/报告通知携带持久 owner 给实时子任务；事务回滚不发成功通知。 |

Service 以认证 userId 调用 Mapper 的 owner 参数；按 UUID 获取时先做 `WHERE id=? AND user_id=?`，找不到即 404，再判断归档/版本/状态，避免泄漏他人资源状态。UPDATE/DELETE 同样带 owner 且核验受影响行数。写入新主实体时 owner 只从服务端身份/持久请求来；子实体外键和 owner 一致性要在事务内验证。报告的 AI 上下文、项目名解析、来源聚合都按原 owner 过滤；原短事务/租约及补偿行为不变。

## 兼容与回滚

空库部署是已确认产品起点，但目标数据库未实查，不能在迁移里悄悄删除旧数据。若部署库仍有旧业务行，新非空 owner 迁移应阻断并要求核实清理目标，不替旧行编造归属。新多人数据上线后不允许旧全局查询应用回连；失败时关闭入口并恢复匹配的应用/数据库快照。既有测试需增加 A/B/ADMIN 样本，不能只改单用户断言。
