# Resume backend 实现与验证

本文件只记录 resume 后端代理的实际证据。ops、Nginx、联合恢复、浏览器和面试历史整合由相应代理另记；没有将计划或接口存在标成验收 PASS。

## 实现合同

- V19 增加 `user_resumes`、`resume_objects`、`resume_write_receipts`；V1–V18 未修改。当前空正文与无 current 分开，删除保留 singleton 且单调 version。
- `ResumeController` 的 GET/PUT/DELETE/import/original/recover/cleanup 使用 `/api/me/resume`；当前 owner 由服务端取得。PUT mode 必传，DELETE 的 expectedVersion/requestId 使用 query。导入恰好四 parts，原件仅 MD，实际字节 gate 沿 AttachmentValidator；原解码及最终正文各独立 20k 码点。
- `ResumePersistenceService.snapshotForInterview(UUID owner,long expectedVersion)` 使用调用者的短事务（MANDATORY），锁账号行及 current，再返回 `Snapshot(exists,version,markdownText,contentSha256)`；HTTP GET 纯读不初始化 slot。
- 共享 `MarkdownText` 复用现 Markdown grammar；原件解码仅剥首 BOM，JSON/multipart 最终正文不规范化。原 worker 继续复用该 helper，其他格式路径未改变。
- 预约和收据先于旧版本检查；确认必须满足 owner/token/lease/version。外部 put/delete/open 不占持久事务，成功写只替换一次，收据不持正文/供应商 URL。
- 正常确认的 put 结束后释放活动 I/O；失败或超时保持 `io_uncertain` 与原 deadline。过期恢复撤销绑定权，未知 tombstone 保 key 重复补偿。迟到 put 在 DELETING/DELETED 时重排 DELETE_FAILED，旧 cleanup/token 不能误提交。后台每30秒持续扫描，不按天删除 current。

## 已实际运行

1. Windows Java17 `mvn -o -DskipTests compile`：exit0，BUILD SUCCESS。
2. 全新 PG17.6 `wbinterview_test`、schema `d9_interview_tests_20261004` 上真实 Flyway V1→V19：PASS（19 migrations），后续 validate checksum PASS。连接来自主授权 project `wbinterview20261004`；没有使用 public/日常库。
3. Windows 针对测试：初次默认 Surefire JVM 因 pagefile/native allocation 无法启动；限制 heap 后仍在 Mockito attach/validator 子 JVM 遇资源失败。2项 ResumeTextTest 实际 PASS；其余资源失败没有算业务通过。
4. 缓存 `maven:3.9.9-eclipse-temurin-17` 容器 compile：exit0。最初挂 C:/Users/kira/.m2 导致离线 Surefire POM/deps 不完整，已核真实 Maven 缓存为 D:/APPS/apache-maven-3.9.9/repository 并只读挂载，复用原 settings 仓库 ID，参数 `-Dmaven.repo.local` 整体引用；未安装/下载依赖。
5. 修正缓存后 Linux test 到达真实 PG V19 validated，随后运行被中断，源三个隔离节点同时 Exited(255)。之后仅 exact start 源三容器恢复原卷；没有重建/删库/清桶/删卷。外部停止原因没有据此推断。
6. 最新 Linux 运行 22 项：`ResumeTextTest` 2 项与原 `AttachmentValidationTest` 7 项全部 PASS；简历 HTTP/PG 13 项未能加载上下文，真实原因 `UnknownHostException: postgres`。随后只读 `docker ps -a` 证明 source 三节点均 Exited(0)、网络 endpoint/IP 为空，已经告知主协调恢复；没有将这些连接错误算作业务通过。
7. 新增 absent snapshot 测试的 AssertJ 泛型推断引起一次 testCompile failure，改为显式 `Snapshot` 局部变量后 testCompile PASS。之后同一持续 exec 内 exact start→强核三节点 Running=true/Health=healthy→Maven，真实 PG/Redis/RustFS/Servlet 全部启动。
8. Linux Mockito 动态 self attach 无法建立 `/proc/55/root/tmp/.java_pid55`，因此仅测试启动改为已缓存 `mockito-core-5.17.0.jar` 的 `Premain-Class` 显式 `-javaagent`；没有变更依赖、生产配置或 MockMaker。
9. **最终简历后端门禁 PASS**：下面实际命令针对 ResumePersistenceIntegrationTest + ResumeHttpIntegrationTest，exit0 BUILD SUCCESS；14 tests / 0 failures / 0 errors。HTTP3项29.45秒；真实 PG11项60.83秒，包含实际 JVM 强制退出与全新 JVM 恢复。结合之前已通过的 Text2 + AttachmentValidation7，共23项有意义的针对测试 PASS。完整 Maven 门禁由主在 engine 集成后统一执行。

