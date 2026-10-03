# Research: 站内发布、私有草稿、作者资料与今日素材后端

- Query: 对照 R3 FR/AC，核对真实认证、账号、记录/任务/专注/报告实现，固定发布状态、数据库、API、幂等/并发和测试建议。
- Scope: internal；附件存储适配及文件传输由另一研究主题负责，本文件只规定它与发布事务的接口边界。
- Date: 2026-10-03
- Task: `.trellis/tasks/10-03-community-oss`；只写本研究文件，未改产品代码、运行交接脚本、执行迁移或测试。

## Findings

### 1. 已批准合同及核对范围

- 已读交接包 `HANDOFF.md`、`requirements.md`、`solution.md`、`acceptance.md`。R3 的 DEC-11/12/13/17 已确认素材默认不选、跨页选择、手动私有保存、显式发布更新、首次发布时间保持、历史保留、公开昵称/简介、ADMIN 下架及孤立对象清理；不能沿用原型说明中的历史“待确认”文字重新阻塞。
- `requirements.md:51`：B 和 ADMIN 对 A 私有资源均返回 404；公开 DTO 不带私有来源关联和账号敏感字段。
- `requirements.md:59`：同日多篇；只复制选中且确认的公开字段，项目和发生时间默认不复制；专注另行勾选，不能算作完成。
- `requirements.md:63,67,71`：每次发布形成保留历史；只有显式发布切当前版本；撤回/下架后新读返回 404；逻辑发布重试不能重复，旧版本写入不能覆盖。
- `HANDOFF.md:54-55`：作者只公开昵称/简介；ADMIN 下架必须理由/审计、作者不能绕过、首版无恢复。
- `solution.md:63,65-67,99-101` 是待本地固定的实现候选，不是已经存在的表/API。本文件给出确定推荐，正式规划可据此冻结。
- 本地最新已有迁移为 `V16__continuous_focus_time.sql`。父规划拟 publishing 使用 V17，storage 使用 V18；实施前再确认编号未被并行工作占用，V1–V16 不改。

### 2. Files found

| 文件 | 真实用途 |
|---|---|
| `backend/pom.xml` | Java 17、Spring Boot 3.5.16、MyBatis 3.0.5、PageHelper 2.1.1、真实 PostgreSQL/Flyway/Spring Session Redis/Spring Security |
| `security/ApiSecurity.java` | Cookie/CSRF 链、ADMIN 路径鉴权、其余请求认证 |
| `security/ApiSessionFilter.java`、`SessionAccess.java`、`SessionActivity.java` | 活会话与独立明确活动期限，基础设施故障失败关闭 |
| `security/CurrentUser.java` | 服务端认证 owner 获取 |
| `controller/AuthController.java`、`AdminAccountController.java` | 现有登录/账号管理合同 |
| `dto/account/AccountResponse.java`、`mapper/AccountMapper.java`、`resources/mapper/AccountMapper.xml` | 账号响应含登录 username，不能作为公开作者 DTO |
| `controller/WorkRecordController.java`、`service/impl/WorkRecordServiceImpl.java` | 原始记录数组、日展示分页、详情与手工记录维护 |
| `mapper/WorkRecordMapper.java`、`resources/mapper/WorkRecordMapper.xml` | owner/day/active 原始事实与日展示合并 SQL |
| `dto/record/WorkRecordResponse.java`、`entity/record/WorkRecordRow.java` | 私有记录返回项目、任务、会话、时间、完成结果/专注字段 |
| `service/impl/FocusServiceImpl.java`、`mapper/FocusStore.java`、`resources/mapper/FocusStore.xml` | 按日专注结算、关联进展事务与专注账本 |
| `service/impl/TaskServiceImpl.java`、`resources/mapper/TaskMapper.xml` | 条件版本更新、完成/重开/完成结果、自动记录与事件事务 |
| `service/impl/ReportPersistenceServiceImpl.java`、`resources/mapper/ReportMapper.xml` | 独立原始记录/待办候选与私有冻结来源 |
| `events/WorkbenchEventHub.java`、`WorkbenchStompGuard.java`、`WorkbenchEvent.java` | after-commit owner 私有刷新通知与严格订阅边界 |
| `common/PageQueries.java`、`PageResponse.java` | 零基分页、PageHelper 作用域清理 |
| `resources/db/migration/V13__accounts.sql`、`V14__business_owners.sql`、`V15__focus_routines.sql`、`V16__continuous_focus_time.sql` | 账号、same-owner 约束与专注真实迁移基线 |
| `exception/ApiExceptionHandler.java` | 安全 ProblemDetail 翻译 |
| `src/test/resources/application-test.yml`、`config/IsolatedProfileSchemaGuard.java` | 真实 PostgreSQL 专用 schema 配置与拒绝非隔离 schema |
| `src/test/java/.../DailyRecordPresentationIntegrationTest.java` | 跨页合并、raw/report 保留、日期/owner/task、重开回归 |
| `src/test/java/.../BusinessOwnerIsolationIntegrationTest.java`、`AuthenticationIntegrationTest.java` | A/B/ADMIN 私有隔离、真实 HTTP/Redis、CSRF、会话失效、无自动续期 |
| `src/test/java/.../TaskCompletionConsistencyIntegrationTest.java`、`FocusIntegrationTest.java`、`FocusHttpIntegrationTest.java` | 并发、事务回滚、版本、进展与来源不退化 |
| `src/test/java/.../FocusUpgradeIntegrationTest.java`、`ReportMigrationIntegrationTest.java` | 真实 Flyway 升级、历史快照与旧迁移不变 |

