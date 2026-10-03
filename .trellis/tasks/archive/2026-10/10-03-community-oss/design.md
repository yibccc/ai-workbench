# 正式设计：发布快照、私有附件与 R2 复用

方案状态：已获用户后续“开工/批准并连续执行”的最终批准，实际实现目标为子任务，父任务保存规划与整合依据。需求唯一依据为本任务 `prd.md`；真实文件锚点见 `research/{backend,frontend,storage}.md`，交接差异见 `research/handoff-check.md`。本文件是本轮已批准合同，不等同于工程已验收。

## 边界与直接复用

继续 Java17/Spring Boot/MyBatis/PostgreSQL/Redis 与 React19/Vite/TypeScript；Controller → Service接口 → ServiceImpl事务 → Mapper/XML。新增发布领域，不给私有记录添加公开标记，不修改旧 owner SQL。复用 `CurrentUser`、Cookie/CSRF、ProblemDetail、`PageQueries`/`PageResponse`、共享Dialog、账号epoch与RetainedView；不创建第二套鉴权或通用社区服务。

原型 `research/handoff/r3/assets/prototype-r2/src/styles.css` 和 `app.js` 的页面区域、卡片/编辑器/附件/弹窗骨架直接转入 React；不是只看图重绘。生产文件位于 `frontend/src/features/community/`、`features/publishing/`、`api/community.ts`、`api/publishing.ts`。CSS限定 `.community-shell`，保留原型218px桌面侧栏、≤1190px的192px及≤760px移动呈现；原五工作区CSS不改换。原CSS全局reset/变量移入作用域，Dialog portal显式带community样式。沿根100dvh/min-height:0链，sidebar/topbar及mobile bottom nav位于滚动区外，正高度/命名/可键盘访问的`.community-scroll`内保留R2 main1320px/max-width、padding/columns分组；只适配sticky offset到新scroll owner。实测document不横/纵溢出、最后动作/附件/pager可达、mobile save/preview在bottom nav上方，截图以明确滚动位置对照。源码具体复用/去除清单及17图状态索引由frontend研究定位。

删除演示身份/状态菜单/虚构fixtures/固定专注时刻；LoginPage继续真实表单、用户名focus与密码eye。图片图案样例不作用户图片或正式资产。原型的innerHTML、简易Markdown/文件头检查、内存权限不复用。

## 账号根、导航与正文

`Workspace key={account.id}`继续持有唯一 `useFocusController`、声效、活动/实时连接与已访问RetainedView。社区与五工作区同账号内切换不重建Workspace；退出/真实401/换号中止所有请求、撤销Blob URL并卸载整个账号树。仅明确点击/编辑信号活动，读列表、文件GET、刷新/轮询不续期。

扩展一个严格hash parser作为现行唯一导航合同，直接保留现行 `#records/#tasks/#focus/#reports/#projects/#users`，新增：

| hash | 目标 |
|---|---|
| `#/community` | 广场及分类筛选 |
| `#/community/posts/<uuid>` | 当前发布详情 |
| `#/community/authors/<uuid>` | 公开昵称/简介及可见发布 |
| `#/publishing` | 我的发布/草稿 |
| `#/publishing/sources` | 指定日期今日素材 |
| `#/publishing/new` | 三类发布选择 |
| `#/publishing/new/<DAILY\|MOMENT\|BLOG>` | 新稿编辑 |
| `#/publishing/posts/<uuid>` | 本人草稿编辑/预览 |
| `#/publishing/posts/<uuid>/preview` | 当前本地输入预览，返回编辑不隐式保存 |

不添加旧地址映射/路由兼容层或路由库。未登录保留已解析的允许站内目标，鉴权成功后回到该目标；不接受任意URL/外站redirect。数据请求必须等 `/api/auth/me` 证实身份；不可见详情404，不展示旧缓存，回访详情重新鉴权/读取。新稿直开仅呈现本地表单，不因页面读取自动POST创建，作者明确保存/上传/发布才按需要创建私有稿。离开社区未保存编辑上下文（space/date/post/browser back）先走一次共享Dialog，可继续编辑/放弃/保存后离开；取消不改URL/选择，保存失败保留输入不导航，beforeunload只用浏览器自带机制。原五工作区依旧RetainedView保留，不能因新增space把原report/record dirty状态改成全局discard。手机在既有真实账号/space菜单补工作台返回，保留R2四项bottom nav。专注芯片复用唯一root真实状态。