## 当前针对验证命令

读取 `.local-runtime/ai-interview-test/test.env` 到忽略的 `maven.env` 时过滤 RUSTFS 管理凭据与模型 key，不打印 secret。当前执行为：

```powershell
wsl.exe --exec docker run --rm --network wbinterview20261004_default --env-file /mnt/e/projects/workbench/.local-runtime/ai-interview-test/maven.env -e 'TEST_DATABASE_URL=jdbc:postgresql://postgres:5432/wbinterview_test?currentSchema=d9_interview_tests_20261004' -e REDIS_HOST=redis -e REDIS_PORT=6379 -e WORKBENCH_STORAGE_ENDPOINT=http://rustfs:9000 -e DEEPSEEK_API_KEY= -e 'MAVEN_OPTS=-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC' -v /mnt/e/projects/workbench:/workspace -v /mnt/d/APPS/apache-maven-3.9.9/repository:/root/.m2/repository:ro -v /mnt/d/APPS/apache-maven-3.9.9/conf/settings.xml:/tmp/maven-existing-settings.xml:ro -w /workspace/backend maven:3.9.9-eclipse-temurin-17 mvn -o -s /tmp/maven-existing-settings.xml '-Dmaven.repo.local=/root/.m2/repository' '-DargLine=-Xms32m -Xmx384m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -javaagent:/root/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' '-Dtest=ResumePersistenceIntegrationTest,ResumeHttpIntegrationTest' test
```

实际结果见上第9项。覆盖真实 HTTP A/B/ADMIN/匿名/CSRF 与缓存/原 bytes、双 gate/空 MD、PG 首写竞争/同时 import CAS、收据、删除 ABA、put/确认失败、未知 late-put、删除确认窗口、活动 I/O、delete retry，以及实际杀掉已提交 UPLOADING 的 JVM 后启动新 JVM 恢复。Receipt replay 验证了重复同 payload 的原最小结果与真实 GET current，不把旧 receipt 当旧正文/对象读取授权。

| 对应验收 | 本代理可证明范围 |
|---|---|
| RES-AC-01 | 真实 HTTP owner/ADMIN/匿名/CSRF、私有缓存与 GET/HEAD 原件，无 key/供应商 URL/redirect |
| RES-AC-02/03 | MD-only、原 bytes/SHA/BOM、独立最终文本、EDIT_CURRENT/PASTE、空 MD/null 区别、20k BMP/emoji 等号/+1、1MiB stage 等号/+1 与完整 import 的组合拒绝 |
| RES-AC-04/05 | 首写及两个 import 的独立 PG 事务竞争，receipt prior-before-version、删除单调版本和迟到请求不能 ABA，已复制 Snapshot 不变 |
| RES-AC-06 | put/DB 触发器确认失败、活动 I/O 保护、真实 kill/new JVM、expired token、unknown tombstone、DELETING 确认窗口、delete fail/retry；当前正文/版本不受失败候选影响 |
| RES-AC-09 | owner+expectedVersion 锁定快照及 precise hash，旧版本拒绝、无 slot snapshot 不建行；最终面试历史由 engine 接续 |

不把这里的函数/HTTP验收扩充为浏览器通过，或声称已验证恢复 bundle/proxy、已完成旧备份删除、云服务或付费模型质量。

完整 `clean verify` 由主在无并行 Maven 时统一执行；RES-AC-07/08 ops/proxy/restore 及 RES-AC-09 engine 的最终历史场景另外接续。没有调用云 API 或真实付费模型。

## 独立检查补充（2026-10-04）