本表 Java 文件省略的统一前缀是 `backend/src/main/java/com/aiworkbench/`；resources 为 `backend/src/main/resources/`；测试在 `backend/` 下。精确证据以下均使用仓库相对路径。

### 3. Code patterns：认证与账户边界

- `backend/src/main/java/com/aiworkbench/security/ApiSecurity.java:56-79` 已配置 `X-XSRF-TOKEN` Cookie CSRF，`/api/admin/**` 要 ADMIN，非登录/健康白名单均 authenticated。新增所有社区/作者/附件 GET 沿用该链；不增加 permitAll 或第二套认证。
- `backend/src/main/java/com/aiworkbench/security/ApiSessionFilter.java:32-61` 仅跳过 auth login/csrf、actuator；失效返回 401，Redis/数据库认证异常返回安全 503。社区读取不调用 activity、不把 503/403/404 变成退出。
- `backend/src/main/java/com/aiworkbench/security/CurrentUser.java:12-22` 的 `requireId()` 是作者命令唯一 owner 来源，请求 DTO 不接受 userId/authorId。
- `backend/src/main/java/com/aiworkbench/controller/AuthController.java:61-87` 的 me 只返回当前账号；只有明确 activity POST 续期。`AdminAccountController.java:20-86` 管理账号，不是公开作者接口，也不是私有业务旁路。
- `backend/src/main/java/com/aiworkbench/dto/account/AccountResponse.java:7-9` 暴露登录 username/role/enabled/createdAt；`backend/src/main/resources/mapper/AccountMapper.xml:15-25` 还会读 password_hash/auth_version。社区作者使用独立表、独立 mapper projection 与 DTO，不能从 AccountResponse 序列化后删几个字段。
- `backend/src/main/resources/db/migration/V13__accounts.sql:1-12` 当前没有公开昵称/简介。规划确定缺省显示 **“未设置昵称”**，简介空；本人可编辑。无登录 username 回退，无真实账号资料自动迁移。

### 4. Code patterns：本地素材已经不同于冻结快照

- `backend/src/main/java/com/aiworkbench/service/impl/WorkRecordServiceImpl.java:48-63` 按 `workbench.zone-id`（Asia/Shanghai 默认）解释日期，范围 `[startOfDay,nextStartOfDay)`，owner 来自 CurrentUser。`list()` 调 raw `findBetween`，`page()` 调日展示 `findPageBetween`。
- `backend/src/main/resources/mapper/WorkRecordMapper.xml:43-54` raw detail/list 保留所有 active 原始行；`55-81` 在 PageHelper 之前，把同 owner、同任务、请求日内专注聚合进 active TASK_COMPLETION，再排除被吸收专注。合并行仍是 TASK_COMPLETION，保留 completionResult，focusMs/breakMs 只是聚合；不能用“focusMs 非空”判定它不是任务完成。
- `backend/src/test/java/com/aiworkbench/service/impl/DailyRecordPresentationIntegrationTest.java:61-104` 明确证明 raw 8 行、日展示 6 项、size=5 两页，完成项目携带 90000/15000 毫秒，raw 完成 detail 仍无 timing；`107-140` 隔离 owner/day/task；`142-185` 进展保存后单次完成、结果修改、重开后专注独立恢复且 raw/report 事实不变。
- `backend/src/main/java/com/aiworkbench/service/impl/FocusServiceImpl.java:214-240` 结束只结算按保存时区分日的专注事实；`243-255` 保存已结束关联进展会在同一事务完成任务或同步已有完成结果。今日素材和社区不改该已实现语义，也不能通过读取候选、发布、进入广场触发 progress/complete/fill-today。
- `backend/src/main/resources/mapper/ReportMapper.xml:70-90` report 候选仍是独立 work_records + 待办，包括 notes、project、occurredAt、sessionId、taskId、breakMs、segmentStart/End；`136-151` report_sources 以所属 report.user_id 授权。**不能复用 report source response 作为社区正文或读者 DTO，也不能把待完成任务的私有 notes 自动作为今日成果。**
- `backend/src/main/java/com/aiworkbench/dto/record/WorkRecordResponse.java:7-24` 也是私有 DTO，不可直接放入发布 JSON。
- `backend/src/main/resources/mapper/WorkRecordMapper.xml:82-88` 手工来源可以更新及物理删除。新发布历史不得使用对 work_records 的 RESTRICT/CASCADE FK 约束来源删除；只在私有来源记录保存当时 UUID 与已确认摘录，历史正文自己持久化。

