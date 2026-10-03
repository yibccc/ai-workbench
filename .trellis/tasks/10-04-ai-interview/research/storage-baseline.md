# Research: 存储基线、简历原件与复用边界

- Query: 核对真实 ObjectStorage/RustFS/config/IAM/文件验证/备份恢复，确定本地简历原件合同及未来不改业务代码切换阿里云的边界；检查原型和外部面试源码的实际可用性。
- Scope: mixed（当前仓库、交接包、归档任务、本机参考源码；阿里云和 GNU 官方文档）
- Date: 2026-10-04（Asia/Shanghai）
- Task: `.trellis/tasks/10-04-ai-interview`

## Findings

### 1. 当前基线与交接包差异

交接包使用 `ai-workbench@cf6ca832bdfffe13628489a99e80bbd3bbe9940a`，主会话提供本地基线 `0090d0127b0693c780aa3e587e20a6e6bae969fc`。研究角色没有执行任何 git 操作；两者的实际 commit 比较由主会话负责。以下结论来自本次直接读取的当前工作树，不将网页快照或归档 PASS 当作当前测试结果。

- 当前只有 `RustFsObjectStorage` 一个 `ObjectStorage` 实现；没有 `AliyunObjectStorage`、OSS SDK 依赖或可切换的云 provider。`StorageProperties.java:12` 只接受 `provider=rustfs`，`:15` 强制 `pathStyle=true`。`RustFsObjectStorage.java:22-24` 为无条件 Spring component。
- `compose.yaml:27-33` 固定 `rustfs` provider、`http://rustfs:9000` endpoint、`path-style=true`，其余 timeout/bucket/app credentials 从 env 进入后端。Compose 的未来切换还需要届时明确调整 provider 配置和 RustFS dependency，不能声称现在改 env 就能换云。
- `README.md:127`、`.trellis/spec/backend/private-attachments.md:55`、归档 `10-03-community-oss/design.md:112-116` 都明确：未来保留 ObjectStorage/逻辑 key/业务合同，但要先完成适配器与资源测试，同 key 搬迁并核对长度/SHA；没有自动搬迁、双读或云回退。归档任务名称中的 `oss` 指对象存储需求，不证明已经实现阿里云 OSS。
- `backend/pom.xml:21-27,33-44` 固定 Java 17、AgentScope 2.0.3、AWS SDK 2.55.10 与 Apache5 S3 transport。本轮不需要云 SDK 或运行外部工程。

### 2. 已找到的文件

