# 发布核心 Trellis 审查

**父整合闭环（2026-10-03）**：下文V18接入/父附件与UI未验收是本发布阶段的历史依赖。现在已由storage在相同事务接READY/owner/精确额度/refs，完成223全量及真实SDK/Servlet/代理/进程故障门禁；hasUnpublishedChanges/fixture reset同步，父22AC均有真实证据。没有把本报告的196/14相加冒称新全量198；最终依据为父validation与storage最终check，界面见UI真实验证/visual-review。

审查完成时间：2026-10-03 03:43 +08。基线 HEAD `1a7928c2b2fa15ce0d3148af187770d85aca96f7`，分支 `codex/community-oss`；核验包含 tracked diff 和全部 untracked 发布文件。已完整读取父 PRD/design/implement、子三份任务文档、check.jsonl 各研究/规范、storage-handoff；native 长文件从真实路径补齐（backend 研究全文、database 相关完整章节）。父 design 是唯一合同，旧研究候选路径/表名/已保存稿发布/独立附件版本不用于审查。

## Findings (fixed)

- File: `backend/src/test/java/com/aiworkbench/community/CommunityPublishingIntegrationTest.java`
  - Issue: 已有发布 revision 故障回滚测试，但下架审计与 lifecycle 事务在写入失败时没有直接数据库验收证据。
  - Fix: 新增 `hideFailureRollsBackAuditAndKeepsCurrentPublishedContent`，用真实 PostgreSQL trigger 在 HIDDEN 状态写入时注入失败，证明 audit 为零、作者稿件/version/pointer 与读者当前正文均保持原值；测试结束清除本用例 trigger/function。
- File: `backend/src/test/java/com/aiworkbench/community/CommunityHttpIntegrationTest.java`
  - Issue: 草稿保存、发布、撤回的 stale 409 及未知附件引用拒绝路径未直接证明 HTTP ProblemDetail 和全聚合无局部写入；B/ADMIN 私有 publish 404 也缺 HTTP 覆盖。
  - Fix: 新增 `staleWritesAndUnknownAttachmentReferencesDoNotPartiallyMutatePublishedContent`，真实 Cookie/CSRF/Redis/HTTP 检验旧 version 的三种写入 409、未知附件 save/publish 404、B/ADMIN publish 404；拒绝后作者完整 JSON 与 reader JSON 不变、version=1、revision 只有一次。

这两项是测试覆盖补齐，未发现需要修改产品代码的发布核心缺陷。只修改上述两份测试及本审查证据，不改 V17、产品接口、前端、pom、环境或 V18；不回滚其他代理改动。

## Findings (not fixed)

当前子任务没有已确认而未修复的问题，也没有需要用户选择的产品/接口设计问题。

下列是父规划已分配给 V18 的依赖交付，不作为本子缺陷或附件已完成的证据：READY/owner/精确额度与 draft/revision refs 同事务接入、存储适配/流授权/上传 fencing/recover/cleanup；`hasUnpublishedChanges` 须比较附件顺序集合；`CommunityTestData` 与 e2e reset 须同步 V18 表。具体接入点仍以 `storage-handoff.md` 为准。本审查可交接 storage 开工，不能证明父 AC-13～21 或 UI AC-22 已交付。

## 已核实行为与驳回项

| 合同 | 真实代码与验证证据 |
|---|---|
| 分层、身份、无 ADMIN 私有旁路 | 四组 controller/service/interface/impl/mapper；private SQL 绑定 owner；moderation UUID 锁仅由角色检查后的 hide 调用；真实 HTTP B/ADMIN foreign GET/save/withdraw/publish 404、USER hide403、CSRF403、匿名读401 |
| 安全公开数据 | `publicProjection` 只 PUBLISHED + 同 post/owner 的 current revision，source_selection 不选、不序列化；Author/Profile 为独立 records 与独立 profile SQL，缺省昵称不回退 username；HTTP 递归禁止私有字段且作者 logout 后 reader 可读 |
| 公开快照与时间 | draft/revision 分别保存 type/date/title/summary/body/source JSON；只 save 不变公开 JSON，source 修改/删除不阻塞或改快照；post type 保存/发布验证并有同type FK；firstPublishedAt 只首次设，列表排序使用它；publish/saved 时间统一 MICROS，成功首次响应与重放相等 |
| 发布幂等/并发 | owner post FOR UPDATE；规范化完整请求包含 version/MEMBERS/type/date/title/summary/body/首次顺序去重 ids 做 fingerprint；相同 requestId 返回 revision 保存的 result_version；异载409；receipt 不重新应用 pointer/draft/status；同键并发一次版本、不同旧version一胜；三轮 hide/publish 同post锁竞争，HIDDEN不能重发 |
| 原子更新/审计 | publish 方法真实 @Transactional 保存当前预览draft + insert immutable revision + pointer/version；故障 trigger 证明全回滚；hide audit+aggregate 同事务且 actor=CurrentUser，新增故障证明audit与可见性同回滚 |
| 完整日期素材 | `WorkRecordMapper.dailyPresentation` 同一 SQL fragment 用于既有 page 和选中 ids unpaged 查询，raw/report 查询不改；Asia/Shanghai [start,nextStart)；fields白名单与独立includeFocus，重复selection去重，所选raw focus UUID并集；真实跨页、完成结果一次、未选项/notes/时刻排除、owner/date/absorbed ID拒绝、同日多篇与source冻结 |
| 分页与缓存 | 所有新 page 使用 PageQueries，目标 select 后 finally clearPage，DTO转换在作用域外；默认5/受支持大小及真实两页 total 验证。CommunityCacheControlFilter 在 security 前加 no-store,private/nosniff，匿名及404错误也覆盖；无 ETag/304 或历史 reader 入口 |
| DB/Flyway与原业务 | V17 composite sameowner/type/currentpointer FK 和状态约束，source无work_records FK；真实空schema+V16 owned升级保留行/checksums测试；V1～V16 HEAD diff为零；原auth/STOMP/focus/tasks/report回归在现有完整196测试门禁通过 |
| 无阶段空成功旁路 | 非空未知附件 IDs 在 save/publish 404且不写入，正文未选附件 URI400；当前空附件代表 V18 尚未建立资源，不能将文字阶段当附件完成。没有运行时 flag、兼容路由/迁移、用户名fallback、restore、互动领域或公共scope |