#### 固定今日素材合同

1. 候选列表复用现有日展示投影和同样的 owner/date/active 条件；默认 size=5，PageQueries 物理分页。完整候选集合意味着所有符合项都可通过分页到达，不能只读取第一页再生成。
2. 新私有 `CommunityMaterialResponse` 返回 `id,source,content,completionResult,progress,focusMs,projectName,occurredAt` 中选择页需要的字段；其中项目/时间明确私有。新公开 DTO 不使用该类型。
3. 生成命令提交指定 date、跨页记录 ID 和明确 `fields`，`includeFocus` 默认 false；默认勾选一条素材只选 `CONTENT`。允许字段仅 `CONTENT|COMPLETION_RESULT|PROGRESS`；后二者须额外确认，项目、发生时间、notes、projectId、taskId、sessionId 永不自动复制。其他内容作者可以在独立稿件中主动编辑。
4. 后端一次 unpaged 重新加载所有所选候选，逐项证明 owner、业务日、active、当前日展示资格，拒绝不存在/他人 ID（404），拒绝已不符合当前投影的本人成果（409），整体无局部写入。不能根据浏览器提交的项目、内容或时长建立可信来源。
5. 在同一短事务生成并保存私有 DAILY 草稿，返回可编辑正文；不得同时公开。后续草稿编辑/发布不会再次查询原始源来自动改写文本。
6. 可从 `WorkRecordMapper` 的日展示 CTE 提取共享 SQL fragment，供候选分页与 selectedIds 的 unpaged 查询复用；对应来源和可选 timing 的底层 raw UUID 集合作为私有追踪，以去重计算。不要复制一份逐渐漂移的合并算法，不改变现有 raw/report 查询。
7. 选中合并完成项同时包含其同日已吸收专注的 timing，但 completionResult 只输出一次；选中独立专注项不增加任务完成数。只有 includeFocus=true 才把**所选项** raw focus UUID 并集的 focusMs 合计加入公开正文；不用 focus/today 的整日 totals，不把 merged 与 raw 再加一次。字段为空则不产生空文本。
8. 私有来源记录冻结确认字段/摘录及底层来源 UUID，稿件和每次发布正文冻结成独立文本。原始记录更新/删除/重开、草稿生成之后数据增加都不更改它；source IDs/selection 不从读者接口发出。
9. 日期切换清掉前端选择与 includeFocus；同日多篇，不加 author/date 唯一索引。原型 `src/app.js:79-80,173,195-198` 是选中素材合计及标题正文生成的布局/交互参考，不能照搬其 `r.minutes` 分类，它不能表示本地 merged completion。

### 5. 确定推荐的数据合同（V17 publishing，V18 storage）