| 文件 | 实际用途 |
|---|---|
| `backend/src/main/java/com/aiworkbench/storage/ObjectStorage.java` | 可直接复用的 put/open/delete 接口，provider 不负责业务鉴权 |
| `backend/src/main/java/com/aiworkbench/storage/RustFsObjectStorage.java` | 当前唯一 provider；S3 SDK、原字节长度/SHA、可 abort stream |
| `backend/src/main/java/com/aiworkbench/config/StorageProperties.java` | 当前 RustFS-only 配置与 timeout/deadline 校验 |
| `backend/src/main/java/com/aiworkbench/storage/AttachmentValidator.java` | 与 post 无关的流式 stage、真实字节/SHA、文件名处理、有界验证 worker |
| `backend/src/main/java/com/aiworkbench/storage/AttachmentValidationWorker.java` | 私有 `markdown(Path)` 严格 UTF-8/控制字符验证 |
| `backend/src/main/java/com/aiworkbench/storage/ValidatedAttachment.java` | 原临时文件及 verified metadata；AutoCloseable 删除临时文件 |
| `backend/src/main/java/com/aiworkbench/service/impl/AttachmentServiceImpl.java` | post-specific 上传编排、鉴权后 open、事务外 put/delete 模式 |
| `backend/src/main/java/com/aiworkbench/service/impl/AttachmentPersistenceService.java` | post-specific reserve/finalize/token/清理持久状态参考 |
| `backend/src/main/resources/db/migration/V18__community_attachments.sql` | 社区附件 key/size/SHA/state/token 和 post-owner/reference FK；不适合作简历表 |
| `backend/src/main/resources/mapper/AttachmentMapper.xml` | READY/owner/current revision 查询与稳定元数据存储 |
| `backend/src/main/resources/application.yml` | 原生 RustFS env 配置、lazy multipart 与运输上限 |
| `compose.yaml`、`.env.example` | 私有 RustFS 1.0.0、持久卷、分离管理/app 身份及 loopback ports |
| `scripts/local/initialize-storage.py` | 明确本地 endpoint/bucket、最小 community prefix IAM、实测正负权限 |
| `scripts/local/backup.ps1` | 仅 PostgreSQL custom archive 与新库隔离恢复，不备份对象 |
| `scripts/local/common.ps1` | 本地脚本 Docker/WSL 与 env 支持 |
| `scripts/local/verify-compose.py`、`scripts/local/storage-smoke.py` | 五服务隔离运行、真实对象及 JVM 崩溃验证参考 |
| `deploy/nginx.conf` | 默认 body 1m；现有社区上传单独 22m |
| `backend/src/test/java/com/aiworkbench/storage/AttachmentValidationTest.java` | UTF-8/BOM/空 MD/格式/字节等号与 +1 测试 |
| `backend/src/test/java/com/aiworkbench/storage/RustFsStorageIntegrationTest.java` | 真 SDK 原字节/SHA、错误 checksum、匿名 GET/HEAD、delete 测试 |
| `backend/src/test/java/com/aiworkbench/community/Attachment{Http,Concurrency,ReadConsistency,Quota,Migration}IntegrationTest.java` | 社区 owner/状态/读写/并发/迁移回归参考（实际为五个文件） |
| `backend/src/main/java/com/aiworkbench/config/IsolatedProfileSchemaGuard.java` | test/e2e/live 只能一个 profile，拒绝使用普通/public schema |
| `.trellis/tasks/archive/2026-10/10-03-community-storage/{design,validation,check-report}.md` | 已归档存储设计与当时的实际验证证据 |
| `.trellis/tasks/archive/2026-10/10-03-community-oss/{design,completion-audit}.md` | 社区总任务的对象存储/未来云边界与完成审计 |
| `.workbench/inbox/AIW-INTERVIEW-20261003-cf4cf8fc-r1/{HANDOFF,solution,core-code-map,requirements,acceptance}.md` | 面试需求、源 commit、复用地图和 AC；不是实现或测试结果 |
| `.workbench/inbox/AIW-INTERVIEW-20261003-cf4cf8fc-r1/prototype/ai-interview-prototype.html` | 源包自带完整 HTML/CSS/JS，可直接复用视觉代码/布局后接真实 React 数据流 |
| `E:/projects/interview-guide/` | 本机已有面试参考源码，包含外部 Java/Prompt/Skill/AGPL LICENSE；只读参考 |

### 3. 可直接复用的 Markdown 代码与边界

真实入口是 `AttachmentValidator.stage(String name, InputStream input)`（`AttachmentValidator.java:17`），无 postId/owner 参数。它按文件名扩展名确定真实校验类型，MD 业务上限为 `1_048_576` bytes（`:21-27`），流式计数并计算原文件 SHA（`:28-40`），返回 `ValidatedAttachment(path,fileName,contentType,size,sha256)`（`ValidatedAttachment.java:8`）。调用方用 try-with-resources 关闭，原临时文件被删除（`:9`）。

`AttachmentValidationWorker.markdown(Path)` 当前是 private static（`:32`），不能在 resume service 直接调用。它使用 `UTF_8.newDecoder()` 和 `CodingErrorAction.REPORT`（`:33-34`），拒绝 NUL 及 ISO controls，允许 CR/LF/TAB（`:35-38`）；空 MD 和 UTF-8 BOM 可以通过。现有测试 `AttachmentValidationTest.java:36-40,52-60` 覆盖 BOM、坏 UTF-8、NUL/control、空文件、exact 1MiB、+1。它没有 20,000 码点规则，也不会自动剥除 BOM 或返回正文。

