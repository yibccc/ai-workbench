# 站内广场与 OSS 附件 · 方案与本地核对

交接 ID：`WB-20261003-community-oss-3f9aaa` · revision：`2` · 状态：`draft` · 日期：2026-10-03
仓库：`yibccc/ai-workbench` · 分支：`master` · commit：`f27eda5f50cf298c5e864acc4961f5838efd4ac6`。

本文件可独立阅读。已确认基线与建议分开；`draft` 可用于本地只读核对、补齐规划，不代表批准编码、修改仓库、部署或使用真实云资源。相关代码事实沿用 revision 1 已记录的静态核对范围；本次仅更新布局批准与交接资料，未重新读取远端仓库或运行工程。

## 1. 目标、已选方案与边界

目标是在不放开私有工作记录的前提下提供站内内容发布/浏览，并实现图片、PDF、MD 的受控上传和读取。已选 DEC-03/DEC-07：私有 OSS 桶、后端中转上传、后端逐次鉴权读取；草稿附件仅作者可见、当前发布引用才对登录成员可见，撤回/下架拒绝新的读取。

已选额度：图片 5 MiB、PDF 20 MiB、MD 1 MiB，单篇 10 个/50 MiB；图片为 JPG/JPEG、PNG、WebP，不转换格式；PDF/MD 只下载。匿名、点赞评论、浏览器直传与面向读者的 OSS 签名链接不在首版内。

以下分层、表名、端点和状态是已有探索的技术候选，不是已实现代码或最终本地设计。DEC-11～DEC-13 的产品建议已由 DEC-17 升级为 selected；DEC-10 仍是架构候选，DEC-14 兼容方案仍未获批准，DEC-15 为本地技术细化。没有新增业务实现代码、SQL 迁移、生产补丁或最终执行计划。

**新增已选布局 DEC-16。** 用户原话“R2 的页面布局全部批准”。原型 R2 全部业务页面及状态画面成为 UI 布局基线，包括桌面/移动导航、作者主页、编辑/附件/预览区及确认/异常画面；不新做一版视觉，也不借“重新实现”改换布局。作者资料、素材默认值、保存/历史版本、管理员下架与附件清理规则也已由 DEC-17 确认；OQ-01～OQ-05 已关闭。

## 2. 已核实仓库证据与读取范围

仓库所有下列证据固定在文件开头所列完整 commit。revision 1 冻结时再次读取分支、交接合同、AGENTS、README 片段和相关归档 PRD；其他代码/规范来自更早对话对同一 ref 的实际读取。revision 2 只继承这些已记录证据，本次未重新读取远端仓库，也不主张该快照仍是当前 HEAD。`code_verified: true` 仅覆盖下面的静态范围，不是全仓扫描或运行验证。未克隆或访问用户本地工作区，dirty 状态/本地 HEAD 均未知。