| 新表 | 推荐字段与约束 |
|---|---|
| `community_profiles` | `user_id UUID PK FK user_accounts RESTRICT, nickname TEXT NULL, bio TEXT NOT NULL DEFAULT '', version BIGINT>=0 DEFAULT 0, created_at,updated_at TIMESTAMPTZ`。昵称 null 显示未设置昵称；本人可设/清空，昵称不唯一，无头像上传扩展。 |
| `posts` | `id UUID PK,user_id UUID FK user_accounts RESTRICT,type DAILY|NOTE|BLOG,status DRAFT|PUBLISHED|WITHDRAWN|HIDDEN,version BIGINT>=0,current_revision_id UUID NULL,first_published_at TIMESTAMPTZ NULL,created_at,updated_at,create_request_id UUID,create_request_hash CHAR(64)`；`UNIQUE(user_id,id)`、`UNIQUE(user_id,create_request_id)`；firstPublishedAt 只在第一次成功发布设置。 |
| `post_drafts` | `(user_id,post_id)` same-owner FK posts，单稿一个可变行；`title,summary,body_markdown,business_date DATE NULL,saved_at`。draft 不存对象 URL、源业务完整快照或可执行 HTML。 |
| `post_revisions` | `id UUID PK,user_id,post_id,revision_no INT>0,title,summary,body_markdown,business_date,published_at`；`UNIQUE(post_id,revision_no)` 与 `UNIQUE(user_id,post_id,id)`；同 owner FK posts。只插入，业务代码无 update/delete。类型固定在 posts，DAILY 才有日期。 |
| `post_draft_sources` | `user_id,post_id,record_id UUID,selection_order,selected_fields,public_excerpt,raw_focus_record_ids`；same-owner FK 只约束 posts，record_id 不 FK 原始 work_records。私有仅冻结选择并支持合法去重，无旧源 rehydrate/fallback。 |
| `post_revision_sources` | 与 revision same-owner FK，冻结该次私有来源选择；reader mapper 无此表 join。历史保留，不给读者源 ID。 |
| `post_commands` | `user_id,post_id,kind DRAFT_SAVE|PUBLISH|WITHDRAW,request_id UUID,request_hash CHAR(64),result_version,result_revision_id NULL,result_saved_at NULL,created_at`；`UNIQUE(user_id,post_id,kind,request_id)`，same-owner FK post。记录成功命令结果用于响应丢失重试，不存异常/失败成功收据。 |
| `post_moderation_audit` | `id UUID,post_id,author_user_id,actor_user_id UUID,request_id UUID,request_hash,reason,previous_status,result_status HIDDEN,published_revision_id,occurred_at`；same-owner FK post，actor FK account；`UNIQUE(actor_user_id,post_id,request_id)`。记录成功下架不可变，不向普通读者公开内部理由/actor。 |

- posts.current_revision_id 使用 `(user_id,id,current_revision_id)` → revisions `(user_id,post_id,id)` 的 composite FK；建表后追加该 FK，避免循环创建。`status=PUBLISHED` 必须有 current revision 和 firstPublishedAt，DRAFT 则无 current revision；WITHDRAWN/HIDDEN 保留历史指针但 reader 不读取。
- 当前读者查询统一 `posts.status='PUBLISHED' JOIN post_revisions ON current_revision_id=id AND owner/post equal`；public profile 单独选择 nickname/bio。排序 `first_published_at DESC,posts.id DESC`，更新不重新置顶；profile 的公开帖子同样查询。
- API 文本边界固定建议：title 200、summary 500、bodyMarkdown 100000、nickname 40、bio 500 个 Java String 字符；draft 可空以保留未完成输入，发布要求 body 非空、DAILY/BLOG title 非空，NOTE title 可空。超长 400；这些是新的技术上限，非当前已存在事实。
- V18 新附件对象及 `post_draft_attachments` / `post_revision_attachments` 引用表，same-owner/post 约束；每稿自己的对象不跨多个帖子复用。附件就绪校验/配额/引用插入必须纳入发布同一 PostgreSQL 事务，引用历史继续保留。V17 的文字流程可先独立验证，V18 后执行全部集成；不是运行时默认关闭门禁。

#### 状态与并发