正式接入建议：

1. 新 resume controller/service 自己校验身份、版本、幂等及只接受 `.md`；不能因为 stage 支持 PDF/图片，就把这些类型引入简历。社区 upload API 与 post 表/FK 不复用。
2. 导入复用 stage 得到验证过的原文件；再严格读取正文，按决定仅剥首 UTF-8 BOM，按 Unicode 码点计数 `<=20_000`。原存储字节、size、SHA 保持不变，正文快照可以没有 BOM；正文指纹与原件字节 SHA 是两个概念。
3. 为粘贴和导入使用同一文本规则，可以把当前 markdown 检查抽取成 `storage` 中很小的共享 helper，让现有 worker 继续调用它；避免复制一套控制字符/解码规则。粘贴不创建持久对象、不制造伪原文件（requirements FR-003）。JSON String 的不成对 UTF-16 surrogate 应明确拒绝，不能在转 UTF-8 时静默替换。
4. 20,000/20,001 测试要包含 supplementary Unicode/emoji，不能用 Java String.length 代替码点数；不得静默截断。

组合上限需准确表述：合法 UTF-8 每码点最多 4 bytes，20,000 码点正文的原件最多约 80,003 bytes（含单首 BOM）。因此完整 1MiB MD 不可能同时满足 20k 正文上限。AC-023 中 byte gate 的等号可在 stage 层单独验证；完整 import 还必须因 text gate 超限而拒绝。不能写成“完整 resume import 的 1MiB 文件必然成功”。

### 4. 本地 RustFS 与未来云切换合同

直接复用 `ObjectStorage.java:8-15`：

```java
void put(String key, Path validatedFile, long size, String contentType, String sha256);
StoredObject open(String key); // stream(), size(), close()
void delete(String key);
```

- 简历业务只依赖该接口。owner/expectedVersion/requestId/引用/状态均由业务层和 PostgreSQL 保证；客户端不提供任意 key/provider/bucket/URL。
- 新 key 建议固定为 `interview/resumes/<ownerUUID>/<objectUUID>`；owner 来自 CurrentUser，object UUID 每次尝试生成。也可以进一步固定具体 CHECK 正则，但需在正式 design 中冻结。key 不含 filename、endpoint、provider、签名或可变 current version；同一个原件 key 永不重写。
- 简历表与原件状态表只保存逻辑 key、真实 bytes、原字节 SHA、verified type、必要 owner/token/version；DB/API 不存 provider endpoint、bucket、签名 URL。面试表保存创建时的简历文本快照和 resume version/正文摘要，不依赖原件读取，历史文本快照不会阻止不再引用的原件清理。
- 当前 RustFS 原生 endpoint 为 `http://127.0.0.1:9000`，Compose 为 `http://rustfs:9000`。管理 `RUSTFS_*` 只进入存储服务/显式初始化；后端只使用 `WORKBENCH_STORAGE_*` app 身份。没有前端 VITE storage env，没有公开/预签名 URL。
- 当前 adapter 使用 known-length Path PUT、raw 32-byte SHA256 再 Base64 checksum、metadata SHA（`RustFsObjectStorage.java:46-51`）。open 返回长度和 stream，close 使用 abort（`:55-62`）。凭据缺失为安全存储错误（`:31-33`），没有匿名/root/local-file fallback。
- 云申请后再实现并验收 `AliyunObjectStorage` 或确认过的 OSS S3 provider，保留同一接口/逻辑 keys。当前不新增占位 provider、默认禁用功能门禁、双读、自动回退、兼容迁移或真实云调用。
- “无缝”应定义为不改 resume/interview 业务代码、DB/HTTP 合同和浏览器路径；未来适配器/config/IAM/资源与实际搬迁仍需做。现有存量对象必须同 key 复制、逐对象核验 size/SHA 后才允许 configuration cutover；改 endpoint 不会搬数据。

