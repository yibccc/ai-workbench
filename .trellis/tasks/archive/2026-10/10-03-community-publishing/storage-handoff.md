# 发布核心 → 附件事务交接

## 当前实现边界

V17 为独立发布核心，所有路径遵循父 design。当前只交付无附件流程；非空 attachmentIds 返回安全 404，未选附件 URI 返回 400，不透传、不返回伪造成功。附件完整功能必须由 V18 集成后再验收，当前阶段不证明父 AC-13～21。

类型以 `PublishingModels` / `CommunityModels` / `CommunityRows` 的 public records 为准，DAO 是 `CommunityPostMapper` / `CommunityProfileMapper`。UI 已核对这组类型。

## 同一事务接入位置

- `PublishingServiceImpl.save` 已在 `posts.lockOwned(owner,postId)` 后检查 version/type；以请求去重且保留首次顺序的 attachmentIds 替换 draft refs、检查内部 URI 属于所选合法附件。所有 quota/READY/owner 检查与 pending 取消放在这里的数据库事务，不能先写正文后外部事务失败。该命令最终只递增一次 posts.version。
- `PublishingServiceImpl.publish` 先锁同 owner post，规范化完整 payload，查 `findPublishResult`，相同 receipt 直接返回当次 result_version（不重新引用/写草稿/恢复状态）。无 receipt 才检查 version/type/HIDDEN、附件；在 `writeDraft`、`insertRevision` 与 `updateAggregate` 同事务内保存 draft refs 和不可变 revision refs。refs 写入必须先于 current pointer 提交。
- 公开附件元数据接入 `CommunityServiceImpl.card/detail`；仅匹配当前 PUBLISHED pointer，禁止历史 URI/云 URL。作者 `PublishingServiceImpl.get` 的 `draft.attachmentIds` / `draft.attachments` 表示 draft 集合，顶层 `attachments` 是本人附件托盘（含待处理/失败状态），`currentPublished.attachments` 用 retained revision refs。
- `CommunityPostMapper.findOwnedPage` 的 `hasUnpublishedChanges` 当前比较公开文本/date/summary；V18 必须再比较 draft/current revision 的附件 ID 集合与位置，避免文件变化没有未发布提示。

## 可复用的 typed DAO

`CommunityPostMapper.lockOwned(UUID ownerId,UUID postId)` 返回 `Optional<CommunityRows.Post>`，提供不可变 id/owner/type/status/version/currentRevisionId/firstPublishedAt。所有上传 reserve/finalize/release/recover/cleanup 用这个 post 锁，再按稳定顺序锁附件；网络 I/O 在事务外。

`updateAggregate(ownerId,postId,version,status,revisionId,firstPublishedAt,updatedAt)` 是条件版本更新；storage 在保留 status/current pointer/firstPublishedAt 时使用这一方法递增 aggregate version。`findDraft` 读取私有正文，不需要给 storage Controller 暴露。不能用 `PublishingService.get` 再调用 `save` 假装原子操作。

## 共同 API 类型

`CommunityModels.AttachmentInfo = {id,kind,fileName,contentType,size,state,safeFailureCode}`；kind 是 IMAGE/PDF/MD，state 是父 design 六态。公开投影仅 READY，隐藏存储 key/provider/云凭据。上传响应使用 `{attachment:AttachmentInfo,version}`，失败 ProblemDetail 可携 currentVersion 供 UI refetch。正文写请求是 bodyMarkdown，publish 指定 MEMBERS。

`POST /api/admin/community/posts/{id}/hide` 返回 `{postId,status:HIDDEN}`，不返回作者私有 aggregate version；请求使用 expectedRevisionId+reason。下架锁与发布锁是同一 posts 行。

## 测试与辅助入口必须同步

- `CommunityTestData.remove` 只清本用例随机 synthetic owner。V18 后须先删除该 owner 的 draft/revision refs 与附件表，再清 revisions/posts/accounts，以免新 FK 使核心回归清理失败；不进行外部对象删除或共享桶 reset。
- `E2eMaintenanceServiceImpl` 的 reset 仅在 e2e、非 live-acceptance profile 且 current_schema=d9_e2e 才存在/运行。已列 V17 五表，V18 后加入附件/ref 表的 schema-qualified multi-table TRUNCATE。原 guard 仍有效；此入口不 reset user_accounts。
- migration 测试中 latest 升级会包含 V18；same-owner/V17 约束测试明确 target17，不需要倒改 V17。
- 核心时间写入使用 PostgreSQL 微秒精度，首次响应与成功重放完全相同。上传 resultVersion/时间亦应返回持久化结果，不能拿 current aggregate 授予旧请求写授权。

## 环境

Java17 与指定 Maven，默认 dot-source `.local-runtime/community-oss-import-env.ps1` 为 BackendTests（清 bootstrap，设置 TEST_DATABASE_URL 与 LIVE_ACCEPTANCE_DATABASE_URL），15432/16379 是本次独立服务。E2E 应显式 `-Mode E2E`。不要并行 Maven clean 或与 shared Redis suite 同时跑 E2E。阿里云 API 不在本轮。