- 新建 → DRAFT；私有 save 不改变 status/currentRevision/firstPublishedAt。
- publish 允许 DRAFT→PUBLISHED、PUBLISHED→PUBLISHED（新 immutable revision）、WITHDRAWN→PUBLISHED；恢复已撤回帖仍保留首次发布时间。HIDDEN 禁止发布、撤回或任意请求修改 lifecycle；没有作者/管理员恢复 API。作者读取自己的 retained draft/history 仍需 owner；下架内容不进入公共详情。
- withdrawal 只改变 PUBLISHED→WITHDRAWN 并增加 aggregate version；同逻辑 requestId 重放只返回已存成功结果。DRAFT 操作 409；HIDDEN 409。不新增删除已发布/历史接口。
- ADMIN hide 只针对当前可见发布，锁聚合行，将状态 HIDDEN、version+1、审计一次写入；已登录普通 USER 是 403。ADMIN 仍不能查看私有 draft/records/sources。
- 每个新命令先 owner-scoped 查并锁 `posts FOR UPDATE`，查成功 receipt；匹配 receipt 立即返回当次结果且不重做，异 payload 409；无 receipt 才检查 `version`，条件 update `WHERE user_id,id,version`、version+1，零行 409。
- requestId 为客户端 UUID，范围是 owner+post+命令；hash 由服务端对规范化请求 DTO SHA-256，含 expectedVersion，集合项确定排序（来源显示顺序保留）。publish hash 只对请求 `{version,visibility}` 计算，**不对重试时的活草稿再计算 hash**，否则后来修改会使同一提交误冲突。create_request_id owner 全局唯一，生成素材和普通新建的 kind/hash 同时纳入 fingerprint。
- SAVE/PUBLISH/WITHDRAW 的成功收据与其业务变化同一事务；失败不留成功收据。save 返回简短 `{postId,version,savedAt}`，publish 返回 `{postId,version,revisionId,revisionNo,publishedAt}`；重放返回当次结果，不把它当作当前公开状态。客户端遇到晚到旧 version 响应须重新 GET 当前作者状态，不覆盖较新输入。
- publish 读取**已保存草稿**；有未保存输入时 UI 先 await 显式保存，再用返回 version 发布，页面仍标明保存≠发布。事务中验证草稿、membership 可见性、附件 owner/ready/current限额，插 revision+引用、切 current pointer、写 receipt，一次提交；任一失败全回滚。只发布正文中的附件 URI 所属/就绪集合，reader 无云 URL。
- ADMIN hide 请求用公开 `expectedRevisionId` 而不是作者聚合 version：私有 save 不应泄漏给管理员，也不应把下架无故变成冲突。锁行检查当前 status/revision，revision 已变化 409；reason 非空且最大1000字符。同 actor/post/requestId/hash 审计记录即成功 receipt，后续重试不恢复或再写审计。
- publish 与 hide 使用同一 posts 行锁；先 publish 则 hide 看当前 revision，先 hide 则 publish 409，任何 interleaving 均不会 HIDDEN→PUBLISHED。withdraw 与附件 reader 同样按当前 committed status/pointer 判定，新请求不读旧版本。

#### 附件与正文聚合版本的简化方案

- 上传/校验/失败/取消的状态单独存在 attachments，返回附件自身状态/版本；预留额度和变 READY **不增加 posts.version**，避免作者同时写正文因上传完成而遭无意义版本冲突，也不自动保存正文。
- 所有 reserve/finalize/release、draft save 和 publish 操作都先锁同一 owner-scoped posts 行，再按确定顺序锁该稿附件；这个行锁只序列化短数据库阶段，网络传输/解析不在其中。
- provisional 额度为当前有效 draft READY ID 集合与未释放 unbound reservation 集合的并集；同ID只计一次，保留历史与纯旧发布引用不计当前稿件额度。预留按已受限实际大小/保守字节上界，finalize用实际大小，超限不能READY；移除/失败释放预留但不删除历史对象。
- 正文 draft save 用显式、去重 attachmentIds+expected posts.version，只接收同稿同owner READY对象，在同事务替换draft refs并递增posts.version；draft变化不会影响current revision refs。成功保存后“另一个上传刚完成”不会静默加进已保存集合。
- publish只取已保存draft refs，不把unbound READY对象隐式发布；同posts锁下校验全部READY/owner/实际额度并写revision refs/切pointer。未选上传文件可以留在私有附件托盘、释放其占位，但不作为发布引用。
- 存储实现要固定一次锁顺序用于reserve/save/publish/cleanup；cleanup依无draft/current/历史ref且无有效reservation，不能与绑定交错误删。此方案无独立draft上传锁version、无自动正文保存、无功能门禁。

### 6. 确定推荐 API/DTO

所有 API 采用现有 Cookie、CSRF、ProblemDetail；本表为**新合同建议**，不是现有端点。page 为零基，size 5/10/20/50，默认5。