阿里云官方文档目前给出 AWS Java SDK 2.x 接 OSS 的配置候选：S3 endpoint `https://s3.oss-{region}.aliyuncs.com`、virtual-hosted（pathStyle=false）、chunkedEncoding=false、V4 签名；示例 region 与 RustFS 当前默认并不等价。OSS 只支持 virtual-hosted addressing，不能沿用当前强制 pathStyle=true。文档还列出可能需启用 S3-compatible authentication、以及新 OSS 用户中国内地默认公网域名数据 API 限制。它们是未来资源申请/适配验收的检查项，本轮不据此猜测可用 endpoint 或声称云适配已经通过。见下方官方 references。

### 5. 私有简历 IAM 与初始化

当前初始化 `PREFIX="community/attachments/"`（`initialize-storage.py:23`），`policy_for(bucket)` 仅 Allow 指定 resource 的 `s3:PutObject/GetObject/DeleteObject`，并 Deny `admin:*`（`:48-52`）。没有 app ListBucket、桶管理或任意前缀权限。

新增简历最小权限是保留现有 `community/attachments/*`，再显式授予同一个明确 bucket 的 `interview/resumes/*` 三个 object actions；不授全桶、admin、anonymous、CORS 浏览器直传或每用户根账号。共享 app IAM 只提供服务端存储能力，用户 owner 隔离仍由应用 SQL/HTTP 控制。

直接把 policy_for 改成两前缀并重跑当前 initialize 会失败：已有 named policy 的 canonical shape 不同即 `conflicting-existing-policy`（`:136-139`）；existing user policy 也做完整 shape 比较（`:146-153`）。正式计划必须有一次明确的当前需求权限更新操作：

1. 显式指明本地 endpoint、bucket、app identity；核对当前 policy 精确等于已知最小 community policy 或新 canonical union，未知权限冲突 fail closed。
2. 使用明确管理身份安装/更新新的 canonical 两前缀 policy 并绑定指定既有 app identity；不替换密码，不改变其他身份/策略/桶 ACL。不用业务 API 自动扩权或启动时猜测修复。
3. 更新 initializer 的返回文案/probe，使两种 prefix 都验证；旧 policy 的常规权限更新不是应用旧版本 fallback，不引入永久的旧行为分支。
4. 在隔离桶实际证明两前缀 Put/Get/Delete 原字节可用；匿名已有对象 GET/HEAD=403；outside prefix/bucket policy/ACL/admin/self-management 均拒绝。新简历 API 的 A/B/ADMIN owner 测试不能被单一 app IAM 测试替代。

当前 initializer 仅允许 127.0.0.1/localhost/rustfs 明确本地 URI（`:108-113`），管理/app credentials 要不同（`:114-118`），并验证 private ACL/无匿名 policy（`:123-131`）。保留这些边界。

### 6. 上传、current swap 与物理清理

不能直接复用社区业务表：V18 `community_attachments` 要求 post/owner FK（`V18__community_attachments.sql:3-6,18-20`），object_key CHECK 限定 community 前缀（`:6`）。复用的是原仓库的存储接口、validator 和持久状态模式，不以虚假 post 挂载 resume。

正式 schema 至少要能表达唯一 current resume/version 和独立的 immutable 原件处理记录。记录应跟踪 requestId、payload fingerprint（包括实际原件 SHA/bytes）、key、attempt token/deadline、READY/FAILED/清理状态，使 DB 确认失败、进程崩溃和迟到 put 不会丢失清理对象的唯一线索。表名由正式 design 冻结。

建议的合同流程：