正文持久化Markdown文本，安全React渲染采用 `react-markdown@10.1.0`，不增加GFM/rehype-raw，`skipHtml`。普通链接只允许http/https；外链图片不抓取。内部图片引用为 `attachment:<uuid>`，仅当ID属于当前稿件/当前发布附件集合时才交给图片组件，经身份管线获取Blob。服务端验证内部ID归属及附件集合，非法引用拒绝；读者DTO不含object_key或云地址。图片列表与大图都复用R2布局、共享modal/focus规则；PDF/MD只有下载。

## 数据与迁移所有权

只追加新Flyway迁移，V1～V16字节不变；不迁移原私有数据为帖子。

**publishing子任务拥有 V17：**

| 表 | 关键字段/约束 |
|---|---|
| `community_posts` | UUID id、owner_id FK user_accounts、type `DAILY/MOMENT/BLOG`创建后不变、status `DRAFT/PUBLISHED/WITHDRAWN/HIDDEN`、version≥0、current_revision_id可空、first_published_at可空、created_at/updated_at TIMESTAMPTZ；`(id,owner_id)`唯一支持复合owner FK；业务日期由对应draft或revision投影 |
| `community_post_drafts` | post_id唯一、owner_id、type `DAILY/MOMENT/BLOG`（MOMENT界面称动态）、business_date（DAILY必填）、title、summary（博客可选摘要）、body_markdown、private source_selection JSONB；复合FK保证同owner，private来源UUID不FK原work_records避免阻断来源删除 |
| `community_post_revisions` | UUID id、post_id/owner_id、revision_no、type/business_date/title/summary/body快照、private source_selection快照、request_id、request_fingerprint、result_version、published_at；post/revision_no与post/request_id唯一；不可变历史 |
| `community_public_profiles` | owner_id唯一、nickname、bio、version；独立公开投影，默认“未设置昵称”，无username；本人可在我的发布入口编辑 |
| `community_moderation_audit` | post_id、actor_id、动作HIDE、非空reason、发生时刻；下架与审计同事务 |

**storage子任务拥有 V18：**

| 表 | 关键字段/约束 |
|---|---|
| `community_attachments` | UUID id、post_id/owner_id、request_id、object_key（服务端生成唯一且每FAILED重试新行/key）、original_filename安全展示值、verified_content_type、actual_size、sha256、state `UPLOADING/READY/FAILED/DELETING/DELETE_FAILED/DELETED`、attempt_token、reservation_expires_at、result_version、safe_failure_code、created_at/updated_at；不存bucket/endpoint/provider URL/签名 |
| `community_draft_attachments` | post_id/owner_id、attachment_id唯一集合、position；只允许同post同owner，不跨帖素材库 |
| `community_revision_attachments` | revision_id、post_id/owner_id、attachment_id唯一集合、position；发布版本不可变引用，历史一直保留 |

附加索引：可见发布按 `first_published_at DESC,id DESC`；本人列表按状态/更新时间；附件requestId按post唯一；FK/restrict保护历史。公开类型/业务日期/摘要取current revision，save/publish中的type必须等于post创建类型，不允许原地强改现有文章。编辑器切另一类型按R2确认“保存并新建”：先明确保存旧私有稿成功，再打开新类型本地编辑器（DAILY转素材选择），取消/失败继续旧稿；不是新增旧类型映射或自动保存。博客摘要输入按R2保留，空摘要可从公开正文派生卡片文本。先创建posts/revisions再加 `(post_id,owner_id,current_revision_id)` 到对应revision的复合FK，避免建表循环且禁止跨owner/post pointer。表名/字段/类型/API/hash以本design为唯一已选合同，research候选别名不用再并行实现。

## 保存、发布与可见性事务

状态与草稿分开：新建DRAFT；保存只改私有draft并增version；DRAFT/PUBLISHED/WITHDRAWN允许显式发布；撤回为WITHDRAWN，ADMIN下架为HIDDEN且没有恢复接口。HIDDEN作者可以维护私有文本但不能发布；读者任何读取都要求 `status=PUBLISHED`且current revision匹配。撤回/下架保留pointer和历史用于作者审计，不以删除对象实现。

发布请求 `{version,requestId,visibility:'MEMBERS',type,title,summary?,bodyMarkdown,businessDate?,attachmentIds}`携带作者当前明确预览确认的输入，不先隐式保存、不发布旧server draft。短事务锁owner post → 查同post/requestId已有结果（规范化整个提交payload生成fingerprint，相同返回当次revision，异载409；不根据后来活draft重算hash）→ 校验version/非HIDDEN → 校验并锁所选附件owner/READY/额度、释放被替换draft引用 → 同事务保存该确认输入与新draft集合、插immutable revision与唯一引用 → 切current pointer/status，首次first_published_at只写一次，version递增且将当次result_version写到revision。任何失败整体回滚，作者未保存输入保留。存储子任务必须将引用校验/写入接入这个同一事务，不能after-commit另绑文件。重放返回持久化当次result_version而非当前posts.version/从revision_no推算，且不重新应用状态，所以撤回/下架以后重试旧发布也不恢复。