| 证据 | 路径 | 实读范围 | 对本方案的约束 |
|---|---|---|---|
| S-01 | [docs/dev-sop/HANDOFF-CONTRACT.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/docs/dev-sop/HANDOFF-CONTRACT.md) | revision 1 冻结时完整读取，合同 v2.0 | 规定四份文档、元数据、DEC 状态、AC 唯一定义及不替代本地规划批准。 |
| S-02 | [AGENTS.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/AGENTS.md) | revision 1 冻结时完整读取 | 兼容层、兼容迁移、旧版回退、功能开关或默认禁用门禁须用户明确同意。 |
| S-03 | [README.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/README.md) | revision 1 冻结时第 1–28 行；此前返回的功能/部署/架构段落 | 已有工作区与部署概述；不能据此证明每项实际代码行为。 |
| S-04 | [frontend/package.json](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/frontend/package.json) | 前序完整读取 | 声明 React 19.1.1、TypeScript 5.9.2、Vite 7.3.6；这是仓库声明，不是运行环境或最新版本结论。 |
| S-05 | [backend/pom.xml](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/pom.xml) | 前序完整读取 | 声明 Java 17、Spring Boot 3.5.16、MyBatis 3.0.5、PageHelper 2.1.1、Spring Security/Session；无本次 OSS 接入验证。 |
| S-06 | [frontend/src/App.tsx](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/frontend/src/App.tsx) | 前序工具实际返回片段，非保证完整文件 | 启动 /me、手写 hash 导航、Workspace 组合、专注控制器/提醒和重复任务调用在返回代码中可见。 |
| S-07 | [frontend/src/api/http.ts](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/frontend/src/api/http.ts) | 前序完整读取 | 身份 epoch、中止旧请求、同源 Cookie、写入 CSRF、401 回调。 |
| S-08 | [frontend/src/styles.css](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/frontend/src/styles.css) | 前序第 1–130 行 | 中性浅色/紫色主色和布局基础；不是全部样式审查。 |
| S-09 | [.trellis/spec/frontend/directory-structure.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/spec/frontend/directory-structure.md) | 前序完整读取 | feature 组织、RetainedView、账号根专注、5 条工作区分页。 |
| S-10 | [.trellis/spec/frontend/identity-session.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/spec/frontend/identity-session.md) | 前序完整读取 | 会话/账号切换/活动信号、回归和隔离测试要求。 |
| S-11 | [.trellis/spec/backend/directory-structure.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/spec/backend/directory-structure.md) | 前序完整读取 | Controller→Service→ServiceImpl→Mapper，Service 事务，迁移不可变。 |
| S-12 | [.trellis/spec/backend/identity-isolation.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/spec/backend/identity-isolation.md) | 前序完整读取 | owner 隔离、CSRF、Redis 会话、ADMIN 无私有业务旁路、STOMP owner 信号。 |
| S-13 | [.trellis/spec/backend/database-guidelines.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/spec/backend/database-guidelines.md) | 前序返回的记录、日期、任务、事务及后续片段；非全文保证 | 业务日期/occurred_at、增量迁移、UUID、服务事务与同 owner。 |
| S-14 | [backend/src/main/java/com/aiworkbench/security/ApiSecurity.java](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/src/main/java/com/aiworkbench/security/ApiSecurity.java) | 前序完整读取 | 仅认证/健康等白名单 permitAll，其余 authenticated；不应为站内广场增加匿名放行。 |
| S-15 | [backend/src/main/java/com/aiworkbench/security/ApiSessionFilter.java](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/src/main/java/com/aiworkbench/security/ApiSessionFilter.java) | 前序完整读取 | 有效会话检查，失效 401，认证存储异常 503；将来匿名访问不能只加 permitAll。 |
| S-16 | [backend/src/main/resources/mapper/WorkRecordMapper.xml](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/src/main/resources/mapper/WorkRecordMapper.xml) | 前序完整读取 | owner 条件、任务完成/专注字段、业务时间与记录查询。 |
| S-17 | [backend/src/main/resources/mapper/ReportMapper.xml](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/src/main/resources/mapper/ReportMapper.xml) | 前序完整读取 | 私有来源快照包含项目、任务、专注字段，不能直接作为发布 DTO。 |
| S-18 | [backend/src/main/resources/application.yml](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/backend/src/main/resources/application.yml) | 前序完整读取 | 会话/数据源等主配置，已读主文件未见 OSS 设置；不等于全仓不存在 OSS。 |
| S-19 | [deploy/nginx.conf](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/deploy/nginx.conf) | 前序完整读取；上传轮另读前段 | client_max_body_size 1m，/api 代理与 SPA try_files；需核对上传路径而非只装 SDK。 |
| S-20 | [.trellis/tasks/archive/2026-10/10-01-focus-task-completion/prd.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/.trellis/tasks/archive/2026-10/10-01-focus-task-completion/prd.md) | revision 1 冻结时完整读取；只是需求文档 | 计时结束不完成任务，保存关联进展应完成任务并返回新专注；不是本次测试结果。 |

revision 1 已查看 `.trellis/tasks/` 顶层与 `archive/2026-10` 目录，读取了 S-20；没有遍历所有历史任务/个人日志，不能断言本地没有并行工作或相同 handoff。GitHub 分支入口仅用于确认该次快照，其他分支、PR、CI、线上数据不在本次核对范围。