1. 验证 owner/request/version，stage 原件并校验正文；短事务在 stable owner resume row 下 reserve 新 immutable key/token，旧 current 仍生效。
2. 事务外 `storage.put`；短事务按同一 active token、expected current version 和有效 state 完成新 current 文本+metadata 的原子 swap。put/DB 确认失败或 stale version 时不覆盖旧 current，新增候选进入可追踪失败/清理状态。
3. swap 提交后旧原件若无 current 引用且无仍有效上传/替换 token，可以 claim 清理；外部 delete 不持 DB 事务，结果另用短事务匹配 token 写回。新 current 的保存结果与旧原件物理删除结果区分，delete 失败不可误报清理成功。
4. 删除 current 或由 MD_FILE 改为 PASTE 时，旧正文/current 文件读取立即关闭；保留最小不可读取 key/state/token/deadline 供明确重试，不能保留可查询的旧正文/文件名形成暗中版本库。历史 interview 的文本快照独立保留。
5. 删除 state 必须封住新绑定/迟到 finalize；有效 I/O deadline 内不能清掉仍在进行的上传。失败/不确定 put/进程中断的 immutable key 持续可追踪；delete tombstone 允许以后重复检查/清理不确定迟到 put，不能第一次 delete 后销毁唯一 key。
6. resume current 的版本/epoch 删除后不能重置造成 ABA。不暴露用户可选版本库/回收站，不新增按天自动删除或不要求的账号 quota。手动保存/删除引发的必要原件补偿与 AI 手动 retry 是不同责任。

参考当前 `AttachmentServiceImpl.java:28-45,70-78` 与 `AttachmentPersistenceService.java:36-84,122-154`：外部 I/O 无长事务、持久 token、不可变 key、清理 claim/finalize。不要复制 post-specific SQL/锁；简历固定采用同一 owner/current→object 锁顺序。

原件读取只通过 owner 后端 stream endpoint（正式 API 名称待 design 冻结），先 owner/state 验证再 open/发 metadata；匿名 401、他人或 ADMIN 请求他人 404。使用 `no-store, private`、`nosniff`、MD attachment 安全文件名；没有 redirect/签名/共享 cache/304。不以客户端传入 key 访问原件，不从原件读取触发模型。

### 7. Nginx 和实际文件边界

`application.yml:28-32` 已有 file=21MiB、request=22MiB、resolve-lazily=true，够新的 1MiB 导入运输层；业务仍 stage 精确执行 1MiB 与正文码点限制。

`deploy/nginx.conf:14` 默认 `client_max_body_size 1m`，只有社区上传 regex（`:17-18`）放宽 22m。新 resume import 不能沿默认 `/api/` 路径接受 1MiB 文件加 multipart overhead。应仅给新 canonical import 路径显式运输上限（例如 2m）、匹配 proxy headers/timeouts；其他 API 保留 1m，社区路由保留既有 22m。2m 是 bounded transport allowance，不是简历业务大小变更。

### 8. 备份与隔离恢复的可操作设计

当前 `backup.ps1:1` 只有 `RestoreTo/ArchivePath`；`:9` 固定 `ai-workbench-postgres-1`，`:24` 只做 PostgreSQL pg_dump custom archive。它校验 checksum/readability（`:33-35`）、只恢复到新的 `d10_restore_*` DB（`:8-12,36-39`），原 DB 不覆盖；不包含 RustFS 原字节、bucket/IAM 或对象 manifest。不能把该 dump 宣称为简历原件备份，也不能直接拿该脚本验证另一个 Compose project 的 PostgreSQL 容器。

本轮建议补一个很小的 DB+objects 联合备份/隔离恢复操作合同，而不建通用备份平台：

