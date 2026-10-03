# 发布核心执行清单

依赖：父最新规划经用户批准、父及本任务context validate。启动本子任务而非父；具体环境/完整门禁见父`implement.md`与`research/storage.md`。

1. 对照父合同实现V17、实体/Mapper/XML；空隔离schema所有Flyway迁移及同owner FK/唯一约束测试。
2. 增private作者CRUD/profile、public分页/详情/作者投影，严格no-store/owner/members。
3. 草稿save/version409，publish完整预览payload事务requestId/fingerprint/保留版本/固定result_version/首次发布时间，title/type/date/blog summary一起冻结，withdraw与ADMIN expectedRevisionId/hide/reason审计。
4. 日素材分页/selected fields生成draft，复用current display merge，测试去重/owner/date/includeFocus/同日多篇。
5. A/B/ADMIN/匿名HTTP+Redis+PostgreSQL测试；并发publish/save、响应丢失重放、撤回/下架与private原业务回归。
6. trellis-check修复并记录结果；向storage交接已核实API/表/事务方法，明确下一阶段须同事务加入READY/owner/额度+refs；不提前宣称附件已完成。

验证：父已核实Maven/Java17命令，拟新增`CommunityPublishingIntegrationTest`/`CommunitySourcesIntegrationTest`及现有owner/auth/focus/report套件；后端最终`clean verify`。记录实际命令/exit/schema，不使用H2替代。

失败处理：修复当前实现，不改旧迁移或自动删schema/业务数据；阻塞如实报，普通已解决问题最后汇总。没有授权git commit/push/PR/云部署。
