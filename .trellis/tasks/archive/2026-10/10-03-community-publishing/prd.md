# 站内发布核心与公开作者资料

## 目标

交付本人素材生成私有草稿、三类站内发布的不可变快照、公开作者资料与撤回/下架控制，保持现有账号隔离和专注记录语义。

## 来源与依赖

父任务：`../10-03-community-oss/`。需求/原AC唯一合同见父`prd.md`；输入R3与真实后端证据见父`research/handoff-check.md`、`research/backend.md`。此子任务须最终规划批准；依赖父统一合同，无前置实现任务。storage子任务将附件集成进本任务发布事务，不能独立after-commit绑定。

## 范围

- FR-01/02/03/04/07/08及FR-09后端部分。
- 仅成员发布、本人私有draft、发布历史保留、first_published_at不重置、requestId幂等与version409。
- 日期本人展示素材完整分页、默认不选、明确公开字段、专注另外汇总、同日多篇。
- 公开profile独立存储，默认“未设置昵称”；ADMIN下架理由/actor审计，无恢复。
- 沿CurrentUser/CSRF/ProblemDetail/PageQueries/真实PostgreSQL；旧owner queries不放开。

## 验收

- [ ] 父AC-01～06、08～12的核心HTTP+数据库结果通过；三类内容可发布并只读当前版本。
- [ ] 保存/来源修改不改公开快照，publish重试一次有效，旧version冲突，首次时间固定。
- [ ] A/B/ADMIN越权私有ID404，匿名401，CSRF403，无局部写入；公开profile不含username。
- [ ] 撤回/下架读者下一请求404，下架审计同事务，作者不能重发。
- [ ] 今日素材兼容当前展示合并含义但不添加任何兼容层：合并完成计一次，focus单列，跨日/owner非法拒绝。
- [ ] V17空schema迁移、同owner FK/唯一约束与现有任务/记录/focus/report回归通过；V1～V16不改。

## 非目标/约束

文件流与RustFS由下一子任务；原型UI由UI子任务。无匿名、点赞评论、restore/通知/云操作/兼容迁移/feature flag。本文不授权start/commit/push/部署。

## 规划状态

无产品未决项；父最终规划已由用户后续“开工/批准并连续执行”明确批准，context validate通过，本任务已in_progress。此阶段完成不等于父需求完整交付。