## 3. 方案取舍与必要重构

**已选的文件链路。** 后端上传和读取统一处理登录、owner、当前发布引用与撤回状态。代价是文件流经业务服务器，需要本地确认带宽、连接和临时空间容量；没有实测容量数字。浏览器签名直传已不选，不能以性能优化为由恢复。预签名下载链接在有效期内可授权持有者访问，不适合本次“每次读取经工作台判断”的已选边界。[E-02]

**候选的应用结构（DEC-10）。** 保留现有 React/Vite、Java/MyBatis/PostgreSQL，新增发布领域而不是给全部私有记录加可见标记或拆独立社区服务。前端账号根持有身份、请求和专注生命周期，工作台/广场是其下面的展示空间。进入广场不应重复初始化声音/计时器，也不应因为渲染一个读者页面触发私有业务创建副作用。具体模块提取由本地确认，保留原有 RetainedView 的账号内状态。

正式路由候选不等于已选 React Router 版本；原型 hash 仅为离线演示。旧 hash 兼容跳转和“功能关闭回退”不得自动采用，见 S-02、OQ-06。

## 4. 数据与状态候选：不是 SQL 或已存在的表

| 拟新增概念/候选表名 | 作用 | 尚未锁定的部分 |
|---|---|---|
| posts | 作者、三类内容、业务日期、生命周期及当前发布版本引用 | 列名/枚举、正文编辑器、同日多篇规则 |
| post_revisions | 明确发布的不可变正文与附件集合；草稿保存不改当前发布引用 | 发布历史版本保留；具体表字段/索引由本地核实 |
| attachments | 作者、服务器生成的对象标识、原文件名、实际类型/大小、校验/上传状态 | 解析器、可读编码、临时状态；清理资格按 DEC-13，执行机制本地核实 |
| 发布/草稿与附件的关联记录（候选） | 证明哪个稿件/版本引用哪个已就绪附件 | 同一附件 ID 重复引用不重复计数；历史保留引用继续保留对象；不建设通用素材库 |
| public_profiles（候选） | 仅承载获批的对其他成员展示的作者资料 | 公开昵称/简介由独立公开资料承载并允许本人维护；不直接复用或暴露账户登录用户名；具体字段/表结构由本地设计 |

上传文件不是发布：上传中/校验中/失败/就绪只是附件处理状态，只有就绪且归作者所有的附件才可绑定到发布版本。`DRAFT/PUBLISHED/WITHDRAWN/HIDDEN` 仅为内容状态候选；手动保存只更新私有草稿；下架后作者不得自行再发布，首版无恢复流程。不要把一个大枚举同时承担稿件保存、发布可见性、审核和文件处理。

数据库保留 owner、稳定附件 ID、私有 OSS 对象标识与关联，正文不存短期签名 URL。发布 DTO 不传出原始 records/reports/source snapshot；需要追溯的来源关联只能在作者侧查询。对象不能被另一个上传覆盖，文件名不是对象主键。替换附件产生新对象/新引用，旧版本的已授权内容不会原地被替换。

## 5. API 候选与权限矩阵

所有以下端点均为**拟新增候选项**，未存在于已读仓库，也不是已冻结的外部接口合同。路径、错误码/DTO、幂等参数由本地核对，不得拿表直接生成补丁。

| 候选接口 | 责任 | 权限边界 |
|---|---|---|
| GET /api/community/posts | 已发布列表/筛选 | 有效成员；只输出当前可见版本 |
| GET /api/community/posts/{id} | 内容详情 | 有效成员；不可见按候选 404，不返回草稿 |
| /api/me/posts | 作者稿件管理 | 当前 owner；文本编辑产品规则仍待审阅 |
| POST /api/me/posts/{id}/publish | 初次发布/更新 | 当前 owner；版本/附件校验，防重复提交 |
| POST /api/me/posts/{id}/withdraw | 撤回 | 当前 owner；结果必须同时影响正文和附件读取 |
| POST /api/me/share-previews | 从私有素材整理草稿 | 只查自己的所选源；默认不选，跨页保留选择，按 DEC-11/17 过滤公开字段 |
| POST /api/me/posts/{id}/attachments | 单文件中转上传（候选形态） | 当前 owner，CSRF，类型/实际字节/单篇额度 |
| GET /api/community/posts/{postId}/attachments/{attachmentId} | 读者图片/文件读取 | 有效成员 + 指定帖当前版本可见且包含该附件 |
| GET /api/me/posts/{postId}/attachments/{attachmentId} | 私有附件读取 | 当前 owner，不能沿附件 ID 越权 |