过期草稿保存/撤回返回409且不覆盖，他人post先404再谈冲突；前端保留本地输入，重新获取版本后明确重试，不静默合并。重复发布响应不能倒退已更新的本地草稿。ADMIN hide携带公开 `expectedRevisionId`与reason（不读取作者私有aggregate version），检查角色后锁post、确认当前可见revision、写reason/actor审计、设HIDDEN并增version；作者没有role/owner旁路。

今日素材使用当前WorkRecordMapper日展示投影：同owner/task/day完成+focus已合并，raw ledger依旧供私有统计/报告。`GET sources/page`完整分页查询，选择用ID集合跨页保持，不只对当页切片。切日期清空当日选择并保护未保存。生成请求明确选择source IDs及公开字段（默认content；项目/具体时刻不默认公开）和独立includeFocus；后端重查本人owner+Asia/Shanghai日期范围并验证完整选择，复制公开投影到草稿正文，不让读者看private IDs/source JSON。合并任务完成计一次，专注合计另列，非任务完成次数；同业务日期不设唯一post约束。

## 固定 API 与权限

所有路径继续ApiSessionFilter与CSRF。新建/上传201，保存/发布/撤回200，已登录不可见资源404，匿名401，非法CSRF/USER管理403，版本/状态/幂等/集合额度冲突409，格式400、单文件过大413、存储暂不可用503。ProblemDetail可加稳定 `code` 区分附件失败，不泄漏SQL/对象凭据。

| API | 合同 |
|---|---|
| `GET /api/community/posts/page?type=&authorId=&page=0&size=5` | 成员列表，PageResponse，仅当前发布投影，不含private关联，first_published_at排序 |
| `GET /api/community/posts/{postId}` | 成员详情，仅当前可见revision，title/body/安全author/附件公开信息 |
| `GET /api/community/authors/{authorId}` | 成员可读公开profile，内容列表复用authorId过滤；无AccountResponse |
| `GET/PUT /api/me/community/profile` | 本人公开资料维护，PUT带version；长度按现行验证风格确定 |
| `GET /api/me/posts/page?status=&page=0&size=5` | 本人稿件列表，private DTO |
| `POST /api/me/posts` | `{type,businessDate?,title?,summary?,bodyMarkdown?}`，owner由身份，产生draft、version |
| `GET/PUT /api/me/posts/{postId}` | 读本人draft，保存 `{version,type,businessDate?,title,summary?,bodyMarkdown,attachmentIds}`；附件集合在V18后同事务接入 |
| `POST /api/me/posts/{postId}/publish` | 上述当前预览payload+requestId/version/MEMBERS合同；返回发布revision/result version |
| `POST /api/me/posts/{postId}/withdraw` | 本人version控制，返回新状态/version |
| `GET /api/me/community/sources/page?date=&page=0&size=5` | 本人完整指定日展示投影；私有候选DTO |
| `POST /api/me/community/share-drafts` | date+所选ID/公开字段+includeFocus，重查owner/date，产生DAILY稿，不产生公开对象 |
| `POST /api/admin/community/posts/{postId}/hide` | ADMIN、`{reason,expectedRevisionId}`，没有restore |
| `POST /api/me/posts/{postId}/attachments` | 单文件multipart，expectedVersion+requestId，逐次队列上传；响应安全附件信息和aggregate version |
| `GET /api/me/posts/{postId}/attachments/{attachmentId}` | 作者就绪附件stream；同owner/post，草稿/保留历史可读 |
| `GET /api/community/posts/{postId}/attachments/{attachmentId}` | 成员当前可见revision包含ID且READY才能stream |
| `POST /api/me/posts/{postId}/attachments/cleanup` | 作者显式清理真正孤立对象，返回逐ID删除/失败结果，绝不清理他人或仍引用对象 |
| `POST /api/me/posts/{postId}/attachments/recover` | 作者显式释放逾期upload reservation、失败key仍可追踪，不作为对象按日自动删除 |

没有面向读者历史端点，也不新增历史恢复UI。PageQueries只包目标select，DTO转换后不意外分页；UI列表默认5，候选选择需要遍历任意页。附件不增通用按objectKey访问API。

## R2页面/状态覆盖清单