1. 准备忽略的 `.local-backups/ai-interview/<run>/` bundle，记录 source DB/schema/逻辑 namespace、Flyway version、dump SHA、manifest SHA、开始/结束时间。原件/DB 包含私有资料，不能提交 Git。
2. 为保证 DB 和对象一致，暂停所有连接源 DB 的 app writers（native 使用已有 stop.ps1 的 owned-process 校验；Compose 只 stop 指定 backend），保持 PostgreSQL/RustFS 数据服务和卷；验证没有其他 app writer/cleanup 仍在运行。不是 docker down -v，也不是清表。存储对象一直是 immutable，因此一个停写窗口足够，不需要双库分布式事务。
3. 做 DB custom dump；用此同一 dump 恢复到已确认不存在的新 `d10_restore_*` DB，再从该快照读对象清单，不从不断变化的 live DB 随意扫描。已有脚本在普通本地 source 上可使用 `-RestoreTo`；隔离测试应显式提供新的容器/库，需为脚本增加目标参数或直接调用命名 Docker exec，不让 hardcoded source 误入日常库。
4. manifest 至少包含所有被 current resume 引用的 READY MD_FILE 原件和社区仍保留引用的 READY 对象，字段仅逻辑 key/type/size/SHA/归属引用。没有 ListBucket 权限也能完成：按数据库精确 key 使用当前 app GetObject。未引用但仍 READY 的可恢复文件如何覆盖，应明确记录；失败/已删除 cleanup receipt 不是必须存在的业务原件。若保留临时失败对象供故障审计，单列 optional-present，不能把缺失 tombstone 对象当有效原件丢失。
5. planned helper 可采用 `scripts/local/backup-objects.py --env-file <ignored> --manifest <bundle>/objects.json --output <bundle>/objects`；这是拟新增接口，当前尚无该文件。使用现有 stdlib Client 签名思路/有界流式 GET，按 key 的 hash 生成 bundle filename 并保留映射，禁止 path traversal；严格核对原字节 size/SHA。任何必要对象缺失/不可读/哈希不同则 bundle 不标 complete。凭据只在 env，manifest 不存凭据/签名 URL/provider URL。
6. 恢复必须拒绝现有目的 DB 和现有目的 bucket；使用新隔离 Compose project、未用 ports、全新 RustFS volume、全新私有 bucket 和 synthetic app identity。按 manifest 同 key PUT 原字节，逐对象 GET size/SHA 核验；为隔离对象桶初始化 canonical 两前缀最小权限。恢复的 app 指向新 DB+新对象桶，模型 key 为空，不触碰源桶/源库。
7. 验证恢复的 current Markdown、MD_FILE 原件下载 SHA、历史 interview snapshot 与 current version、社区引用与 Flyway metadata，分别记录 PostgreSQL 恢复和 RustFS 恢复结果。错误 SHA、对象缺失、existing target、恢复中断后不误报 complete 都应验证。
8. 在线删除不代表旧 dump/bundle 的同步删除（FR/AC-022）。不用伪造“所有备份已清理”。对象备份只保留本地原字节和逻辑 key；未来迁到云依然可复用同一 bundle 合同。

现有普通本地 DB 脚本的真实命令（研究阶段未执行）：

```powershell
powershell -NoProfile -File scripts/local/backup.ps1 -RestoreTo d10_restore_ai_interview_20261004
powershell -NoProfile -File scripts/local/backup.ps1 -RestoreTo d10_restore_ai_interview_copy_20261004 -ArchivePath <explicit-existing-dump>
```

这两条会读取普通本地 Compose source；正式隔离演练必须先确认目标，不能复制命令就运行。新对象 helper、target 参数和联合验证都是本次尚待实现/验收内容。

### 9. 原型直接复用与外部源码状态

源包的 `prototype/ai-interview-prototype.html:7-94` 具有完整 style 和页面结构，`:95-98` 是静态 screen/hash 切换。它是用户指定的 UI 核心代码复用对象；应把 CSS/布局/文案/组件结构迁入现有 React feature，再接真实状态/API。静态 hash/demo fixture 不是 production router/状态实现。不能因另一仓库 AGPL 就将此自带原型和本仓库代码一起判定为不能复用。

本机找到 `E:/projects/interview-guide`，包括：

- `app/src/main/java/interview/guide/modules/interview/service/InterviewSessionService.java`、`model/CreateInterviewRequest.java`；
- `app/src/main/resources/prompts/interview-evaluation-system.st`；
- `app/src/main/resources/skills/frontend/SKILL.md`、`skills/ai-agent-dev/SKILL.md`；
- 根 LICENSE、AGENTS、Gradle 版本配置。