- `trellis-check` 独立审阅全部 Resume Controller/Service/Persistence/Mapper/XML、DTO/rows、V19、共享 Markdown/cache 与实际测试路径；没有遗留的本范围业务缺陷，详见 `check-report.md`。主继续统一规范同步和完整验收。
- 新增 `ResumeHttpIntegrationTest.multipartAllowsExactEmojiBoundaryAndRejectsEitherOverLimitWithoutReplacingCurrent`：真实 Servlet multipart 中原件与最终正文各20,000 emoji / 80,000 UTF-8 bytes 同时导入成功，读取原件与正文完整；两个来源的20,001分别400，最终 part 原始非法 UTF-8/NUL分别400，正文/version/对象行数不变。此测试补齐原 HTTP 最大 emoji 只经过 PUT 的缺口。
- worker charset import 收窄为 PNG 实际使用的 `StandardCharsets`。第一次误删全部 charset import 被 Maven 编译指出，已恢复必需类型并复跑，未把该次失败当成功。
- 主已核源三个隔离节点 Running=true/Health=healthy；检查代理未启动/停止源服务。复用上方同一真实 Linux Maven 入口，把测试参数改为 `'-Dtest=ResumeHttpIntegrationTest,AttachmentValidationTest'`。
- **本次 exit0 BUILD SUCCESS**：compile/testCompile通过（170主代码 / 49测试文件），HTTP4 / AttachmentValidation7共11 tests，0 failures/errors/skipped，总耗时1:33；结合此前 PG11 + Text2 共24项针对用例实际通过。没有在本轮重复已通过的PG11；没有将targeted测试当完整 `clean verify` 或RES-AC-07/08/最终面试历史通过。
- Maven窗口已经释放给engine实现代理；全量验证仍由主协调。

## 精准补审：最终 wall-clock fence 与精确 JSON token

主补审指出首轮审阅遗漏的两处实际边界，均在 resume 范围内修复，没有修改 engine、ops、frontend 或全局 ObjectMapper：

1. `ResumePersistenceService.finish` 原本在子写之前用 Java 时间校验 lease 且先更新 READY，慢 current/receipt 写入可能跨 deadline 后仍提交。现在先完成 current 与 receipt 子写，最后 READY CAS 同时要求 owner/status/token 与 `lease_expires_at > clock_timestamp()`；零行抛 `UPLOAD_EXPIRED` 回滚全部子写。`ResumeServiceImpl` 在新事务记录 FAILED/固定失败收据，已确认上传的候选随后可补偿删除。
2. Save mode/expectedVersion 通过局部 `ResumeJson.ExactMode/ExactVersion` 精确反序列化，拒绝数字 enum ordinal、小数或字符串版本；仅接受正式 mode 字符串与整数版本，不给旧 API 加全局规则。

新真实 PG 测试 `leaseExpiresDuringCurrentWriteLastDatabaseFenceRollsBackAndRetainsCleanupReceipt` 的 BEFORE current UPDATE trigger 在写入中将候选 lease 改为实时钟 +150ms 并 `pg_sleep(0.3)`，保证前置检查已经通过而写入跨 deadline。导入返回 `UPLOAD_EXPIRED`，原正文/version保留、没有 READY 新原件，失败收据/resultVersion固定，候选可删除且同ID重放不多put。新 HTTP 测试 `numericModeAndNonIntegerVersionAreRejectedWithoutCoercionOrCurrentChange` 实际发送 mode=0/1、version=1.5/1.0/"1"，均400且正文/version/receipt行数不变。

使用上方同一已核在线隔离 source、只读离线缓存/settings、既有Mockito显式javaagent入口，只替换 Maven 测试选择为：

```text
-Dtest=ResumePersistenceIntegrationTest#leaseExpiresDuringCurrentWriteLastDatabaseFenceRollsBackAndRetainsCleanupReceipt+importKeepsExactBytesIndependentFinalTextEditPreservesAndPasteDetachesWithoutPut+putAndDatabaseConfirmationFailuresRetainOldCurrentDurableKeyAndImmutableFailedReceipt,ResumeHttpIntegrationTest#numericModeAndNonIntegerVersionAreRejectedWithoutCoercionOrCurrentChange+emptyMdAndEmojiTransportCurrentEditPasteReplayAndDeleteUseAuthoritativeGet
```

**实际 exit0 / BUILD SUCCESS**：compile189/testCompile54，PG3+HTTP2共5 tests / 0 failures/errors/skipped，1:36；Flyway validate正常，V19 SHA256保持 `15e456510b4b6717b95b7366c8c755fa2f2c4df19717c6fa2b465a58809f79dc`。没有重复此前24项全套；没有宣称完整 clean verify 已通过。Maven窗口已释放给主/engine_check。