已核实的 UUID 映射由现行 `mybatis.type-handlers-package=com.aiworkbench.config.mybatis` 与 `UuidTypeHandler @MappedTypes(UUID.class)` 提供；新 XML constructor声明 UUID 延用该注册，无需重复在每列指定 handler。`lockForModeration` 的非owner查询是显式ADMIN公开下架路径，不构成私有读取旁路。HIDDEN 作者可继续 save 私有文本是父正式合同，不应禁止或新增恢复功能。

## Verification

- Lint: PASS（`git diff --check -- backend` exit0，另按指纹清单逐行检查全部33份文件的尾部空白与重新核验SHA-256，exit0，覆盖untracked）；backend/pom 没有独立 Java linter，未冒称运行不存在的 lint 命令。frontend lint/build 由 UI 子任务负责。
- TypeCheck: PASS（原 `clean verify` Java17 compile/testCompile；本次定向 `test` 再次完成编译及补充 testCompile，exit0）。
- Tests: PASS。核读现有 `.local-runtime/community-publishing-verify-passed.log`：`clean verify` 196 tests / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS，2026-10-03 03:31:30 +08，exit0与实施者validation一致。产品文件本次未改，未无意义重复整套；补测实际定向执行 14 tests（HTTP6、Publishing8），全部0失败/错误/跳过，exit0。没有把两次结果相加冒称新的全量198测试门禁。
- Task validate: PASS，8 implement / 8 check entries，exit0；已知两份长文件注入截断告警通过原路径读取处理。
- 旧迁移: `git diff --exit-code HEAD -- backend/src/main/resources/db/migration` exit0（V17 untracked 已另行全文审查，不被此diff涵盖）。
- 全部33份被检代码/测试文件的当前 SHA-256 与路径保存于 `check-fingerprint.json`，涵盖所有新增发布文件及原有后端变动；前端并行修改不在这个结论内。

本次实际命令（独立 PostgreSQL15432 / Redis16379，BackendTests helper注入 `d9_community_tests_20261003`；无日常5432/6379/public、无模型key、无全局Flyway覆盖）：

```powershell
. 'E:\projects\workbench\.local-runtime\community-oss-import-env.ps1' | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
& 'D:\APPS\apache-maven-3.9.9\bin\mvn.cmd' -s backend/maven-settings-aliyun.xml -f backend/pom.xml '-Dtest=CommunityPublishingIntegrationTest,CommunityHttpIntegrationTest' test
git diff --check -- backend
git diff --exit-code HEAD -- backend/src/main/resources/db/migration
python ./.trellis/scripts/task.py validate .trellis/tasks/10-03-community-publishing
```

定向原始日志在 `C:\Users\kira\AppData\Local\Temp\community-publishing-check-20261003.log`；Surefire XML 在 backend/target/surefire-reports。另只读解析两份XML，确证新增两个具名用例实际被运行且对应suite为6/8条、零失败/错误/跳过；随后指纹与untracked格式复核33份全部通过。没有并行后端/E2E；正常沙箱因 Windows ACL 初始化 error5 失败，只读与已授权隔离测试改用明确 require_escalated，执行成功，非自动审批拒绝。没有修改沙箱/权限或日志环境目录。

## 下一阶段与 spec 同步

可以启动 storage，发布产品文件释放。storage 在相同 PublishingServiceImpl.save/publish 短事务中接 refs、验证与取消pending，不另造发布实现；receipt路径在验证活附件之前重放，仍不得恢复状态或授予当前新 version。V18完成后必须重新完整clean verify及真实RustFS附件全AC，当前包不能证明未来代码。

父主会话按执行计划负责最终 `.trellis/spec/` community publication/attachment 与空间导航合同沉淀；本次没有发现改变已批准合同的新规则，暂不抢写尚待 V18 收敛的统一spec。无需追加兼容处理、默认禁用门禁或用户确认。尚未commit/push/部署/云API，未声明父目标完成。