这意味着外部 Java/Prompt/Skill 并非材料不可获得。但该目录的 origin/HEAD 未由研究角色校验，不能声称是交接 commit `4341b0597466b2a9ce8a72967552c1a020878324`。主会话可只读确认；本轮没有安装、运行、修改外部源码。

本地外部 `AGENTS.md:3,9-14` 与 `app/build.gradle:99-100` 为 Boot 4.1.0 / Java 25 / Spring AI / JPA 等；不可迁入目标 Java17/Boot3.5/MyBatis/AgentScope runtime。其 evaluation prompt `:2` 写死 Java 后端专家。它不适合原样服务四方向，skills 列表没有独立 full-stack 资源；本轮可按行为合同独立实现本仓库 gateway。

外部根 `LICENSE:1-2` 确认为 GNU AGPL v3。直接复制/派生其 Java、Prompt、Skill、reference 文本前需明确 provenance 和适用许可范围，保留 notices，并根据复制/发布/网络使用情况准备 Corresponding Source 等义务。GNU §13 针对修改后的可远程网络交互程序要求向相关用户明显提供免费获取对应源代码的机会；不能把它简化成“任何看过源码都要公开全仓库”，也不能提供法律保证。当前主会话决定本轮不复制外部素材，因此这一项作为未来复制前门禁，不阻塞原型/本仓库代码复用与独立 gateway。

### 10. 建议验证命令与覆盖

以下为计划/既有入口，本研究没有启动服务、执行测试、调用模型、修改真实 IAM 或操作源数据。

现有 initializer（完成本次 canonical policy 更新实现后在明确隔离桶执行）：

```powershell
python scripts/local/initialize-storage.py --env-file .local-runtime/ai-interview-test.env --endpoint http://127.0.0.1:<verified-unused-port> --bucket <new-private-test-bucket>
```

现有测试入口（准备明确 synthetic env、隔离 PostgreSQL/Redis/RustFS、空 model key；URL currentSchema 与 schema guard 一致）：

```powershell
mvn -s backend/maven-settings-aliyun.xml -f backend/pom.xml -Dtest=AttachmentValidationTest,RustFsStorageIntegrationTest test
mvn -s backend/maven-settings-aliyun.xml -f backend/pom.xml -Dtest=AttachmentHttpIntegrationTest,AttachmentReadConsistencyIntegrationTest,AttachmentConcurrencyIntegrationTest test
mvn -s backend/maven-settings-aliyun.xml -f backend/pom.xml clean verify
docker compose config --quiet
python scripts/local/verify-compose.py
```

`verify-compose.py` 本身固定隔离 project `d10deployvalidation`，使用 synthetic credentials/ports/volumes，初始化并执行真实 storage smoke（`:20,310-311,343-344`）；正式使用前仍须核对它所选 port/project 没有其他任务占用。`storage-smoke.py` 是由 verifier 调用的 helper，不能误写为 standalone CLI 已有入口。

新增 meaningful 验证需覆盖：

- import 的 .md-only、BOM/invalid UTF-8/NUL/control、original byte hash、20k/+1 supplementary Unicode、byte gate 等号/+1；paste 不 put。
- A/B/ADMIN owner GET/HEAD、匿名/CSRF、metadata 不泄露、无 provider URL/redirect、proxy import 运输上限。
- 同 expectedVersion 并发替换至多一个生效；响应丢失按原 receipt 重放，不重写/不重复 put；校验/put/DB confirm 失败保持旧 current。
- 替换→旧原件 claim/delete，DELETE failure 不可读且可重试；有效 upload 不误删；kill JVM 于已提交 upload 后新 JVM recover，迟到 put/finalize 不复活 current。
- 删除 current 历史 snapshot 保留；删除 interview 不误删 current 原件或其他面试；current version 删除后不 ABA。
- 实际两个 prefix 权限、现有社区回归，应用身份无 ListBucket/admin/outside prefix。
- 联合 bundle dump/manifest/checksum，新 DB+新桶+新卷恢复，必要对象 missing/SHA mismatch fail closed，既有目标拒绝；原库/原桶/原 .env 前后指纹不变。

