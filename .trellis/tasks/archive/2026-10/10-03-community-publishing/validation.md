# 发布核心实际验证

本子任务已实现无附件发布核心，等待父协调的 trellis-check 与 V18 附件接入；父需求未宣告完成。用户“开工”和“批准并连续执行”是当前执行依据。仅开发与验证，没有 commit/push/部署/云 API。

## 被测范围

- V17 社区独立表、同 owner/type/pointer FK、三类型/date/summary/正文草稿与 immutable revisions。
- publishing/community/profile/moderation 四条 Controller → Service interface → impl → Mapper/XML，CurrentUser、Cookie/CSRF、成员公开投影与 no-store。
- 当前预览 payload 原子保存 draft+revision+current pointer；完整规范化 fingerprint、固定 result_version、首发时间保持，receipt 不恢复撤回或下架。
- 指定日完整素材分页、跨页 ID 重查、合并完成/所选 raw focus 去重、字段白名单、生成私有 DAILY、原来源删除不阻塞。
- 原 WorkRecord 日展示 SQL 提取为共享 fragment，raw/report 查询语义不变；MissingServletRequestParameterException 安全 400；仅 d9_e2e 的 reset 增列 V17 表。
- 与 UI 共同固定 typed `AttachmentInfo`，当前空集合仅表示此阶段不存在已上传附件，非空未知 ID 拒绝；同事务接入位置见 `storage-handoff.md`。

## 隔离与命令

基线 HEAD `1a7928c2b2fa15ce0d3148af187770d85aca96f7`，分支 `codex/community-oss`，包含本子后端未提交代码；并行 UI 修改未纳入本后端测试结论。V1～V16 无 diff。

实际 Java 为 `C:\Program Files\Java\jdk-17`；Maven 为 `D:\APPS\apache-maven-3.9.9\bin\mvn.cmd`。同 PowerShell 进程加载 `.local-runtime/community-oss-import-env.ps1` 默认 BackendTests，UTF-8。独立 PostgreSQL 15432 / Redis 16379；数据库 `wbcommunityoss_test`，主 test schema `d9_community_tests_20261003`，实时回归 schema `d9_live_acceptance`。未对日常库/卷进行 reset。

```powershell
. '.local-runtime/community-oss-import-env.ps1' | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
& 'D:\APPS\apache-maven-3.9.9\bin\mvn.cmd' -s backend/maven-settings-aliyun.xml -f backend/pom.xml clean verify
```

最后一次执行显式用当前 WorkbenchWebSocketConfig 默认精确 5173/15173 origins；环境代理随后把该值纳入 BackendTests helper，E2E 模式仍使用自身配置，不改生产允许来源。

## 实际结果

| 验证 | 结果 |
|---|---|
| Maven compile | PASS，exit0 |
| publishing/source/HTTP/migration + 原日展示定向门禁 | PASS，17 tests，exit0（之后补并发与真实 source HTTP） |
| PrivateRealtime 定向真实 STOMP | PASS，7 tests，exit0 |
| **最终 clean verify** | **PASS，196 tests，0 failures / 0 errors / 0 skipped，exit0**，2026-10-03 03:31:30 +08，88秒 |
| 最终新增 CommunityPublishingIntegrationTest | 7 tests PASS，包含3轮 hide/publish 独立事务竞争 |
| 最终新增 CommunitySourcesIntegrationTest | 2 tests PASS，真实合并/分页/来源冻结与删除 |
| 最终新增 CommunityHttpIntegrationTest | 5 tests PASS，真实 HTTP server + Cookie + CSRF + Redis |
| 最终新增 CommunityMigrationIntegrationTest | 2 tests PASS，空schema/V16-owned升级/同owner与type/pointer约束 |
| AuthenticationIntegrationTest / PrivateRealtimeIntegrationTest | 20 / 7 tests PASS |
| task context validate | PASS（8 implement / 8 check entries，已知长文件截断告警已完整从原文件加载） |
| git diff --check；旧迁移 diff | PASS；V1～V16没有修改 |

最终日志：`.local-runtime/community-publishing-verify-passed.log`。Surefire 实际报告在 `backend/target/surefire-reports/`。构建产物 `backend/target/backend-0.0.1-SNAPSHOT.jar` 已生成。随后 storage 改动须重新完整验证，不能沿用该产物或结果证明 V18。

## 已解决问题

1. Java 不可变 List 的 contains(null) 会抛异常，改为显式 stream null 校验；空/重复附件集合规范化仍保留首次出现顺序。
2. PostgreSQL 微秒精度与首次响应纳秒不一致，社区写入统一 MICROS，旧发布重放和首次响应完全一致。
3. 原 Advice 未覆盖 required query 缺失，新增 MissingServletRequestParameterException 安全 ProblemDetail，避免400无detail。
4. 测试环境最初混入 E2E bootstrap、live-acceptance 地址与 WS origins。修正 helper 的 BackendTests/E2E 模式后回归通过。依据根明确授权，仅精确删除本次独立 test schema 的一个自动建立 synthetic bootstrap 行：先查同库/schema、单行匹配、全部业务引用0，再条件删除1行；没有删 schema、其他账号或业务数据。

前两次完整门禁失败分别为环境输入/Origin，记录保留，不冒充当时已通过；最终全量结果是上述实际 PASS。

## 下一阶段与限制

V18 必须真正接入 READY/owner/精确 quota、draft/revision refs、图片/下载授权、上传 fencing/recover/cleanup；更新 `hasUnpublishedChanges` 的附件集合比较，以及 `CommunityTestData` / `E2eMaintenanceServiceImpl` 的 V18 fixture 清理。当前不证明附件、RustFS 私有读写、网页原型、浏览器 E2E 或父全部22 AC。阿里云按用户指令延期，不做 adapter/API/资源。

核心没有新增兼容层、匿名 scope、用户登录名回退、旧 payload 路由、默认禁用门禁、互动或恢复 API。后续核心 review 发现问题可按当前合同修复，不需要重新批准普通实现细节。