作者主页布局与公开资料边界已冻结；具体 URL/API、资料存储字段和管理员下架端点属于本地实现设计，不改变已确认行为。下载不能按“创建者当前在线/离线”授权，也不能单凭 attachment.user_id == 当前用户，否则读者无法阅读已发布附件。反过来也不能“所有登录用户读所有附件”，否则草稿泄漏。两类授权都要检查文件就绪与可见引用。

首版沿用现有 Cookie/CSRF，不增第二套认证；新增站内 GET 仍须认证，不增加 permitAll。401、404 和服务故障应分开。候选错误合同：匿名/失效 401，外部私有或不可见资源 404，缺少写入 CSRF 403，过期写入/状态冲突 409；文件大小/格式/总额由本地固定可辨识的错误码，不能只显示“失败”。

## 6. 上传、读取及一致性：工程建议，不是本地执行计划

**上传职责。** 先核对身份和稿件归属，再以有界资源读取/暂存文件，验证实际大小、格式和额度，上传到 OSS，最终确认数据库就绪引用。文件传输不应占用一个长数据库事务。多人或同稿并发要在服务端原子计量/预留额度，失败/取消释放预留；不能先在浏览器加总再无条件落库。预留方案与超时属本地细化，不新建队列服务。上传 OSS 成功但数据库确认失败时，留下可识别的孤立状态/补偿记录供安全重试或清理，不能声称 PostgreSQL 与 OSS 有一个共同事务。

**文件验证。** 依已选白名单检查扩展名、内容、实际字节；不信任客户端 MIME/Content-Length。PNG/JPEG/WebP 与 PDF 需适合其格式的解析验证，MD 无固定签名，编码/文本合同本地明确。服务端生成对象标识，清理展示文件名中的控制字符/路径语义，错误日志不泄漏凭证。禁止原型的少量文件头检查替代完整服务端方案。OWASP 建议分层校验、授权、限额与安全文件名；格式校验不是病毒扫描证明。[E-04]

**读取职责。** 鉴权/授权在发出响应体和受保护元数据前完成；图片与 PDF/MD 均从 OSS 取流，由应用返回，不能用 302/307 把权限变成可转发云链接。PDF/MD 以下载响应提供，图片仅在获批展示位置内显示；MD 不作为 HTML 执行。下载后的浏览器自动打开不属于站内预览。按当前数据库可见引用检查，每个仍支持的 HEAD/Range/条件请求同样检查。

**缓存与撤回。** 为兑现“新的读取被拒绝”，候选为受保护内容/附件使用不共享的 no-store 响应，不引入公开 CDN/Service Worker 缓存；生产缓存头和代理行为留给本地核实。已开始流、已下载副本、浏览器已解码图片不承诺远程收回。不能将会话检查后签发的 OSS URL 当作撤回有效性的证明。

**发布一致性。** 公开集合与作者草稿隔离。确认发布时检验版本、所有附件 owner/就绪状态/额度/引用；在事务内切换当前版本。读取端只按该当前版本返回。反复请求应幂等，不生成重复发布；过期修改不静默覆盖。原型只是内存数组和布尔状态，不证明任何这些数据库性质。

**对象清理。** 移除编辑器引用不等于立即删 OSS；当前发布、有效草稿和获批历史保留引用仍存在时不得删除。只有不再被当前发布、有效草稿或保留历史版本引用的对象才是可清理孤立对象；首版不设置自动清理天数。实现不得使用过宽 Bucket 生命周期规则删掉有效内容。

## 7. 现有位置与改动候选