| 端点 | DTO / 行为 | 权限 |
|---|---|---|
| `GET /api/community/posts/page?type=&authorId=&page=0&size=5` | `PageResponse<PostCard>`；只当前 PUBLISHED，卡片输出 id/type/DAILY date/title/summary或显式公开正文摘要/firstPublishedAt/revisionNo/attachment展示概要/publicAuthor | 已登录成员 |
| `GET /api/community/posts/{id}` | `PostPublishedResponse {id,type,businessDate,title,summary,bodyMarkdown,firstPublishedAt,publishedAt,revisionId,revisionNo,author:{id,nickname,bio},attachments}` | 已登录；不可见404；无 drafts/source IDs/account fields |
| `GET /api/community/authors/{id}` | `PublicAuthorResponse {id,nickname,bio}`；资料缺省未设置昵称/空简介，public posts 用上方 authorId分页 | 已登录；账号不存在404，不返回 username/role/online/私有计数 |
| `GET /api/me/community-profile` | `{nickname,bio,version}`，缺表行返回 version0 缺省 | 当前 owner |
| `PUT /api/me/community-profile` | `{nickname?,bio,version}`，insert/update条件版本并返回新version | 当前 owner；初次并发仅一人成功，其他409 |
| `GET /api/me/posts/page?status=&type=&page=0&size=5` | 作者管理卡片，draft/status/privateVersion/currentRevisionNo/首次时间/是否未发布修改/最近下架说明 | 当前 owner；ADMIN无旁路 |
| `GET /api/me/posts/{id}` | 作者编辑投影 `{postId,type,status,version,draft,currentPublished?,moderation?}`，draft不自动从源恢复 | 当前 owner；foreign404 |
| `POST /api/me/posts` | `{requestId,type,businessDate?,title?,summary?,bodyMarkdown?}` →201 作者草稿；create同键同payload同post，异payload409 | 当前 owner+CSRF |
| `GET /api/me/share-materials/page?date=YYYY-MM-DD&page=0&size=5` | 私有日展示候选；date必填、无业务副作用 | 当前 owner |
| `POST /api/me/posts/from-materials` | `{requestId,date,selections:[{recordId,fields:[CONTENT,...]}],includeFocus:false}` →201 私有DAILY草稿；后端验证整个selected集合、冻结摘录 | 当前 owner+CSRF；外部ID404，全回滚 |
| `PUT /api/me/posts/{id}/draft` | `{requestId,version,title,summary,bodyMarkdown,businessDate?,attachmentIds}` → `{postId,version,savedAt}`；仅私有保存 | 当前 owner+CSRF；stale409、key异载409 |
| `POST /api/me/posts/{id}/publish` | `{requestId,version,visibility:MEMBERS}` →当次revision结果；必须显式MEMBERS，其他范围400且无写入 | 当前 owner+CSRF；HIDDEN/stale409 |
| `POST /api/me/posts/{id}/withdraw` | `{requestId,version}` → `{postId,version}` | 当前 owner+CSRF |
| `POST /api/admin/community/posts/{id}/hide` | `{requestId,expectedRevisionId,reason}` → `{postId,status:HIDDEN}`，同事务audit | 当前有效 ADMIN+CSRF；USER403，非可见目标404 |

- `attachmentIds` 由 V18 实装为 deduplicated 稿件有效集合；正文重复 URI 不增加数量。V17 不引入无检查的附件 ID 透传完成路径。
- 若不需要 source 私有追踪 UI，本轮不增加新来源浏览/历史恢复页面或版本回滚API；保留数据即可，未来接口须重新明确范围。作者当前编辑响应不序列化全部 retained历史。
- reader 返回须设置 `Cache-Control: no-store`，附件同样授权后才返元数据/字节；不从旧revision地址公开查询。service 按 owner 和状态分别出404/409；必须先 owner查到，才能泄露状态冲突。
- 可新增 `ProblemDetail.code` 便于客户端区别 `POST_VERSION_CONFLICT|POST_HIDDEN|IDEMPOTENCY_KEY_REUSED|SOURCE_CHANGED`，保留现有 detail；404 不带 foreign内容。unexpected SQL/storage 不回原始消息。

### 7. 私有 STOMP 复用边界

- `backend/src/main/java/com/aiworkbench/events/WorkbenchEventHub.java:33-60` 已经 afterCommit 发送纯刷新信号，只发指定 owner 的 live sessions，失败不会让已提交HTTP失败；`WorkbenchEvent.java:6-7` 只有 kind/entityId/state 等信号。
- `backend/src/main/java/com/aiworkbench/events/WorkbenchStompGuard.java:47-55,60-74` 仅允许现有 `/user/queue/workbench-events`，禁止其他订阅与SEND，出站再验会话。
- 可复用为本人稿件/资料 `kind=POST|COMMUNITY_PROFILE` 刷新，ADMIN下架只向作者的 owner queue 推 POST/HIDDEN。事件不含title/body/素材/对象key/理由，不广播各成员，不添加公共 broker destination。
- 广场数据由受保护 HTTP GET 承担；本轮没有点赞评论/社区通知功能，不设计全员广播或离线事件。作者已有私有信号不能当成读者可见数据或撤回授权依据。

