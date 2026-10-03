# 发布核心设计

共享权威：`../10-03-community-oss/design.md`的数据、状态、API、版本/幂等事务与素材合同；本任务不重新命名或改产品行为。

所有权：`V17__community_publishing.sql`（拟新增）、新增`CommunityController/PublishingController/PublicProfileController/CommunityModerationController`，对应`service`接口/`impl`、`mapper`及XML、`dto/community`/`dto/publishing`、`entity/community`，publishing集成测试。按实际既有目录命名，不建立重复owner/auth工具。

引用研究：父`research/backend.md`。private CurrentUser SQL与public current-revision projection分开；保留WorkRecordMapper现行display merge；Asia/Shanghai `[start,nextStart)`不改raw report/focus ledger。publish短事务锁post、requestId结果+fingerprint、检查version/状态、插不可变revision、切pointer；V18阶段由storage任务同一事务加附件验证和引用，不建立外部存储调用的长事务。

默认profile昵称“未设置昵称”，昵称/简介本人编辑含version，读者DTO从独立profile表查。原AccountResponse不做公开作者数据源。公共正文metadata no-store，匿名/owner/CSRF拒绝沿现行机制。

迁移仅新表/FK/索引/状态约束，不把原私有记录数据迁移进posts，不设置“一天一篇”。本任务未涵盖文件流实现；storage依赖已建posts/revisions并拥有V18和附件集成改动，按序执行避免覆盖。
