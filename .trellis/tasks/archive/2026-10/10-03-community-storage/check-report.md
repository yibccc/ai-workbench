# Storage check report

审查快照：2026-10-03 06:37（Asia/Shanghai），`codex/community-oss`，HEAD `1a7928c2b2fa15ce0d3148af187770d85aca96f7`。实际代码包含未跟踪文件，未仅依赖 git diff。本次全范围 366 个后端/前端源文件、测试、运行脚本、配置和规范文件的原字节 SHA256、16 个既有迁移的 Git 规范化 blob、构建与日志指纹见 `check-fingerprint.json`；本次源码集合 SHA256 为 `a7ba468145b899abcd7f2620baa82a5f88ab111cb6b388ea4acb005829051ee9`。

第一阶段按主会话窗口只读产品与测试，随后主会话明确授权本 reviewer 只补指定 AC19 并发测试方法及本报告/指纹。UI 明确释放窗口后只运行这一个方法的 Maven `test`，不 clean/package、不改产品或重包 JAR；无 commit/push。完整 hook/context、子 PRD/design/implement、父三文档及 check.jsonl 所列规范/研究已读。当前父设计覆盖研究历史候选与旧行号。

## Findings (fixed)

- File: `scripts/local/verify-compose.py`，`browser_environment` 与 Node 启动处。
  - Issue: 原 `browser_env = dict(env, ...)` 会将新 RustFS 管理密钥和应用存储密钥交给 `frontend/e2e/compose-restart.mjs` 的 Node 子进程。
  - Fix: 本 reviewer 提交确定证据，脚本 owner 在其独占窗口修复；新增纯环境净化函数，去除 RustFS/storage/AWS/数据库/AI/其他 bootstrap 环境，只重加浏览器验收必需的合成 bootstrap 登录和 `D10_INPUT_ID`。该测试登录值不是对象存储身份。
  - Verification: reviewer 只提取函数 AST，用合成大小写键运行断言：所有这些前缀和四种数据库 URL 被去除、调用方 dict 不变、只保留正常路径与必需合成字段。exit0。没有读取真实进程环境或调用该脚本的容器路径。owner又增加 `browser-env-check.mjs`，在实际 Node 子进程中验证 forbidden key 数量和合成登录字段是否存在，通过后才 import 浏览器验收；只输出数量/布尔，出错封闭。reviewer 的 `node --check` 与最新 Python AST 语法检查 exit0，真实跨 WSL/Windows Node 环境边界现已由最终 `verify-compose.py --skip-build --browser-node /mnt/d/APPS/node/node.exe` 验收证明：实际 Node forbiddenCount=0、合成必需字段齐全，完整命令 exit0。

没有修改任何产品实现、公共接口或迁移。

- File: `backend/src/test/java/com/aiworkbench/community/AttachmentConcurrencyIntegrationTest.java`。
  - Issue: 原50MiB边界是顺序上传；现有并发用例证明个数/同request，缺少实际字节预留竞争的具名证据。
  - Fix: 主会话明确要求后，本 reviewer 补 `concurrentFiftyMiBReservationRejectsNewBytesAndReplaysPendingWithoutAnotherPut`，真实45MiB READY、第4个5MiB在commit reservation后暂停真实put，校验新增1byte的quota409、原request旧versionUPLOADING重放/仅1PUT、放行后下载SHA及四ID50MiB/body/refs/version守恒。
  - Verification: 真 PG/Redis/RustFS 定向方法1PASS，session41311 exit0，原JAR SHA保持63b7df…；不以新增1PASS声称224全量通过。

## Findings (not fixed)

没有尚未修复的确定存储产品、规范或测试缺陷。native、最终代理/JVMkill/Node guard 与新增 AC19 定向方法的真实终态均已闭环。完整父 AC/UI/视觉交付不是本子报告的独立验证范围；指纹收录前端文件用于跨层身份核对，不表示本 reviewer 对全部前端作过功能/布局审查。

后续 runtime 又生成一份 `scripts/local/__pycache__/storage-smoke.cpython-311.pyc`，主会话明确将在本 reviewer 释放后按精确路径清理；未把这份验证产物加入源码指纹。native 专有 schema 和两份被发布/历史引用的对象按证明需要保留，属于受引用的持久化证据，不能作为孤立对象删掉。

## Final specification closure

主会话已同步后端 `community-publication.md`、`private-attachments.md`、index、Linux/local delivery 与前端 `community-publishing.md`、index、directory/dialogs。reviewer 按实际源码核对附件 DTO/API、精确大小/空 MD/历史额度、post→attachment锁、既有发布事务原子 refs、durable receipt/deadline/fencing、清理tombstone、RR读取、SDK/pathstyle/rawSHA256、stream/headers/私有IAM、子进程环境及 Blob/未知上传重放/逐ID维护反馈。