### 8. 实现顺序与测试复用点

1. 在 publishing 子任务冻结 DTO/enums、V17、status/public projection、profile fallback、source字段和 receipt contract；新 Controller→Service interface→impl→Mapper/XML，UUID使用项目TypeHandler；migration历史不改。
2. 先完成表约束与 mapper integration：same-owner FK、public current pointer、immutable版本、首发排序、不限制每天一篇、source无源FK、profile不取username。
3. 做私有 create/save/source生成及整集合验证，复用日展示SQL与PageQueries；测试多页候选和合并独立raw去重，恶意 foreign source 全回滚。
4. 做短事务 publish/withdraw/ADMIN hide、成功receipt与聚合版本并发；新读者 list/detail 只当前版本，reader不带source/account字段；after-commit仅本人信号。
5. storage V18 接入附件ready/owner/额度和draft/revision ref，与现有publish同事务；文件不占长DB事务；新附件替换只变私有draft，显式publish才切current引用；retained历史阻止误清理。
6. ui 子任务对接，原型布局代码复用；执行父任务22AC集成及原五工作区/会话/专注/报告回归，不改变任何原始业务owner/date/active条件。

| 现有测试 | 复用/保持的证据 |
|---|---|
| `DailyRecordPresentationIntegrationTest:61,107,142` | 六项跨页、合并源类型/一次结果、timing90秒、另一owner/day/task、reopen恢复raw；新增素材测试使用相同fixture思路，但断言真实生成正文和私有来源集合。 |
| `BusinessOwnerIsolationIntegrationTest:108,157,349,442` | A/B/ADMIN foreign404、真实HTTP pages、安全404 detail、reports owner、禁用不删历史；新增接口纳入同类隔离矩阵。 |
| `AuthenticationIntegrationTest:69,97,156,178,201,403` | realHTTP CSRF、USER403、失效/基础设施失败关闭、后台不续期；社区所有读取/写入保持同合同。 |
| `TaskCompletionConsistencyIntegrationTest:91,119,244,257` | 并发事务/数据库 uniqueness、故障注入全回滚、过期版本；新发布并发用独立事务barrier，不能只在单个@Transactional测试内串行调用。 |
| `FocusHttpIntegrationTest:37,74` | 功能启动即有效，无默认关闭门禁；owner/CSRF/stale合同。 |
| `FocusIntegrationTest:334,379,488` | 按日拆分、不因专注当完成、ADMIN owner隔离、结算回滚。 |
| `FocusUpgradeIntegrationTest:36,89,193` | V15→V16、owned数据升级、冻结旧快照/迁移保持；新V17空库及V16-owned非空库升级应复用真实Flyway思路。 |
| `WorkbenchEventHubTest`、`PrivateRealtimeIntegrationTest` | owner、afterCommit/rollback、连接失效；不新增全员topic。 |

新增必要测试建议：`CommunityPublicationIntegrationTest`（DB快照/版本/事务）、`CommunityHttpIntegrationTest`（realHTTP+Redis auth/安全DTO/撤回读）、`CommunityMaterialsIntegrationTest`（跨页/字段/合并与日期）、`CommunityMigrationIntegrationTest`（空schema与V16-owned升级）、`CommunityProfileIntegrationTest`（资料缺省与自身版本编辑）。这些名字是规划，不是已存在测试。

核心 AC 断言：

- AC-01/08/09/11/13：匿名401无标题/元数据；foreign草稿/源/附件404；ADMIN无私有旁路；CSRF拒绝无写；visibility!=MEMBERS拒绝；公开DTO无source/task/session/project/username。
- AC-02/12：三类型发布，作者退出不影响当前版本，无互动API或计数；作者主页只公开资料及可见帖。
- AC-03/04：默认无选择、至少两页，未选敏感数据/项目/时间不进稿，完成+焦点合并时结果一次、仅所选专注opt-in，日期切换；原源更新/删除、save不改公开散列；同日多篇。
- AC-05/06/15：save不切pointer，publish新增revision并原子切pointer，firstPublishedAt保持；成功响应丢失重试同revision、异payload409、两旧version写仅一成功；withdraw/hide后list/detail和文件的新GET/HEAD/条件/Range不得泄漏；hide与publish竞态不能恢复，审计一次。
- AC-07：原记录、关联进展、任务完成/重开、report_sources、日期/专注time与私有STOMP现有回归保持。
- AC-16/17/19：V18后验证错误附件不能发布、全部ready/同owner、配额实际字节与独立ID、历史对象保留/cleanup竞争；此处不可用text-only测试声称附件通过。

