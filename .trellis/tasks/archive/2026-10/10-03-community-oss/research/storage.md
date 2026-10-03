# Research: RustFS 附件存储与未来阿里云切换边界

- Query: 核对真实后端、本地 WSL Docker 能力及官方资料，规划可用的私有 RustFS 附件链路；以后接入阿里云时保持业务 API、数据库及对象 key 不变。
- Scope: mixed（仓库静态核对、本机只读环境探测、官方文档在线核对）
- Date: 2026-10-03（Asia/Shanghai）

## Findings

### 1. 权威需求与本次研究边界

本次按父任务下发的 `.trellis/tasks/10-03-community-oss` 研究，不读取 implement/check JSONL，不改变任务状态。交接包正文顶部的 revision 2 / draft 是残留；HANDOFF 元数据、R3 专项以及本次用户“本地 RustFS（WSL Docker），阿里云尚未申请 API 暂不实现”的决定优先。附件范围仍以 `requirements.md` 的 AC 定义为准。

| 约束 | 原文位置 |
|---|---|
| JPG/JPEG、PNG、WebP，每件最多 5,242,880 字节 | `.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3/requirements.md:20` |
| PDF 最多 20,971,520 字节，仅下载 | 同文件 `:21` |
| MD 最多 1,048,576 字节，仅下载，不导入正文 | 同文件 `:22` |
| 每篇全部附件合计最多 10 个 / 52,428,800 字节，包含图片、包含等号 | 同文件 `:24`、`:123`（AC-19） |
| 相同 ID 重复正文引用只计一次；不同 ID 分别计数；历史版本对象保留，但不占当前稿件额度 | 同文件 `:123` |
| 草稿仅作者可读；普通读者仅可读当前可见发布版本引用 | 同文件 `:99`、`:107` |
| 撤回、下架、退出、真实失效后拒绝新的读取；HEAD/条件/Range 若支持也同样鉴权 | 同文件 `:103`、`:131` |
| 无当前发布、有效草稿、保留历史版本引用才能清理；无自动清理天数、无账号总配额 | 同文件 `:115` |
| 服务端按真实内容验证；MD 没有独特文件头；格式通过不等于病毒认证 | 同文件 `:119` |

**没有“20 张图片”限额。** 父任务初始研究提示的 20 图已纠正；设计和测试不得采用它。

本次实际只做读取、版本/配置语法探测和研究文件写入；没有安装依赖、拉镜像、启动容器、创建 Bucket、上传对象、执行产品/交接包脚本或连接阿里云。

### 2. Files found 与实际代码模式

| 文件 | 一行说明及证据 |
|---|---|
| `backend/pom.xml` | Spring Boot 3.5.16（`:10`）、Java 17（`:21`）、Spring MVC/Security/Redis Session（`:44`、`:70`、`:74`）；依赖区 `:27`–`:103` 无 S3/OSS/Tika/PDFBox/WebP 依赖。 |
| `backend/src/main/resources/application.yml` | PostgreSQL/Redis Session、loopback 服务及业务配置；完整文件无对象存储或显式 multipart 设置；Redis 会话 `:21`，服务 loopback `:29`。 |
| `backend/src/main/java/com/aiworkbench/security/ApiSecurity.java` | `:53` 起共用 SecurityFilterChain；Cookie CSRF `:56`–`:60`、ADMIN 路径 `:69`、其他路径需登录 `:71`、匿名 401 `:73`–`:74`、CSRF/权限 403 `:75`–`:78`。新附件路径应纳入同一链。 |
| `backend/src/main/java/com/aiworkbench/security/ApiSessionFilter.java` | `:44`–`:49` 读取既有 Session 并执行存活判断；失效时清除身份并失效 Session。不可仅凭 Cookie 或 UUID 判断附件读取权限。 |
| `backend/src/main/java/com/aiworkbench/security/CurrentUser.java` | 现有认证身份入口；附件 owner 必须从认证身份取值，不能接受请求体 userId。 |
| `backend/src/main/java/com/aiworkbench/exception/ApiExceptionHandler.java` | `:18`–`:43` 已有安全 ProblemDetail、409 状态冲突及 400 参数校验翻译；需补附件超限/存储故障的脱敏合同。 |
| `compose.yaml` | `:3` 当前只有 backend/frontend/postgres/redis；`:63` 和 `:78` 把数据库端口发布在 loopback；`:89` 只有 pg/redis 命名卷，无 RustFS。 |
| `deploy/nginx.conf` | `:14` 入口请求体只有 `1m`，`:17`–`:22` 代理 `/api/`，目前不够上传 PDF/多数图片，MD 上限加 multipart 开销也可能被挡住；暂无 proxy_cache 配置。 |
| `scripts/local/common.ps1` | `:6`–`:13` `Invoke-Docker` 优先 Windows Docker，否则 `wsl.exe -d Ubuntu -- docker`；无需另建 Docker 调用约定。 |
| `scripts/local/start.ps1` | `:5` 支持显式 EnvFile；`:27`–`:29` 将配置暂时注入后端进程并恢复；`:35`–`:43` 启动前端时只去除旧后端配置前缀。新增存储凭据前缀也要排除，避免被前端子进程继承。 |
| `scripts/local/verify-compose.py` | `:1` 宣称隔离 smoke；`:53` 起设置隔离端口，`:272` 起检查旧资源，`:412`–`:418` 清理该测试项目。它会创建/重启/删除测试资源，本次未运行。扩展时需覆盖 RustFS，严格保留项目归属校验。 |
| `README.md` | `:99` 本机 Java/Maven/Node 前提；`:107`–`:119` WSL Compose 与 Windows JAR/Vite 两种进程边界；`:180` 后端质量命令。 |
| `.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3/solution.md` | `:91` 短事务与失败补偿，`:93` 内容验证，`:95`–`:101` 鉴权/缓存/发布/保留对象原则；这些是候选设计，非测试结果。 |
| `.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3/acceptance.md` | `:108`–`:169` TC-12～19 覆盖版本替换、失败释放、清理竞争、格式、字节/总量边界、下载及私有直读拒绝。 |