发现并由主会话修正一处文档措辞：原 private-attachments 声称失败上传原 token/time 始终保留；实际 `claimDelete` 为删除操作轮换 token/deadline。现规范正确区分 immutable key/creation time、original upload receipt 与当前 operation token/deadline。reviewer 未改产品或他人规范。

新的 private-attachments 规范承载存储配置与权限，runtime-integration 的现行 backend/postgres/redis/deepseek 状态键仍与源码一致；不要求新增 RustFS status 组件。各相关 index 与相对链接有效。之前“四服务/旧 env”和规范同步待办已闭环。

## Actual code-path review

| 合同 | 当前代码证据与结论 |
|---|---|
| 身份与 multipart 顺序 | `ApiSecurity` 与 `ApiSessionFilter` 使用既有会话/CSRF；`resolve-lazily=true`，`AttachmentController.upload` 不绑定 MultipartFile/请求字段参数，先 `authorizeUpload(postId)` 后 `request.getParts()`，stage/worker/SDK 更晚。明确区分 Servlet 有界接收与应用验证暂存，不声称 owner 在 TCP/所有 Servlet 缓冲之前。 |
| 精确字节与集合 | `AttachmentValidator.stage` 流式计实际字节、SHA256，5,242,880/20,971,520/1,048,576 等号可过、+1 立即413；空 MD 可过，图片/PDF 要非空。post 锁下 draft unique refs（含 pending）总数10/52,428,800；发布输入去重保首次顺序。V18 也限制真实单文件类型/大小。 |
| 幂等与版本 | reserve 先锁 owner post、查同 post/requestId，再核验旧 expectedVersion；匹配真实 sha/size/安全 filename/type。重放不 put、不再次占额。FAILED 重放保原 code/resultVersion，重试新 requestId/id/key；旧行/key 不覆盖。发布 fingerprint 是规范化完整提交，receipt 固定当次 resultVersion/时间，不把撤回/下架变回发布。 |
| 短事务及晚到 put | reserve/finish/fail/recover/claimCleanup/finishCleanup 均 post→按 ID 锁附件。外部 put/delete 不在 DB 长事务。finish 要 UPLOADING/token/有效 deadline/仍 draft 引用；save 移除 pending 同事务 FAILED，迟到 finish 不复活引用/READY。确认 DB 错误 best-effort fail，不能伪造 READY；DB仍不可用时持久 key 保留供 recover。 |
| cleanup 与失败收据 | 检查 draft 和全部 retained revisions，跳过 UPLOADING、有效 FAILED/DELETING lease。DELETING fence 禁止绑定，删除态保 tombstone/key，可再 delete 晚到对象；delete completion 要当次 token，不能盖新 claim。DELETE_FAILED DTO 展示当前安全存储错误，数据库原失败 upload code 不被 cleanup 擦除。 |
| 保存/发布原子性 | 同一既有 PublishingServiceImpl 事务内锁 post、检所选附件同 owner/post/READY/quota/body refs、替换 draft、写 immutable revision refs、切 pointer/version/first date；没有 after-commit 补绑、副本事务或隐藏门禁。HIDDEN 不可发布。历史 ref 保留且不算当前额度。 |
| coherent DTO | `CommunityServiceImpl` 与作者 `PublishingServiceImpl.get` REPEATABLE_READ，从一个快照读取 body/pointer/ref/attachment metadata；不以 READ_COMMITTED 多条查询拼旧 body 与新 refs。owner 盘/当前发布 DTO 各按实际合同，不暴露 key/bucket/endpoint/username。 |
| 读取与流关闭 | `download` 先 CurrentUser、精确 owner+draft/history 或 PUBLISHED+currentRevision+READY 查询，后 storage.open。GET/HEAD 同方法；no-store/private/nosniff，无 ETag/304/206/云重定向；Range/条件头忽略后授权 full200。PDF/MD attachment、图片 inline，安全 filename 编码。同步16KiB copy，全生命周期关闭/abort SDK stream，size 不符在提交元数据前503。 |
| 存储统一合同 | 只有 ObjectStorage/RustFs adapter，业务表稳定 key/size/sha，无供应商 URL。固定 AWS2.55.10 + Apache5、path-style、nonchunked、已知长度文件 body；SHA256 是 hex 解为原32 bytes再Base64。有限 SDK connect/read/attempt/total 时间小于持久 deadline，不另建 fallback。 |
| 真实解析与资源边界 | 两个可终止 child parser slot，heap/direct/metaspace预算和60s父截止；force kill 后 wait。独立新 scratch目录、不跟链接精确 finally 清理，worker环境去 DB/AI/storage/RustFS/AWS/bootstrap。fatJar走 PropertiesLauncher，flat Surefire 走真实 worker。JPEG/PNG sampled decode，PNG遍历全chunk CRC；WebP全RIFF/frame/encoding头与安全帧decode；PDF strict file-backed xref/outer/unencrypted page tree；UTF8 REPORT/BOM/空MD，无转换/模型调用。 |
| 运行配置与初始化 | native loopback、compose service DNS；管理身份只进入 RustFS/显式初始化，后端只应用身份；前端和 validator剥 storage/root。initializer明确本地 endpoint/bucket，authoritative policy list，不把500当不存在；冲突policy/user不覆盖密码；私有bucket和minimal prefix实测、显式admin Deny；原PG/Redis卷不改。Nginx仅单上传path22m，Servlet file21MiB/request22MiB，其余1m。E2E/schema-owner guard与fixture先删refs同步V18。 |