### 9. 验证命令与环境边界

- 当前真实命令来自 `README.md:176-180`：backend 下 `mvn -s maven-settings-aliyun.xml clean verify`。
- `backend/src/test/resources/application-test.yml:3,7-9` 必须让 TEST_DATABASE_URL currentSchema 和 WORKBENCH_TEST_SCHEMA 指向同一全新专用schema；`backend/src/main/java/com/aiworkbench/config/IsolatedProfileSchemaGuard.java:21-38` 新测试名必须匹配 `d9_[a-z0-9_]+_tests_[0-9]{8}`，例如 `d9_community_tests_20261003`。日常 public/application卷不用；数据库/Redis端口和账号凭证按授权隔离环境注入，本文件不含凭证。
- 有已建全新隔离服务、schema env、模型替身后，backend 下定向门禁：

```powershell
mvn -s maven-settings-aliyun.xml '-Dtest=DailyRecordPresentationIntegrationTest,FocusIntegrationTest,FocusHttpIntegrationTest,FocusConcurrencyIntegrationTest,TaskCompletionConsistencyIntegrationTest,DailyReportIntegrationTest,WeeklyReportIntegrationTest,BusinessOwnerIsolationIntegrationTest,AuthenticationIntegrationTest,PrivateRealtimeIntegrationTest' test
mvn -s maven-settings-aliyun.xml '-Dtest=CommunityPublicationIntegrationTest,CommunityHttpIntegrationTest,CommunityMaterialsIntegrationTest,CommunityMigrationIntegrationTest,CommunityProfileIntegrationTest' test
mvn -s maven-settings-aliyun.xml clean verify
```

- 新test类未实现前不运行对应命令；不把缺类“无测试”算通过。迁移单测有自建随机schema，完整运行不要全局覆盖 spring.flyway.schemas；live-acceptance 自有独立schema。
- 本次只做静态研究，未执行上述测试，未查询任何真实个人业务行，未检查或变更云资源。素材和权限有效性结论是代码证据，测试结果留待执行。

### 10. Related specs / external references

- 已读 `.trellis/workflow.md` 与 backend index。
- 适用规范：`.trellis/spec/backend/directory-structure.md`、`database-guidelines.md`、`identity-isolation.md`、`focus-routines.md`、`pagination.md`、`error-handling.md`、`quality-guidelines.md`，以及 `.trellis/spec/guides/index.md`。Quality部分仍模板，实际约束以已填合同/真实代码为准。
- 外部 references：本次没有新增外网研究；依赖精确版本仅来自已读 `backend/pom.xml`，未主张其是最新版本。R3 solution列的OSS/OWASP链接是交接来源，SDK/传输/解析器版本由storage研究另行验证。本文件的数据库/接口建议为本地工程推导，不把外部文档当成实际配置证据。

## Caveats / Not Found

- 真实代码没有 community posts/profile/revision/attachment 领域，上述表/API/测试类均是新增规划；本轮未假定原型内存数组有真实权限、持久化或并发保障。
- requirements文件开头仍沿用revision2/draft文字，HANDOFF元数据为revision3/ready-for-local-review且DEC17明确最终批准；读合同以R3决定及唯一AC正文为准，正式规划应消除这处来源描述差异。
- 默认沙箱exec因helper_sandbox_lock_failed无法启动，本次require_escalated仅用于只读文件与创建本任务research目录；产品/库/云状态未动。
- 测试配置真实路径在 `backend/src/test/resources/application-test.yml`，不在main；已纠正研究读取路径。
- 尚未运行realHTTP/PostgreSQL/Redis/浏览器；阿里云未申请，不进行真实OSS验证，不假造ACL/云凭证/上传结果。RustFS/阿里云切换以另一storage研究和用户当前授权为准。
- 无任何旧hash兼容、旧行为迁移回退、默认禁用开关、ADMIN私有读取特权、发布历史恢复入口或一天一篇约束。