| 已读位置（证据） | 本地应评估的改动；均未实际修改 |
|---|---|
| frontend/src/App.tsx（S-06） | 账号根/页面外壳拆分，草稿/声音/活动信号不随空间切换失效 |
| frontend/src/api/http.ts（S-07） | 附件错误与真实 401 处理、取消/旧身份隔离，保留同源 Cookie/CSRF |
| frontend/src/styles.css（S-08，片段） | 按获批 UI 复用基础样式，不覆盖全部旧样式 |
| backend/src/main/java/com/aiworkbench/security/ApiSecurity.java、同目录 ApiSessionFilter.java（S-14/15） | 沿用认证边界，不把站内资源匿名放行；检查附件流错误在提交响应前的处理 |
| backend/src/main/resources/mapper/WorkRecordMapper.xml（S-16） | 候选素材读取保留 owner、日期、有效记录约束，不能移除 owner 条件 |
| backend/src/main/resources/mapper/ReportMapper.xml（S-17） | 不把私有 report_sources 直接串到读者 DTO；不改变报告生成链路 |
| backend/pom.xml、backend/src/main/resources/application.yml（S-05/18） | SDK/凭证配置/流式处理/上传限额；不把凭证放入前端 VITE 配置 |
| deploy/nginx.conf（S-19） | 现有 1m 请求体限制需与后端 multipart/业务限额协调；上传路径单独评估，保留其他路径限制 |

**拟新增候选位置**：`frontend/src/features/community/`、`frontend/src/features/publishing/`、`frontend/src/api/community.ts`；后端沿既有 `controller/`、`service/`、`service/impl/`、`mapper/`、`dto/` 组织发布与附件职责；增量 SQL 在既有迁移目录中新建下一可用版本。目录/文件名称非已存在事实，不指定未核实的 Flyway 版本号，不生成这些业务文件。

## 8. 部署、兼容与回滚边界

当前已读 Nginx 1m 会阻挡较大文件，中转上传需同时核实入口代理、后端 multipart 和真实字节业务校验。multipart 头有额外开销，不能简单把请求体限制设为恰好文件上限导致合法边界文件失败。临时目录、超时、连接取消、上传占用必须有界；没有给出未经测量的带宽或性能 SLA。

保留已应用 Flyway 迁移，新表/列仅通过新迁移追加。源数据不能自动赋予公开状态，不迁移真实私有记录为帖子。不覆盖 AGENTS、已有 spec，不因广场改掉任务/专注/报告时间与事务语义。

早期提到的旧 hash 兼容和关闭功能回退没有获批，与 S-02 的要求有冲突，已降为 DEC-14。正式实现前，本地必须比较旧二进制、Schema 与对象引用是否兼容，再呈现具体回退方案；不能假定回退应用安全、不能删除新表/OSS 对象当回滚、不能自动加默认关闭的门禁。需要停服或恢复时由本地依授权操作，所用备份不随本包传递。

本次只有交接资料的布局批准修订和原样保留的独立原型，撤除本包不涉及生产回滚。没有在本包中编造部署步骤、task start 命令或 commit 计划。

## 9. 原型代码材料说明与差异

类型：**已批准布局的独立参考实现/只读原型**，不是生产补丁；布局与本 revision 已确认业务行为 selected；技术实现仍待本地核实。来源是本对话刚交付的 `workbench-community-r2.zip`，本包将 30 个原文件逐字节复制到 `assets/prototype-r2/`，未重绘或重写。其版本仍是 UI R2，与本 handoff revision 2 独立计数；原型版本和内容未变。

目标：审阅广场→选素材→编辑附件→预览→发布→读者查看→更新/撤回。`src/app.js` 是内存模型，`src/fixtures.js` 全部虚构；`index.html` 内联源文件，图片示例为原型绘制。示例附件 ID、hash 路由、Markdown 简化解析、演示身份/状态菜单与静态专注显示不得直接移植为生产协议或安全实现。