解析证明范围如实现与批准设计所述：高压缩/需完整 raster 的 WebP 分支检查外层和编码头，不能证明全部压缩 bitstream；加密 PDF 仅可观察外壳，不要求密码/证书，不认证隐藏内容或病毒。没有把这些事实重新解释为像素/动画/加密 subtype 禁止政策。

## Verification

- Lint: PASS，reviewer 运行 `git -c core.safecrlf=false diff --check`；三 Python 脚本 AST syntax PASS，无 py_compile 缓存写入。项目没有独立 Java linter；不能把这两项称为 frontend lint。
- TypeCheck: PASS（既有产品编译与新增定向测试实际testCompile），`.local-runtime/community-storage-final-verify.log` 包含 Java17 `clean verify` 编译、testCompile 和 Boot repackage 成功。第一阶段没有重复Maven；后来UI明确释放窗口后仅运行新增具名方法的test，当前所有测试源码实际编译通过，原JAR没有重包。
- Tests: PASS（核验真实后端结果），上述最终日志行961/971/974：223 tests，0 failure/error/skipped，BUILD SUCCESS，2026-10-03T05:14:30+08:00。对应最新源码另有18定向通过，已读实际测试，不将原220中间结果当最终门禁。
- New browser-env fix: PASS，纯 AST 提取+合成大小写前缀、DB URL、caller unchanged 断言 exit0；无需重跑所有后端。
- Migrations: V1～V16 当前 Git 规范化 blob 与 HEAD 全同；V17 为 publishing 所有、本 reviewer 未修改。V18 composite owner/post/revision FK 与 retained history RESTRICT 有真实 PG 测试；不回改已应用迁移。
- Docker source: `.local-runtime/community-storage-docker-current-source.json` exit0，before=after=`958199ef3cb13af6345332dce748df8c72cd2fb494dc1b17f7fdb9330374e2b0`，image=`sha256:4de0448e3b1cd28ed082c81628faa4b35589ce0ac8da2093badb0f728004c4de`，image source label相同；此前 reviewer 逐一比较245个 sourceFiles 差异0；现在唯一差异是新增并发测试方法所在 testfile，所有产品源/资源/pom/Dockerfile未变。旧镜像不声称包含新增测试源码，原实际运行产品验证仍有效。旧 `image-source-audit.json` 的不一致是已替换镜像证据，不能用于当前镜像否决/通过。
- Actual proxy: latest-source日志有2×20MiB PDF +2×5MiB PNG（不同ID）原字节 SHA/length、50MiB+1→409、20/5MiB+1→413、私有headers及真实 packaged worker通过。
- Actual JVMkill: 同日志有RustFS pause后UPLOADING已提交→KILL实际backend→重建新JVM→Redis会话保留→显式注入已过期deadline→recover version2/正文保留/refs释放→cleanup tombstone/key保留的通过证据；不能把 service 直接 reserve 或单worker启动算为本项。
- Runtime terminal: 最终实际命令 `verify-compose.py --skip-build --browser-node /mnt/d/APPS/node/node.exe`，主会话/运行 owner 直接核验 handle56407 exit0；reviewer 读取 final-browser完整日志与最新 validation，重复代理/真实JVMkill/同browser restart/STOMP/HTTP fallback/account生命周期全部通过。reviewer又只读查询 d10 项目标签下容器/卷/网络，三命令均exit0且空，确认精确清理。native Windows 完整门禁也已由真实 default-profile JAR/Vite 验证闭环，见下面终态；不将本子门禁扩张为父UI/视觉任务完整交付。

已读测试覆盖 owner404/anonymous401/CSRF403、GET/HEAD/Range/conditional、current/F1-F2/history/withdraw/hide、真实白名单与伪装/UTF8/PNGCRC/WebP动画与高压缩/PublicKeyPDF、精确单文件/总量、10 distinct附件与同request并发重放、延迟put移除/cleanup、DB READY失败、revision subwrite回滚、DELETING绑定fence与stale completion、真实MyBatis/PG RR一致快照、Java SDK checksum/匿名403。原50MiB测试是连续不同ID边界，不将同requestId并发个数用例宣称为独立字节并发场景；新增具名场景见下节，现已有独立定向1PASS终态。