对 `backend/src/main/java`、`backend/src/main/resources` 和 pom 的存储/附件关键词检索无实现命中。应复用现有登录、CSRF、CurrentUser、ProblemDetail、Mapper/Service 分层；现有仓库并没有可直接复用的生产上传器。原型的文件头判断/演示附件 ID 不构成后端安全实现（solution `:132`–`:136`）。

### 3. 已核实的本机状态、权限及验证能力

以下为研究时的实际只读结果，运行时状态可能变化，执行前必须重查：

| 项目 | 实际结果 |
|---|---|
| WSL | 默认 Ubuntu，Running，WSL version 2。`wsl.exe --list --verbose` 输出存在 UTF-16 NUL 展示，但发行版/状态明确。 |
| Windows Docker CLI | `Get-Command docker` 未找到；WSL Docker 可执行。 |
| WSL Docker context | `default`，DockerEndpoint `unix:///var/run/docker.sock`；Docker Client/Server 均 28.3.0。 |
| Compose | v2.37.3；`wsl.exe -d Ubuntu -- docker compose -f /mnt/e/projects/workbench/compose.yaml config --quiet` 实际退出 0，未展开输出配置值。 |
| 已有业务基础设施 | `ai-workbench-postgres-1`（postgres:17.6-alpine）127.0.0.1:5432、`ai-workbench-redis-1`（redis:7.4.2-alpine）127.0.0.1:6379，均 healthy。 |
| 其他资源 | 还存在别的运行/停止容器；只能操作本次明确创建的测试项目，不能按镜像/进程类型批量清理。 |
| RustFS | `docker ps -a` 未发现 RustFS 容器；`docker image ls --filter reference=*rustfs*` 无结果。需要联网拉取镜像；这是实施前置步骤，不是已确认 blocker。 |
| 候选端口 | Windows `Get-NetTCPConnection` 未见9000/9001 listener；WSL `ss -lnt 'sport = :9000 or sport = :9001 or sport = :19000 or sport = :19001'` 也只有表头，无这些listener。探测并未预占端口，实施启动前仍须重查。 |
| 工具 | Java 17、Maven 3.9.9、Node 25.2.1、Python 3.11.0、Windows curl.exe 可用；已有 backend JAR 与 frontend 的 Vite 安装目录。 |
| SDK/解析器 | 只核对官方资料，未下载依赖、未编译或进行真实 RustFS SDK 读写。 |
| 执行权限 | 默认 exec 因 `helper_sandbox_lock_failed` / `SetNamedSecurityInfoW error 5` 无法启动；经 require_escalated 审查的只读命令和研究目录创建成功。未来实施命令应单独按其资源范围审查，不可据此推断所有 mutation 都允许。 |

未读取/打印 `.env` 实值、进程环境、容器 Env 或任何凭据。配置文件凭据相关行在输出中已遮蔽。

