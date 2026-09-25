# 归属隔离实施计划

前置：父任务最终规划获批准，认证子任务已固定 userId、Principal/管理边界；只在本子任务被原生 Trellis 激活后执行。

1. 对照 `research/handoff-check.md` 与全量 Controller/Service/Mapper/迁移/测试清单，再核查当前分支改动。建立新迁移、复合唯一及 owner 约束，在隔离空库和原迁移路径验证；旧迁移只读。
2. 让所有业务接口从认证主体得到 owner，Service/Mapper 的查询、计数、详情、变更、关联改为 owner 限制；先证实归属再返回业务冲突，确保他人 UUID 为 404 且写入原子性。
3. 改输入/报告持久请求和异步执行链；把原始 owner 贯穿模型上下文、重试/撤销/恢复、生成子项、报告取材/来源/版本锁及 after-commit 事件。联合实时子任务改通知载荷，不让默认 ADMIN 或当前 HTTP Session 代替原 owner。
4. 以 A/B/ADMIN 和确定性 AI 替身验证 TC-020～025、TC-039，包含分页总数、同名项目、相同 request ID、并发周报链、迟到异步结果、跨用户 404、无部分写入；复用现有业务集成测试并增加身份样本。
5. Review 全部 Mapper XML、调用链、事务/锁及数据库约束；检查来源分页和软删除/版本回归。全范围运行 `backend/` 下 `mvn clean verify`。真实 PostgreSQL 隔离验证可使用已确认可用的 WSL Ubuntu Docker/Compose，但本轮只运行配置解析；未启动隔离数据库时不得声称 TC 通过。

回滚点是迁移前隔离库/配置快照；若迁移或权限测试失败，不把旧无 owner 应用指向新多人库。