| 页面/状态 | 参考证据与正式验证 |
|---|---|
| 广场、三类型过滤、继续草稿卡、empty/loading/retry | PNG01/11及app.js68～76；true API数据/失败布局 |
| 独立三类型选择compose、今日素材empty/多页/日期 | PNG02/12及app.js77～81；canonical new/sources，无旧原型路由别名 |
| 新稿/已发布修改稿、BLOG摘要、附件empty/pending/failed/retry/remove、type-change | PNG03/10/13/14及app.js82～91/160～162；所有输入真实持久化 |
| 发布预览desktop/mobile唯一确认区 | PNG04及app.js110～114/styles7～9；精确当前local payload |
| 详情、gallery/lightbox、PDF-MDdownload/error、链接copy fallback | PNG05/15/16及app.js93/110～114/165～166；真实权限与Blob |
| 我的发布all/draft/published/withdrawn/HIDDEN提示、撤回确认 | PNG06/07/17及app.js115/161；真实状态/audit无恢复 |
| 作者主页及empty、公开profile本人编辑 | PNG08及app.js116；仅public昵称/简介/可见帖子 |
| 真实login gate、unavailable/401/404、dirty三动作 | PNG09及app.js117～118/160；真实LoginPage/身份/允许目标 |

没有独立PNG的上述既有状态必须以R2源码对照正式DOM/截图，不能因17张PNG已对齐就遗漏。fixture只证明状态布局，不证明业务已实现。

## RustFS 及后续阿里云

应用只依赖 `ObjectStorage`：`put(key,validatedFile,size,contentType,sha256)`、`open(key)`返回可关闭stream及长度、`delete(key)`；供应商异常统一翻译。当前只有S3 RustFS adapter，服务器私有访问。配置为当前provider、endpoint、region、bucket、access key/secret、path-style、连接/读取/总调用超时；秘密只从后端env读，前端VITE无凭据。固定AWS SDK BOM/s3 2.55.10、PDFBox3.0.8、TwelveMonkeys imageio-webp3.15.2，不引入Tika；前端react-markdown10.1.0。官方来源和当前未安装/未构建限制见research/storage.md、frontend.md。应用配置默认当前RustFS路径可用，配置错误明确报错，不静默转本地文件/公开bucket。

WSL Ubuntu2 Docker已运行，现有PostgreSQL/Redis健康；RustFS容器/镜像尚不存在。执行阶段新增RustFS持久卷和私有bucket初始化，固定`rustfs/rustfs:1.0.0`稳定版（registry manifest已查询成功，尚未下载镜像层）；Windows native backend用WSL可达loopback endpoint，compose backend用service DNS，使用同一组`WORKBENCH_STORAGE_*`配置。初始化管理员身份与后端限于指定bucket/prefix Put/Get/Delete的应用身份分离；业务API不自动建桶/改Policy。控制台/API只暴露本地需要的端口，不启匿名策略/CORS直传；真实无签名GET对象必须403，不能只凭声明ACL判定。不改现有服务/数据卷，不用down -v。具体Env键、digest与初始化验证见storage研究。

未来申请云资源后新增独立AliyunObjectStorage（或验证过的OSS S3 adapter），映射同一接口、保留object keys，业务DTO/URL/表不变；测试后仅切应用provider/endpoint/bucket/身份配置。SDK/Endpoint不同不能声称直接替换；RustFS存量对象也不会因改配置自动出现到云桶，实际切换时须同key复制并核对size/sha后切配置。本轮不实现云adapter、复制工具、兼容迁移或云实测；此扩展与数据搬迁边界已写入规划，避免把“无缝”误写成“自动搬数据”。

## 文件管线、额度、读取与清理

单文件multipart，固定运输上限Servlet file=22,020,096字节（21MiB）、request及Nginx=23,068,672字节（22MiB），业务精确逐字节执行5/20/1MiB。只对附件上传路径调整Nginx/body limit与timeout，其余1m保持。临时文件有界、失败/finally删除、不用整个附件byte[]；Security Filter先session/CSRF，multipart采用lazy解析或等效机制区分Servlet有界接收和owner验证后的应用暂存，不能把Controller方法体owner检查冒充所有接收前已授权。编码/解析失败不向存储写就绪对象。

扩展名与真实结构一致：JPEG/PNG/WebP解析验证（包含真正WebP解码支持、格式与扩展一致、有界subsampling验证，不新增像素配额）；PDF解析结构而非只看%PDF头或仅Loader宽松修复成功；MD为严格UTF-8文本，可BOM，无NUL/二进制控制字符，不宣称有专有magic或能拒绝全部改名文本。动画WebP和加密PDF不先列为新禁止类型，验证结构而不要求读取私有文档内容；若现有parser能力确实需要额外拒绝合法子类型，须提出具体影响确认，不能静默缩小白名单。解析资源边界用实际有界I/O/SDK调用，不以不可中断Future timeout假称decoder已停止。清理filename的路径/控制字符，header编码安全；所有类型不转换/送AI，PDF/MD下载 `Content-Disposition: attachment`，图片inline且nosniff。具体固定SDK/解析器版本与官方资料见storage研究。

