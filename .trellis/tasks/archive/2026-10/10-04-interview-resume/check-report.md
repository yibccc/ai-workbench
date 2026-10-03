# Resume 后端独立审查

范围：Resume Controller/Service/Persistence/Mapper/XML、DTO/rows、V19、共享 Markdown 提取、私有缓存路径及对应测试。未修改 scripts/local、deploy、frontend 或其他代理实现；审批事实按父任务与主会话派发，未以旧 planning 文案推翻已批准范围。

## Findings (fixed)

- Files: `backend/src/main/java/com/aiworkbench/service/impl/ResumePersistenceService.java`、`ResumeServiceImpl.java`、`backend/src/main/resources/mapper/ResumeMapper.xml`
  - Issue: 最初仅在子写前用 Java 时间检查 lease；READY 更新也发生在 current/receipt 子写之前。慢 SQL/trigger 跨越 lease 后仍可能提交 READY/current。本审查首轮遗漏此边界，由主补审指出。
  - Fix: 把 READY 终态 CAS 放在 current 与 receipt 所有子写最后，owner/status/token 条件同时要求 `lease_expires_at > clock_timestamp()`；零行抛 `UPLOAD_EXPIRED` 使整个确认事务回滚，随后 orchestration 在独立事务持久 FAILED/原失败收据，已上传候选可安全清理。新增真实 PG 慢 current trigger 回归，验证旧正文/version、零 READY、失败收据及删除补偿，不改变 V19。
- Files: `backend/src/main/java/com/aiworkbench/config/ResumeJson.java`、`backend/src/main/java/com/aiworkbench/dto/resume/ResumeModels.java`
  - Issue: 默认 Jackson 能把 mode 数字 0/1 当 enum ordinal、小数版本截为 Long，违背精确 JSON 合同。
  - Fix: 仅 Save 的 mode/expectedVersion 使用局部精确 deserializer；mode 必须是正式字符串名，version 必须是 JSON 整数 token。真实 HTTP 回归覆盖 ordinal 0/1、小数 1.5/1.0 与字符串版本，均400且正文/version/收据不变。未修改 engine 或全局 ObjectMapper。
- File: `backend/src/main/java/com/aiworkbench/storage/AttachmentValidationWorker.java`
  - Issue: 共享 Markdown helper 提取后不再使用 CharsetDecoder/CodingErrorAction，charset import 仍为 wildcard。
  - Fix: 收窄为 PNG 分支仍必需的 `StandardCharsets` 精确 import，保留 PDF/图片/MD 既有验证分支。审查初次误删整个 import 导致 compile failure，已根据真实编译指出的 PNG 引用恢复必需类型，随后重新验证。
- File: `backend/src/test/java/com/aiworkbench/resume/ResumeHttpIntegrationTest.java`
  - Issue: 最大 emoji 正文的 HTTP 验证只经过 PUT；导入双 20k 上限由持久化测试证明，尚未经过 Servlet multipart 的最终正文 80,000 字节门禁。
  - Fix: 新增真实 multipart 测试；原件与最终正文各 20,000 emoji / 80,000 UTF-8 bytes 可成功导入并完整读取；原件/最终正文各 +1、原始非法 UTF-8 与 NUL 最终 part 均拒绝，GET current、version 与对象行数不变。上传测试 helper 可传原始最终正文 bytes，以避免 Java UTF-8 编码器先替换错误字符而掩盖传输验证。

## Findings (not fixed)

- 本次范围内未发现需产品决定或模块边界变更的未修业务问题。
- 完整 Maven `clean verify`、RES-AC-07 IAM/proxy、RES-AC-08 联合恢复和 RES-AC-09 最终历史面试由主/ops/engine 继续；这些不属于本审查代理的代码所有权，未把针对后端测试写成全产品验收。
- `.trellis/spec/backend/` 需新增真实简历 API/事务/收据/清理合同，并把 private-attachments 的 IAM 说明同步为 community 与 resume 两个前缀；已告知主会话统一同步，避免多代理改同一规范。当前仅保留审查记录，不重复编辑共享规范。

## 已核对的实际代码路径

- 首次 GET 与 absent snapshot 均不创建 singleton；空正文与 null 分开；delete 清空内容却保留版本槽位，version 单调递增。
- `snapshotForInterview(UUID,long)` 必须在调用者事务内使用，锁 owner account → current，检查 owner 范围内精确 expectedVersion 并返回正文/version/hash。
- PUT 显式 mode，EDIT_CURRENT 保留服务端原件指针，PASTE 解除引用；import 原 bytes/SHA 与最终文本独立，解码剥首 BOM 而最终正文不规范化。
- 收据检查先于旧版本；同 payload 重放原最小结果，不再 put/version；不同 payload 409；历史收据不授予旧正文或原件读取权限。
- reserve/finalize/cleanup 都由独立 Spring bean 短事务执行；put/open/delete 在事务外；owner 谓词、token、有效 lease 与 version 限制提交权。
- READY 为所有确认子写之后的最后终态 CAS；使用数据库实时 `clock_timestamp()`，不依赖事务开始时间或子写前 Java 时间。任一子写跨 deadline 必须整体 rollback。
- 未知写入保留 immutable key/uncertain tombstone，后台每 30 秒恢复；有效预约/current 引用/本实例活动 I/O 不清理；迟到成功在 DELETING/DELETED 窗口重排 DELETE_FAILED，旧 cleanup confirmation 不误报完成。
- original 为当前 owner 与 READY 原件单 JOIN 鉴权后才 open；GET/HEAD/Range/conditions 不给云 URL 或 redirect，私有缓存 filter 先于 Security 生效。
- V19 为新增迁移，V1–V18 未修改；同 owner 复合 FK、MD 原件实际 bytes 上限与正文 CHAR_LENGTH 约束完整。社区验证器仍共享同一文本 grammar，其他格式门禁未改变。

## Verification

- Lint: PASS，`git diff --check` 无空白错误；Java 没有单独配置 linter。
- TypeCheck: PASS，本次 Linux Java17 离线 Maven compile/testCompile 均通过（170 主代码 / 49 测试文件）；精确 StandardCharsets import 修正后无编译错误。
- Tests: PASS，本次 `-Dtest=ResumeHttpIntegrationTest,AttachmentValidationTest test` exit0 / BUILD SUCCESS：HTTP4 + AttachmentValidation7 共11项，0 failures/errors/skipped；已核之前实际 Surefire 报告的 PG11（包含实际 JVM kill/new JVM）与 Text2。合计24项有意义的简历/附件针对用例通过，没有重复 PG11。实际命令沿 `validation.md` 的只读离线缓存、原 settings、既有 Mockito 显式 javaagent 与授权忽略 maven.env，仅替换上述 test 选择。
- 隔离资源：主授权 project `wbinterview20261004`，DB `wbinterview_test` / schema `d9_interview_tests_20261004` 与私有 RustFS fixtures；没有读取日常 env、库、桶或输出 secret。

### 精准补审本轮结果

新的 slow-current lease 回归与严格 JSON HTTP 回归，以及原 import 成功/确认失败/明确mode保存删除三项受影响回归，共5项，**exit0 / BUILD SUCCESS**；真实 PG3 + HTTP2，0 failures/errors/skipped，总耗时1:36，compile189/testCompile54均通过。只复跑相关5项，没有重复此前24项整套。V19 SHA256 仍为 `15e456510b4b6717b95b7366c8c755fa2f2c4df19717c6fa2b465a58809f79dc`，snapshot 接口不变。Maven窗口已释放，由主/engine_check接续最后全量验证。当前本范围没有未修业务问题。