归档 `community-storage/validation.md:11-18` 有当时 SDK/validator/1MiB/IAM/clean verify PASS 证据，`check-report.md:47-49,83-87` 有 provider/worker/native restart 证据。这些可指导新用例，不代替上述新 resume 合同的实际验证。

### External references

- [阿里云：使用 AWS SDK 访问 OSS](https://www.alibabacloud.com/help/en/oss/developer-reference/use-aws-sdks-to-access-oss)（页面更新 2026-04-17；本次读取）——S3 endpoint、Java SDK2/pathStyle=false/nonchunked、RAM/公网域名注意事项。
- [阿里云：OSS 支持的 S3 API 与限制](https://www.alibabacloud.com/help/en/oss/developer-reference/compatibility-with-amazon-s3)（页面更新 2026-09-17；本次读取）——Put/Get/Delete 可用范围、virtual-hosted-only、ETag 语义差异；本合同仍以原字节 SHA 而非 ETag 校验。
- [阿里云：0002-00000033 S3 V4 authentication](https://www.alibabacloud.com/help/en/oss/user-guide/0002-00000033)（本次读取）——S3-compatible authentication 未开启时的拒绝，不作为当前可用配置承诺。
- [GNU：AGPL v3 §13](https://www.gnu.org/licenses/agpl-3.0.en.html#section13)、[GNU FAQ：Corresponding Source](https://www.gnu.org/licenses/gpl-faq.html#AGPLv3CorrespondingSource)（搜索结果提供官方条文/FAQ，本次核对）——未来直接复制/派生范围的许可核对，不构成法律意见。

### Related specs

- `.trellis/workflow.md`：规划产物与研究持久化；研究角色仅写本 task research，不执行 git。
- `.trellis/spec/backend/index.md`：真实 backend guidelines 索引。
- `.trellis/spec/backend/private-attachments.md:13,30-55,77-83`：ObjectStorage、strict MD、I/O/DB/token、IAM、provider-neutral key、验证。
- `.trellis/spec/backend/identity-isolation.md:9,28,34-44`：CurrentUser/async persisted owner/ADMIN 无业务绕过。
- `.trellis/spec/backend/local-delivery.md`：owned-process、忽略合成 env、custom dump 与新库恢复。
- `.trellis/spec/backend/linux-deployment.md:21-23`：Compose RustFS 管理/app separation/卷与不重建日常 PG/Redis。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`：共享规则沿数据所有者提取，复用现成模块，避免复制一套 validator 合同。

## Caveats / Not Found

- 本研究未执行 git；网页 commit 对本地 HEAD 的精确文件 diff、外部目录 origin/HEAD 核对归主会话。本次只核对当前真实文件。
- 没有 Aliyun OSS adapter、云 resource/API key、云调用/真实迁移/切换测试；本轮明确不实现/申请这些内容。
- initializer 当前只能 community prefix；新 resume prefix 权限和规范更新是执行阶段必要工作，不能假定已有权限足够。
- 现有 PostgreSQL backup 没有对象备份；拟新增对象 helper/target 参数与联合恢复尚未实现或验收。
- 原型 HTML/CSS 已在源包可用；外部 ai-interview Java/Prompt/Skill 本机也可用，但本轮默认不复制。远端 commit LICENSE raw URL读取失败，已直接检查本地 LICENSE 并核对 GNU 官方条文，不能声称远端指定 commit 经过本次下载校验。
- 本研究没有运行 lint/build/test/runtime/IAM/云/模型操作；归档测试证据注明历史时间和原范围，不冒充本次通过。
- 严格 20k 正文与 1MiB 文件的双上限存在复合约束，正式 AC 必须分层表达等号验证；不静默放松任一上限。
- Windows 默认 exec sandbox 因 helper sandbox lock/ACL失败；随后均用明确 require_escalated 只读命令，唯一 shell 写入是建立本 task 的 research 目录。未写代码/spec/外部目录，未读取正常 `.env` 值，未加载 implement/check.jsonl。