### 4. 官方资料与可锁定版本

版本均为 2026-10-03 在线查到的候选，实施时应写入 pom/镜像设置并完成实际解析构建，不能把“文档存在”写成“已安装/已集成”。

| 组件 | 候选版本 | 官方核实与限制 |
|---|---|---|
| RustFS | **1.0.0 stable** | [官方稳定 release](https://github.com/rustfs/rustfs/releases/tag/1.0.0)；`releases/latest` 跳到该版本。列表顶部的 1.0.1-preview.16 是预发行，不应因排在顶部就选用。公开 `rustfs/rustfs:1.0.0` registry manifest 已只读查询成功（exit 0，OCI image index），不是镜像层拉取/启动成功；实际拉取后还须记录 RepoDigest。 |
| AWS Java SDK v2 | **2.55.10** | [官方 release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.10) 日期 2026-10-01；建议 SDK BOM 同版，使用 `s3` 与显式 HTTP client。RustFS 示例中的 2.25.27 只是文档示例版本，不能视作最新。 |
| Apache Tika core | **3.3.2（可选，正式方案不引入）** | [官方下载页](https://tika.apache.org/download.html)说明 3.x 维护线仍支持、Java 11+；[3.3.2 Getting Started](https://tika.apache.org/3.3.2/gettingstarted.html)允许只用 tika-core 做类型探测。ImageIO/PDFBox/严格UTF-8已经分别负责格式验证，首版无需额外Tika依赖或完整parser bundle。 |
| Apache PDFBox | **3.0.8** | [官方下载](https://pdfbox.apache.org/download.cgi)，声明 Java 8+；[3.0 IO 迁移文档](https://pdfbox.apache.org/3.0/migration.html)说明文件 reader 与 Loader API。 |
| TwelveMonkeys ImageIO WebP | **3.15.2** | [官方 release](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.15.2)；候选 `com.twelvemonkeys.imageio:imageio-webp`，JPEG/PNG 先使用 JDK ImageIO。不能只凭 `ImageIO.read()!=null` 或 magic bytes 宣称完整安全验证。 |

公开镜像manifest实际返回Linux amd64子manifest `sha256:ba0a1b53e36f321c0d46f3867104abef169f7bc59c467c664ddac87e7ddc9a8b`，arm64为 `sha256:42edb61d588775f9431ff436216d14392d2234d4eb2ed68321569fbf7245b36b`。两次 `docker manifest inspect rustfs/rustfs:1.0.0` 都exit0；这证明稳定tag可解析，不证明镜像层下载/运行。未知架构的两个descriptor是附加artifact，不选作Linux运行manifest。

RustFS 的 [Java SDK Guide](https://docs.rustfs.com/en/developer/sdk/java)确认：没有独立 Java SDK，使用 AWS SDK v2；连接 S3 API endpoint、region、服务端凭据并开启 path style。本机候选 endpoint 为 `http://127.0.0.1:9000`，容器内 backend 必须使用 `http://rustfs:9000`；9001 是管理控制台。AWS [S3 文档](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)说明 SDK v2.18+ endpoint override 仍可能采用 virtual host，需要显式 `forcePathStyle(true)`。

[RustFS Docker 文档](https://docs.rustfs.com/en/installation/container/docker)支持镜像、命名卷和单节点本地模式；进程非 root UID 10001，bind mount 必须满足所有权。优先 Docker 命名卷，避免对 Windows 盘递归 chown。[RustFS 状态文档](https://docs.rustfs.com/en/operations/status-check)区分 `/health` 进程存活与 `/health/ready` 存储/IAM就绪；应以后者加真实授权 Put/Get/Delete 作为就绪证明。

[Microsoft WSL 网络文档](https://learn.microsoft.com/en-us/windows/wsl/networking)确认 Windows 可通过 localhost 访问 WSL Linux 服务；这不证明本机 RustFS 转发已实测，且不应为本地使用新增 0.0.0.0 端口转发或公网防火墙规则。

### 5. ObjectStorage 边界与未来阿里云

**建议合同（新代码候选，本次未实现）：**

```text
ObjectStorage.put(objectKey, validatedFilePath, actualLength, detectedMime, sha256)
ObjectStorage.open(objectKey) -> 可关闭的 StorageObject（InputStream + 长度/类型）
ObjectStorage.delete(objectKey) -> 幂等删除
```

- 当前唯一生产实现为 `S3RustFsObjectStorage`，用同步 S3Client 和已暂存文件/已知长度上传。接口不接受 HTTP 请求、ownerId 或公开 URL；owner/可见版本/额度都由业务服务决定。
- 同步上传使用 Path/已知准确长度；[AWS 流上传说明](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices-s3-uploads.html)提示未知长度同步流可能整段缓冲。当前每件最多 20 MiB，可先采用单次 PutObject，无需 TransferManager/云 multipart/断点系统。
- 唯一随机不可覆盖 key，例如 `community/attachments/<attachment-uuid>`；扩展名/用户输入/源记录标题不参与 key。F2 使用新 attachmentId/key，不原地覆盖 F1。
- 业务表存 attachmentId、postId、ownerId、objectKey、实际大小、SHA-256、服务端 MIME、清理后展示名、状态/时间；**不存 provider、Bucket、Endpoint、供应商 URL、预签名 URL**。Bucket/provider 是当前部署配置，不是业务身份。
- SHA-256 由实际上传字节计算。供应商 ETag 不能当内容 SHA，也不能要求各供应商 ETag 相同。普通 DTO 不返回 objectKey 或内部 Bucket。
- 配置独立放在 `workbench.storage`：provider、endpoint、region、bucket、path-style、部署身份、connect/read/attempt/total timeout；建议起点 connect 2s、socket read 30s、单 attempt 60s、总调用 120s，再以隔离实测调整。这些是有界工程起点，非已经测得的 SLA。
- 本地配置默认选择可用的 RustFS；未知 provider/不合法配置明确报错。不存在自动使用本机目录、公开桶、空实现或旧版路径的 production fallback。测试替身只在明确 test profile 使用。
- Bucket 初始化是本地部署步骤，业务请求不自动 createBucket/changePolicy。初始化管理身份与 backend 应用身份分离；应用身份只允许指定 Bucket/key 前缀的 Put/Get/Delete（如果引入额外操作，再精确补对应权限），不获得桶策略/ACL/账号管理权限。Bucket、对象 ACL、Policy 都无匿名读取授权，必须以无签名 GET 实测。

**阿里云此轮不实现、不安装其 SDK、不申请凭据、不创建云资源。** 后续增加 `AliyunOssObjectStorage` 即可复用上述业务合同。官方 [AWS SDK 访问 OSS](https://www.alibabacloud.com/help/en/oss/developer-reference/use-aws-sdks-to-access-oss)现有专门 S3 接入文档，但其 endpoint、addressing、分块传输/签名配置有专门约束；原生 OSS SDK/API 与 RustFS S3 不能被假定为同一协议。实现时再核实原生 OSS Java SDK V2 或专用 S3 接口，不能声称只换 endpoint 就已经验证可用。

“无缝切换”指业务 API、表、attachmentId 和 objectKey 不变。**配置变更不会自动搬运存量字节。** 后续先实现/验收 adapter，在明确的迁移窗口或停写窗口把所有仍保留对象按相同 key 复制、逐件核对长度/SHA，再切换部署配置，验证现有草稿、当前版本及历史引用。此轮不实现自动迁移、双读、双写或旧存储回退；切换前不销毁 RustFS 卷。

### 6. 上传、校验与资源边界建议

推荐一请求一文件。先校验有效 Session/CSRF 和稿件归属，再流式计数暂存并计算 SHA；文件落入应用私有临时目录、不在静态目录、不用用户文件名建路径，不调用 `getBytes()` / `readAllBytes()` 做整段内存上传。每件业务上限超出 1 字节即停止；请求 Content-Length 和客户端 MIME 仅作提示。

要落实“owner检查先于应用读取/暂存”，不能仅在接收 `@RequestParam MultipartFile` 的Controller方法体再检查post owner：参数绑定可能已经解析multipart。实现须区分Servlet受上限控制的接收缓冲与应用验证暂存，必要时采用lazy multipart/request控制先执行owner查询再访问part；缺少CSRF header的请求也可能走参数解析，需以真实Servlet测试其拒绝/暂存清理，避免声称单个配置即可保证所有解析顺序。

代理与 Servlet 要留 multipart 开销：候选 Servlet `max-file-size=20971520B`、`max-request-size=22020096B`（21 MiB）、disk threshold=0、指定临时目录。Nginx 为附件上传路径允许 `21m`，保留其他路径原有限制；验证恰好上限的结构有效文件穿过真实 Nginx/Servlet。Spring Boot [3.5.16 MultipartProperties](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/servlet/MultipartProperties.html)默认每文件 1MB、请求 10MB，当前缺少显式配置，必须补齐。服务端仍单独限制图片/MD 的较低上限，并拒绝多文件 part/过多字段。

类型探测仅是第一层。最终最低依赖方案采用实际 ImageIO reader 的 `getFormatName`/结构/解码、PDFBox结构解析和严格UTF-8文本合同，**不引入 Tika**。扩展名必须与真正reader格式一致，而不是ImageIO能读某种格式就接受。若以后确有额外探测需要，Tika候选只可作辅助，不能靠用户Content-Type或文件名hint选格式；MD不得因探测为text/plain/application/json就误拒合法UTF-8文本，不引入全量文档解析/外部fetch。

| 类型 | 建议验证合同 | 展示响应 |
|---|---|---|
| JPEG/PNG | 扩展名与真实类型一致；选取对应 ImageIO reader，读取维度与结构并检查解码错误/截断；所有 reader/stream 关闭；不转码原字节。 | 服务端 `image/jpeg` / `image/png`，inline。 |
| WebP | 扩展名与 RIFF/WEBP 类型一致，使用专用 reader 做结构/解码检查，不能只看前 12 字节。 | `image/webp`，inline；保留原字节。 |
| PDF | 类型/结构一致；使用 PDFBox 3 文件 reader，确认能读取有效文档 catalog/page tree，拒绝明显伪造/坏结构；不渲染页面、不执行 JS、不提取内嵌文件、不联网抓取资源。PDF/A Preflight 不是普通 PDF 必选限制。 | `application/pdf`，Content-Disposition attachment。 |
| MD | UTF-8，允许 UTF-8 BOM，非法序列使用 CharsetDecoder REPORT 拒绝；拒绝 NUL/明显二进制。普通合法 UTF-8 文本改为 `.md` 可接受；不要求标题/特殊 magic，不执行或导入正文，不改写字节。 | `text/markdown; charset=UTF-8`，Content-Disposition attachment。 |

[PDFBox IO 文档](https://pdfbox.apache.org/3.0/migration.html)指出 InputStream-backed buffer 可复制整个流且加载为增量解析，单纯 Loader 成功不足以证明所有页面结构被访问；应选 file reader 并明确必要结构校验范围。[JDK17 CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)支持报告非法/不可映射输入；这是 MD 编码合同候选，不是格式识别所有改名文本的保证。[OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)支持内容/扩展名/授权/资源限制及安全文件名的分层校验。

解析资源要受限：限制并发验证 worker、暂存量/连接数、超时及图像分配；5 MiB 压缩图片可能解压很大。不要用只等待 Future 超时就声称不可中断的 decoder 已终止。优先结构/受限解码，必要时使用可终止的隔离进程。**具体像素、页面、加密 PDF/动画 WebP 拒绝策略可能影响合法产品输入，此次需求尚未给出，不得静默添加数量/格式限制；最终设计应写明选择和影响。** 不执行 PDF/MD 并且强制下载，不等于给文件无病毒认证。

### 7. 额度、失败补偿、引用保留与竞争

推荐在验证得到实际长度后，以短 PostgreSQL 事务锁定稿件，原子核对“已选择 READY 附件 + 活跃 UPLOADING 预留”的 unique ID 数和字节，写持久化上传记录与唯一幂等约束，随后离开事务执行 Put。最终再用短事务确认预留/owner/对象信息并标 READY。发布事务再次检验完整引用集合、owner、READY、版本和额度，再切换当前发布版本；数据库与 RustFS 没有共享事务。

- upload 重试 key 作用域至少 owner + post + clientUploadId；同一成功请求响应丢失后返回已有结果，不生成第二件、不重新占额度；同 key 参数/实际内容冲突返回 409。失败后的新尝试不得覆盖已保留对象；旧 key 的补偿记录仍须保留。
- 验证失败：删本次临时文件，无 READY 引用、无已成功正文变更。Put 中断/失败：不能假定对象一定未生成，保留 key/状态供补偿确认，释放当前稿件预留；正文和其他 READY 附件保留。
- Put 成功而 DB 确认失败/进程崩溃：上传前持久化 key/attempt，避免生成不可追踪对象。完成结果不确定时，恢复逻辑按同 key/实际长度/SHA 核对；未完成记录不能被发布。失败需真实标记、可重试，不给浏览器假成功。
- 清理使用持久化 DELETE_PENDING/失败记录；每次按 owner/post 和所有引用重新确认。引用链接操作与标记删除共用对象行锁/状态门禁：先取得无引用状态才能标 DELETE_PENDING，此后拒绝新绑定；对象删除在事务外，失败继续保留待处理状态并可重试。
- 移除草稿引用、撤回/下架都不等于立刻删对象。当前版本、有效草稿、保留历史版本任一引用存在就保留。F1→F2 后读者 F1 返回 404，但作者历史仍可引用 F1；历史附件不计当前额度。
- 首版没有固定天数自动清理或 Bucket 生命周期删除规则。可提供明确触发的孤立对象清理/补偿操作，但不能删除未核实项目归属的对象、不能清空 Bucket。

对应：solution `:91`、`:99`、`:101`；acceptance TC-13 `:116`、TC-14 `:124`、TC-17 `:148`。

### 8. 逐次鉴权读取与缓存合同

读者入口建议 `GET /api/community/posts/{postId}/attachments/{attachmentId}`，作者入口建议 `GET /api/me/posts/{postId}/attachments/{attachmentId}`（交接 solution `:82`–`:83`）。**先认证 Session，再查询数据库 owner/当前发布引用/状态，再开对象流，最后发任何受保护元数据/字节。** 禁止客户端传 key；禁止 302/307/预签名 URL/公开 Bucket/CDN。

- 匿名/真实失效先 401；已登录但不属于允许引用/草稿 owner，统一 404。ADMIN 不因角色获得他人私有草稿读取权限。
- 所有受保护正文/附件成功、失败、HEAD 响应都明确 `Cache-Control: no-store, private`、`X-Content-Type-Options: nosniff`；同源鉴权，代理不缓存这类路径。确认 Spring 默认 Security header 与新 streaming 响应均生效。
- 首版无需 Range/resume，建议声明 `Accept-Ranges: none`，忽略 Range 返回授权后的完整 200；不要借框架 Resource 静态处理器自动开启未审查的范围/304路径。可以不发 ETag/Last-Modified 并忽略条件头，授权后返回完整 200。若后续支持 HEAD/304/206，必须授权后才能生成其元数据/响应。
- Spring MVC 可对 GET 自动提供 HEAD；必须让 HEAD 也走同一授权查询，不能因“不写 HEAD handler”就声称 HEAD 无通道。
- 文件名从服务端清理值使用 ContentDisposition builder 编码；去除路径段、CR/LF/NUL 等控制字符，不直接拼请求 filename 到 header。PDF/MD 一律 attachment，图片仅用服务端检测的 MIME。
- 响应采用有界 buffer 的可关闭流，客户端断开时关闭/abort SDK 流；不要先把整件读入 byte[]。异步 StreamingResponseBody 必须只捕获已授权的不可变描述/key，不能在工作线程依赖未传递 SecurityContext 再寻找 owner。
- 撤回事务提交后，新请求读取数据库的当前可见引用，不依赖历史对象存在与否。已授权开始的流无需强行中断；下载副本、已解码图片无法远程收回，符合 AC-05/14。
- 前端若用 fetch+Blob 显示图片，退出/换号/真实 401、不可见状态后及时 revoke object URL，丢弃旧账号迟到响应；不把附件写到 Service Worker/持久离线缓存。

### 9. 实施阶段可用命令与验收清单（本次未执行 mutation）

**已验证可用的只读命令：**

```powershell
wsl.exe --list --verbose
wsl.exe -d Ubuntu -- docker context show
wsl.exe -d Ubuntu -- docker version --format '{{.Client.Version}} / {{.Server.Version}}'
wsl.exe -d Ubuntu -- docker compose version
wsl.exe -d Ubuntu -- docker ps -a --format '{{.Names}} | {{.Image}} | {{.Ports}} | {{.Status}}'
wsl.exe -d Ubuntu -- docker compose -f /mnt/e/projects/workbench/compose.yaml config --quiet
```

**规划确认后才能执行的操作形态：** 先追加 RustFS compose 服务/命名卷、loopback 端口、后端 endpoint 配置以及忽略的独立测试 EnvFile，使用当前 Ubuntu Docker。固定候选稳定镜像并记录实际 digest；9000/9001 用于本机服务，隔离测试另用经检查空闲的 19000/19001、PG15432/Redis16379/APP18088，不能冲撞现有 5432/6379。

```powershell
# 仅显示无凭据的基础命令；读取的 env file 应由实施阶段生成、gitignored，禁止打印。
# 以下 up/pull 只是未来命令，本研究没有执行。
wsl.exe -d Ubuntu -- docker pull rustfs/rustfs:1.0.0
wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- docker compose --env-file .local-runtime/community-oss-e2e.env -p wbcommunityoss20261003 up -d --wait postgres redis rustfs
curl.exe --fail --silent --show-error http://127.0.0.1:19000/health/ready
```

`rustfs` 服务、上述 env file 和测试项目此刻尚未创建；命令依赖正式实现追加配置。RustFS healthcheck 不能假定镜像内有 curl，需以实际镜像可用命令定义。若镜像名/tag 拉取失败，记录 registry/network 的真实错误后调整可验证的官方分发方式，不静默改为 preview/latest 或启动另一存储。

创建专用私有 Bucket/最小权限应用身份属于部署初始化，使用受控后端/管理步骤，不能把长期凭据放到 CLI 参数、日志、研究、截图或前端。无需安装 aws/ossutil/rc 才能做 Java SDK 正式集成；初始化工具方式实施时按镜像实际能力核实。

**后端质量命令**（README 有据，本次未执行）：在 `backend/` 运行 `mvn -s maven-settings-aliyun.xml clean verify`。已核实实际可执行文件存在：`D:\APPS\apache-maven-3.9.9\bin\mvn.cmd`、`C:\Program Files\Java\jdk-17\bin\java.exe`；Maven输出实际JDK17 home为后者根目录。本机默认platform encoding是GBK，文件读写坚持UTF-8。可在 `backend/` 使用以下明确路径形态，输出版本不输出运行环境：

```powershell
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -version
& 'D:\APPS\apache-maven-3.9.9\bin\mvn.cmd' -s maven-settings-aliyun.xml clean verify
```

普通测试用明确 test storage 替身验证故障；真实 S3 集成使用隔离 RustFS + 专用 Bucket + 新 PostgreSQL schema/独立 Redis namespace，之后只清理该次创建的资源。实际集成测试类/命令需随实现落地，不假造当前已存在的测试名。

**隔离 EnvFile 建议键（仅键名，不是已读取的配置值；最终与实现 properties/compose 保持一致）：**

| 用途 | 键名 |
|---|---|
| 后端对象存储 | `WORKBENCH_STORAGE_PROVIDER`、`WORKBENCH_STORAGE_ENDPOINT`、`WORKBENCH_STORAGE_REGION`、`WORKBENCH_STORAGE_BUCKET`、`WORKBENCH_STORAGE_ACCESS_KEY`、`WORKBENCH_STORAGE_SECRET_KEY`、`WORKBENCH_STORAGE_PATH_STYLE` |
| 有界SDK调用 | `WORKBENCH_STORAGE_CONNECT_TIMEOUT`、`WORKBENCH_STORAGE_READ_TIMEOUT`、`WORKBENCH_STORAGE_API_CALL_ATTEMPT_TIMEOUT`、`WORKBENCH_STORAGE_API_CALL_TIMEOUT` |
| RustFS服务/初始化 | `RUSTFS_IMAGE`、`RUSTFS_PORT`、`RUSTFS_CONSOLE_PORT`、`RUSTFS_ACCESS_KEY`、`RUSTFS_SECRET_KEY`；前两种身份须分离，服务root key不作公开应用凭据。 |
| 当前基础设施隔离 | `POSTGRES_PORT`、`POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、`REDIS_PORT`、`DATABASE_URL`、`APP_BIND`、`APP_PORT` |
| Session/浏览器 | `WORKBENCH_BOOTSTRAP_USERNAME`、`WORKBENCH_BOOTSTRAP_PASSWORD`、`WORKBENCH_COOKIE_SECURE`、`WORKBENCH_WS_ALLOWED_ORIGINS`；测试Redis namespace与PostgreSQL schema用明确profile配置隔离。 |

Compose与宿主backend使用相同storage配置键，endpoint值由其网络边界分别配置；控制台port不是S3 endpoint。`start.ps1`前端环境剔除规则至少追加 `WORKBENCH_STORAGE_`、`RUSTFS_`、`AWS_`，后端凭据绝不能进入 `VITE_*`。隔离EnvFile可由 `start.ps1 -EnvFile`用于Windows JVM，或Compose的`--env-file`读取，不打印、不加入构建上下文。

| 验证 | 必须观察的证据 |
|---|---|
| 真实 SDK Put/Get/Delete | 文件散列、长度、私有权限、进程/容器重启后对象保留；health 200 不能代替它。 |
| JPG/PNG/WebP/PDF/MD | 合法样例、明显伪造/结构破坏、客户端 MIME 伪造、UTF-8/BOM/非法编码/NUL、危险文件名；不转换/不在线预览。 |
| 各类型等号/+1字节 | 结构有效边界样例经 Nginx→Servlet→RustFS；边界不被 multipart 开销误拒。 |
| 单篇合计 | 2×20MiB PDF + 2×5MiB 图片恰好50MiB允许，再加1字节有效 MD拒绝；10件允许，第11件拒绝；重复ID只计一次。 |
| 并发与重放 | 同稿件并行上传/保存/发布不能突破10件/50MiB；成功响应丢失不重复占用。 |
| 失败注入 | 中断、Put失败、DB finalize失败、删除失败/恢复；无未就绪发布、额度释放、正文/已有附件保留。 |
| 清理竞争 | 草稿/当前/历史仍引用对象全保留；绑定与清理交错不删有效对象；只按确切归属清理。 |
| ACL | 从 Windows 及 WSL 对专用测试对象无签名 GET/HEAD 被拒绝；Bucket policy/对象ACL无匿名读；应用身份不能改策略。 |
| Session/版本/缓存 | 匿名401、其他人草稿404、F1/F2保存/发布隔离、撤回/下架404、旧URL及HEAD/条件/Range不能绕过；检查响应头和浏览器网络。 |
| 前后端凭据边界 | 浏览器只访问工作台 API，无云URL/凭据/重定向；frontend启动环境剔除存储凭据；构建产物不含密钥。 |
| 本地与容器 endpoint | Windows JVM使用loopback，容器backend使用服务名；真实访问两种入口，禁止容器使用127.0.0.1访问另一个容器。 |

### 10. Related specs

- `.trellis/workflow.md`：先研究/规划，正式复杂任务需要 prd/design/implement 和审阅后才 start；研究材料持久化。
- `.trellis/spec/backend/index.md:17`–`:28`：相关分层、数据库、身份、错误、运行和本地/Linux交付入口。
- `.trellis/spec/backend/identity-isolation.md:9`：CurrentUser owner，ADMIN无私有业务越权；Session/CSRF合同复用。
- `.trellis/spec/backend/runtime-integration.md:73`–`:81`：mvn/lint/build及实际基础设施验证，loopback、密钥隔离；不能用配置解析冒充运行成功。
- `.trellis/spec/backend/local-delivery.md:5`、`:19`–`:27`、`:48`–`:54`：Windows/WSL两种Docker调用、隐藏应用进程、PID归属、精确清理和新资源隔离。
- `.trellis/spec/backend/linux-deployment.md:19`–`:26`、`:47`：Java17非root容器、服务名连接、代理/Security分工、独立Compose项目/新卷验收，禁止碰现有ai-workbench卷。
- `AGENTS.md`：未获批不得增加兼容层/迁移/旧版回退/功能开关/默认禁用门禁。ObjectStorage接口和未来adapter设计是当前用户提出的架构边界，不是授权现在实现多云兼容。

## Caveats / Not Found

1. 当前没有 RustFS 容器、镜像或附件生产代码；联网拉镜像、命名卷、私有隔离 Bucket和身份初始化仍需在规划批准后的实施阶段完成。没有将其标记为不可继续的 blocker。
2. 官方 release/SDK/解析器版本只是候选事实。稳定RustFS tag的registry manifest已只读查询成功，但未下载镜像层、做Maven依赖解析、JDK17编译或真实SDK集成；实施证据必须写实际结果。
3. RustFS latest稳定release是1.0.0，preview系列与稳定版的文档行为可能不同。必须以固定实际镜像对health/权限/SDK功能验证；不照搬文档中的公开端口/默认凭据/直传/预签名示例。
4. 阿里云API、Bucket/地域、身份和权限均未申请；本轮明确不实现adapter、不连接云。保留业务接口不变不代表配置切换能搬存量。
5. MD编码建议已具体化；加密PDF、动画WebP以及解析资源上限可能形成额外合法输入拒绝，最终设计须说明，必要时在最后汇总向用户确认，不能偷偷作为新的产品限制。
6. 执行时需重查端口、WSL运行态和项目名；不能使用现有日常数据/卷来证明验收，不能按相同镜像名批量删容器。
7. 尚未执行任何TC或产品回归；交接包自带原型PASS和本研究Compose语法通过都不证明附件、安全、缓存撤回或数据库事务已通过。