批准变化：保留的 R2 `review-notes.md`/README 是不可变的原始附件，其“整体布局待审阅”描述已被本轮用户“R2 的页面布局全部批准”及 DEC-16 取代。整体导航呈现、作者主页、编辑器等全部既有页面布局现为 selected；素材默认值、身份资料边界、保存/版本保留、图片操作、管理员下架和附件清理语义已由 DEC-17 确认。不能引用历史原型说明要求用户再次批准布局，也不能把原型演示逻辑当成全部获批。附件额度、白名单、PDF/MD 仅下载和 OSS 链路以本包 requirements 已确认部分为准。原型能绕过“身份”开关查看其内嵌全部虚构数据，这是演示性质，不是访问控制缺陷修复已经完成。

验证状态：本次只做文件/摘要/源记录静态核对，未执行原型脚本或浏览器回归。随附 CHECKS.md 与 checks/results.json 是**上轮产物自带的原型局部记录**，revision 1 记录其 59 条 PASS（revision 2 不重跑），不据此宣称实际工作台、OSS、数据库、会话或原有专注已通过。本地不得自动执行 build.py、smoke.py 或 HTML，先依授权审阅。

## 10. 产品决定关闭与待本地核实项

OQ-01～OQ-05 已关闭，具体行为以 requirements AC 为准。剩余事项不是产品范围阻塞，而是本地核实或需要在触发时重新取得兼容批准：

| 编号 | 状态 | 本地责任 |
|---|---|---|
| OQ-01～OQ-05 | closed | 不重新询问产品选择；按 DEC-11～DEC-13/DEC-16/DEC-17 与 AC 实现/验证。 |
| OQ-06 | local-review / compatibility | 核对新路由对旧 hash/入口的影响。若确需兼容层、旧版回退或功能开关，必须依据 AGENTS.md 提出具体方案并取得用户明确同意。 |
| OQ-07 | local-review / technical | 核实当前 HEAD、Trellis 活动任务、OSS Bucket/地域/Endpoint/身份/最小权限、SDK 精确版本、代理与 multipart、流式 I/O、解析器、缓存、事务/幂等、数据库字段/索引和测试命令。 |

本地可在不改变产品行为的前提下细化数据结构、类名、端点路径和内部状态；若细化会改变已确认可见性、保存/历史、管理员权限、清理、兼容或重大风险，须回到用户。

## 11. 外部一手来源（沿用 revision 1 记录）

以下来源和适用性沿用 revision 1 的来源记录（其标注核对日期为 2026-10-03）；revision 2 未重新在线核实，不主张它们是本次查询的最新状态，也不是 SDK 已安装或云资源已配置的证据。SDK 精确版本未锁定；不新增 npm/Maven 依赖文件。

| 编号 | 官方来源与 URL | 本包采用的事实 / 适用版本 |
|---|---|---|
| E-01 | 阿里云 OSS Bucket ACL：https://help.aliyun.com/zh/oss/user-guide/oss-bucket-acl | 私有读写权限与对象默认继承；当前 OSS 用户指南，无本包固定服务版本。实际还需核查对象 ACL、Bucket/RAM Policy。 |
| E-02 | 阿里云预签名下载：https://help.aliyun.com/zh/oss/user-guide/how-to-obtain-the-url-of-a-single-object-or-the-urls-of-multiple-objects | 预签名 URL 在有效期内可授权持有者下载/预览；不等于每次检查工作台登录。当前 OSS 文档。 |
| E-03 | OSS Java SDK V2：https://help.aliyun.com/zh/oss/developer-reference/oss-sdk-for-java-2-0/ | V2 官方参考存在；本包仅保留候选，不声称选定最新版本、兼容验证或实例化成功。 |
| E-04 | OWASP File Upload Cheat Sheet：https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html | 授权、白名单、实际类型/大小、文件名及多层保护；通用上传安全参考，不是单一产品版本。 |
| E-05 | OWASP XSS Prevention：https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html | 按上下文编码与需要时净化，不能直接执行用户 HTML/链接；通用安全参考。 |

正文 Markdown 编辑器/路由库没有在本次选择具体版本。文件与代码的主张仅限 S-01～S-20，外部事实仅限 E-01～E-05；未通过公共搜索替代项目证据。