阿里云 adapter/API/资源/复制工具按批准范围 DEFERRED；没有云已验收或仅换 endpoint 自动搬数据的声明。

最终闭环指纹涵盖366文件；新增变动只在具名 AC19 testfile，backend产品源/资源不变。223全量和18定向是既有产品门禁，新方法独立记录，不预填224全量PASS，不clean/package/覆盖已验收JAR。

## AC19 concurrent-byte test addition (PASS)

最小验证差距是此前真实字节边界为顺序上传，并发场景仅涵盖ID个数/同request。行为实际位于 post锁下 reserve/quota/receipt；不修改产品逻辑。仅修改 `AttachmentConcurrencyIntegrationTest.java` 加一个具名方法，并更新本任务check记录。

`concurrentFiftyMiBReservationRejectsNewBytesAndReplaysPendingWithoutAnotherPut` 使用真实PNG/PDF fixtures：2×20MiBPDF+1×5MiBPNG已经READY（45MiB），第4个5MiBPNG持久UPLOADING提交后在ObjectStorage真实put入口同步暂停，且无DB事务。主线程以最新version上传1-byte有效MD必须ATTACHMENT_QUOTA、无新增row；原request旧version重放同ID/UPLOADING/resultVersion，不再put。拒绝与重放后整个owner DTO不变。放行spy的callRealMethod实际RustFS PUT，确认READY下载原长度/SHA、DB四distinctID合计50MiB、正文/savedAt/refs/version不变。finally解除latch、shutdown/await并reset spy，随后既有fixture清理仅本用例随机owner/key。

开发前已读技能、共享指南、testprofile/schema guard；test URL和WORKBENCH_TEST_SCHEMA须指同一专用 `d9_community_storage_tests_20261003`，使用隔离PG15432/Redis16379/RustFS19000。已运行diff --check exit0。UI视觉session52767 exit0且真实18080/15173无listener后明确释放PG15432/Redis16379/target，reviewer实际只运行 `-Dtest=AttachmentConcurrencyIntegrationTest#concurrentFiftyMiBReservationRejectsNewBytesAndReplaysPendingWithoutAnotherPut test`。

实际命令：Java17/Maven3.9.9，经既有忽略 env helper，显式 guard专用schema、隔离PG/Redis/RustFS目标以及空model/bootstrap。`mvn -s ./backend/maven-settings-aliyun.xml -f ./backend/pom.xml -Dtest=AttachmentConcurrencyIntegrationTest#concurrentFiftyMiBReservationRejectsNewBytesAndReplaysPendingWithoutAnotherPut test`，session41311 **exit0 / 1PASS / 0 failure/error/skipped**，2026-10-03T06:30:29+08:00。已直接读取Surefire XML确认仅该具名方法被执行，method3.722s/class12.39s。日志 `.local-runtime/community-storage-check-ac19-byte-concurrency.log` SHA256=`bfe323a0c89d0f65e457016bad7e60cd37a4523887ec11a6c0ab1df1b52afd25`。原打包JAR前后SHA同为 `63b7df371897bd1cede8bc1c11169655f914f4854c70cff5ef2cac55b0d0c74a`；没有clean/package。原223全量、18定向与新增1定向分列，不冒称新的224全量。

## Native terminal closure (PASS)

已读权威 `.local-runtime/community-oss-delivery-native-state.json` 与 notes、attempt2 worker观察，并引用父 `research/runtime-delivery.md`。真实执行现成 `start.ps1 -EnvFile`，default-profile packaged JAR8080 + Vite5173→WSL Docker正常私有RustFS9000/受限app，专有 `d9_native_storage_tests_20261003`/PG15432、Redis16379独立namespace；不用正常旧DB/model配置，无模型调用。完成helper session30223 exit0；现成stop最终exit0、8080/5173与ownedPID文件为空。

MOMENT/BLOG真实创建/发布，128-bytePNG inline/38-byteMD attachment通过Boot内嵌worker READY；重启新JVM仍用同posts/revisions/session/objectkeys，前后原SHA/length和no-store/private/nosniff/none/noredirect相同。匿名401、伪造CSRF403。实际OS进程环境观察：两轮backend管理变量各0、app storage各7，两轮Vite敏感变量各0；attempt2中两validator子进程实际观察敏感匹配各0（PID来自该轮，未冒充最终重启轮）。正常 `.env` 前后原字节SHA相同、原PG/Redis/RustFS身份相同、JAR同63b7df…未改变。

native保留受引用测试schema/对象供持久化证据，不扫描/删除日常桶其他对象。首次PIPE EOF等待与第二轮header大小写误判仅修忽略的观察驱动，最终连续读取同一已发布对象成功；没有清表、修改产品或改变存储权限来规避验收。
