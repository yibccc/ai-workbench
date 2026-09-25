# 归属改造需保留的现有数据库合同

此索引来自已读 `.trellis/spec/backend/database-guidelines.md`（原文件 35,795 字节，超过单文件自动注入上限）；实施/检查时按需打开原文件的相应完整章节。它不替代原规范，也不新增产品行为。

| 原规范章节 | 归属改造不能破坏的边界 |
| --- | --- |
| 行 5～114：PostgreSQL persistence | Flyway 旧迁移不可改；时间戳和 Asia/Shanghai `[start,end)`；MyBatis XML/UUID handler；归档项目历史关联与新关联活动项目要求；真实 PostgreSQL 约束测试。项目名唯一从全局改为用户内是本需求的唯一有意变化。 |
| 行 115～198：Task management | 任务状态/优先级/到期过滤、乐观版本、删除版本与 400/404/409 边界；不能借 owner 改为无条件更新。 |
| 行 199～283：Task completion | 完成/重开/删除、自动工作记录与事件在同一事务；重复转换只读幂等；软删除历史与活动记录唯一不变。 |
| 行 284～364：AI capture | 原文先持久化、模型调用在事务外、结果批次短事务原子保存；request ID 定义幂等。新增 owner 维度不能让不同用户互相复用请求。 |
| 行 365～453：Capture recovery | PostgreSQL processing token/lease 是处理权威；旧 owner 不能覆盖新 token；启动恢复只处理过期，整批撤销保留 ledger 与版本验证。 |
| 行 454～530：Daily report | 完整来源冻结快照、同日多版本、request ID 幂等、日报编辑和错误状态；不从实时原表重读历史来源。 |
| 行 531～末尾：Weekly report | 上海自然周、三种 source role、前驱链尾和事务 advisory lock、人工补充与 AI 正文分离；用户维度须进入锁键、链查询及来源。 |

相关补充规范：`.trellis/spec/backend/pagination.md`、`report-deletion.md`。本任务的 owner/404 要求见 `../prd.md` 和父任务 `prd.md`，具体 SQL 调用链见 `../design.md`；规范原文中的历史类位置若与当前包结构不同，以当前代码和 `directory-structure.md` 为准。