上传无长DB事务：验证暂存文件 → 短事务锁post先owner，再查同post/requestId+sha/长度已有结果，再检查expectedVersion/状态；仅新请求对真实字节/数量预留并建立draft pending引用、version递增 → 事务外put → 短事务按UPLOADING+持久attempt_token+仍有效reservation条件确认READY。跨tab保存/取消/cleanup撤销pending后，晚到put不能无条件READY或重绑draft。失败释放预留/pending并记录新aggregate version；FAILED终态同requestId重放返回原失败，用户重试用新requestId/新attachmentId/key，旧FAILED行永远保留旧key用于补偿（绝不更新object_key丢掉旧孤立对象）。添加/移除draft引用需要aggregate version，前端单稿写操作/上传串行，跨tab过期409；重放不能把旧输入授予当前新version。文本编辑不被上传自动保存/清空。

每attempt持久字段含token、上传deadline、result_version、状态更新时间、脱敏失败类别。SDK连接/attempt/总调用有界且短于上传deadline；进程崩溃后由作者显式 `POST /api/me/posts/{postId}/attachments/recover` 检查已过期attempt，按锁/token标FAILED并释放占位，保留key待清理。有效deadline内不得当孤立对象删除；晚到I/O只更新失效attempt的补偿记录，不改稿件引用/READY。recover是上传恢复，无自动清理天数/后台新队列，上传/重试UI明确调用并显示结果。cleanup保留删除tombstone/key，可重复核查不确定晚到put，不能因一次delete就销毁唯一可追踪记录。

单稿当前集合与上传预留共10个/50MiB，actual size精确；相同ID只计一次，attachmentIds保留首次出现顺序去重，fingerprint包含该固定顺序。历史只引用且不计当前额度。发布只接受明确所选已就绪引用，pending/FAILED引用409；draft保存移除active pending时同事务撤销token/标FAILED释放额度，晚到finalize不可复活。失败ProblemDetail可带`currentVersion`供队列重新获取版本，不可覆盖新输入或把旧ack当当前授权token。历史旧附件不原地覆盖。安全clean锁相同post，核查draft与所有保留revision引用及有效reservation后标DELETING，事务外delete，成功DELETED、失败DELETE_FAILED可重试；删除态禁止新绑定/发布。所有binding/cleanup固定post→attachment锁顺序，避免查无引用后发生新引用的竞争。没有自动清理天数或总账号配额。

所有读/HEAD必须先验证会话、owner或 `PUBLISHED/current revision/ref`，再打开storage stream和返回任何元数据；鉴权错误连HEAD也不能泄漏长度/文件名。正文与附件成功/失败都设 `Cache-Control: no-store, private`、`X-Content-Type-Options: nosniff`；不使用共享cache/ETag/304。首版不支持Range，`Accept-Ranges:none`，鉴权通过后忽略Range/条件头返回完整200，未授权仍先401/404；不增加206旁路。storage open在响应提交前完成，missing/unavailable转安全错误；流开始后故障不能伪装JSON成功。异步stream只捕获已授权不可变描述，不依赖工作线程SecurityContext；客户端断开关闭/abort SDK输入流、不302到云，无前端签名URL。

## 交付次序、风险与恢复边界

发布子任务(V17) → 存储子任务(V18+发布事务附件集成) → UI与全量端到端；UI可在合同不变时并行搭骨架，最终验收须等两后端交付。父任务仅规划/整合审查，不作为产品实现目标。

执行开工前核对未提交归属、创建 `codex/community-oss`分支且不stash/reset；实际变动纳入验证记录。任何旧版回退/兼容/flag未获批，不能借恢复条款新增。失败以修复当前实现与保留已落库/对象证据为先，不自动降schema、不删表/卷/有效对象、不承诺旧二进制运行新schema。真正部署/云操作/commit/push不由本规划自动授权。

已知非阻塞限制：新SDK/解析器/Markdown依赖与RustFS镜像需在线取得；文件流带宽/磁盘占用未压测；阿里云实测延期；已下载副本不能追回。执行阶段任何影响AC的真实问题必须修复或作为阻塞明确报告，不能悄悄降低验收；常规技术问题自行解决后在最后汇总。
