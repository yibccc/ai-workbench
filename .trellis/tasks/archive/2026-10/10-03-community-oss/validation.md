# 工程验收与整合证据

用户已在最终规划后明确“开工/批准并连续执行”。业务分支 `codex/community-oss`，实现基线 `1a7928c2b2fa15ce0d3148af187770d85aca96f7`。此文件由主会话维护全量验收，子代理把具体命令/结果写各自任务验证记录；工程通过只能来自实际测试/HTTP/SQL/文件散列/正式截图。

## 隔离与最终整合状态

发布核心、RustFS附件和R2正式界面均已实现，独立子审查已完成。专用测试项目 `wbcommunityoss20261003`，PG15432、Redis16379、RustFS19000/19001，数据库 `wbcommunityoss_test`、当前后端测试schema `d9_community_storage_tests_20261003`、E2E `d9_e2e`、桶 `wbcommunityoss-e2e`，与日常数据库/schema/桶隔离。另有全新五服务项目 `d10deployvalidation` 做代理与崩溃恢复，验收后容器/卷/网络均已精确清理；未删除日常或专用测试项目。

普通本地 RustFS9000/9001、新卷及私有桶 `workbench-community` 已初始化和真实权限验证，管理与应用身份不同；只向忽略的 `.env` 追加缺失配置，原字节前缀保持。日常 PostgreSQL5432/Redis6379未重建，也未启动/迁移日常业务应用。原生脚本另用全新 `d9_native_storage_tests_20261003` 和独立Redis namespace实际验证通过，见research/runtime-delivery.md。真实资源ID、环境及秘密只保存在忽略的 `.local-runtime`；密钥不进入本文件、前端、解析worker或浏览器driver。

四任务规划validate与36源文件SHA核验已PASS，属于规划证据；不是下表工程AC结果。原型自带59PASS不作产品证据，阿里云API本轮DEFERRED。

## AC覆盖与结果

| AC | 核心判据 | 对应源TC | 负责验证 | 当前结果/证据 |
|---|---|---|---|---|
| 01 | A/B/ADMIN私有越权404、公开DTO无source/account | 01 | publishing+storage | PASS：CommunityHttp/AttachmentHttp真实Cookie、CSRF和递归私有字段拒绝；B/ADMIN均无私有旁路 |
| 02 | 三类发布/浏览/作者下线/无草稿泄露 | 02/20 | publishing+UI | PASS：allTypes、publishedProjection/ownerLogout；真实BLOG、DAILY素材及MOMENT发布与作者页面 |
| 03 | 跨页/跨日选择、默认none、所选公开字段/focus不重复 | 03/20 | publishing+UI | PASS：CommunitySources两个真实SQL场景、source browser两页/默认/日期guard；共享dailyPresentation原始focus并集 |
| 04 | source改删/save不改公开snapshot、历史与首次时间固定 | 04 | publishing+storage+UI | PASS：privateSaveAndCurrentPreview、source freeze、F1/F2/browser原始SHA及firstPublishedAt不变 |
| 05 | withdraw/hide旧详情/附件新请求404、审计/禁止再发 | 05 | publishing+storage+UI | PASS：realAdminHide/回滚/竞争及AttachmentHttp GET+HEAD；真实撤回/ADMIN理由及作者禁止再发 |
| 06 | 相同payload/requestId一次版本、固定原resultVersion、409/input保留 | 06 | publishing+UI | PASS：真实post锁并发/固定receipt；浏览器丢publish响应、迟到save和409显式读取/重试 |
| 07 | 原五区retained drafts/filters、唯一focus/声音、A→B隔离与旧回归 | 07/08 | 全量整合 | PASS：全量223含原后端；原76浏览器回归exit0（历史2FLAKY），修正测试建立lease同步后最新社区12+focus13均首次通过；原63workbench在76重跑中首次通过 |
| 08 | 详情直开/刷新、safe正文/URI/filename、CSRF/source拒绝无写 | 09 | 全量整合 | PASS：真匿名深链登录/刷新、Markdown安全、真实CSRF/非法来源与FK/事务回滚；safe filename及受保护Blob |
| 09 | 401无保护metadata、真实失效清view、503/403/404不误退出/不被动续期 | 08/10 | 全量整合 | PASS：真实HTTP401/404及expiry/A→B延迟Blob/ACK；403/503消费者状态另外以明确HTTP夹具覆盖，不冒称实际服务故障 |
| 10 | 登录回允许target、撤回404不回显、无外站redirect | 10/20 | UI | PASS：真实深链登录回原reader；撤回/hide404清投影；strict完整route与invalid target场景 |
| 11 | MEMBERS唯一scope、无匿名读取/写入副作用 | 10 | publishing+UI | PASS：malformedOrAnonymousPublication实际HTTP/SQL；UI明确members可见；匿名文件GET/HEAD无metadata |
| 12 | 无点赞/评论/计数/占位/API | 02/11 | publishing+UI | PASS：产品Controller/DTO/DB/React全范围检查；实际页面无互动入口/计数 |
| 13 | author同owner READY、reader仅current ref、匿名401/foreign404 | 01/12/19 | storage+UI | PASS：AttachmentHttp三场景、foreign/crosspost测试与浏览器真实图片/文件；current-reference SQL |
| 14 | withdraw/hide/替换ref后GET/HEAD/条件/Range no-store不能绕过 | 05/12/19 | storage+UI | PASS：currentReferenceWithdrawAndHide所有新请求，authorized条件/Range full200，401/404无元数据且no-store/private/nosniff |
| 15 | F1→privateF2→publishedF2散列/权限独立、F1历史不覆盖 | 12 | storage+UI | PASS：真实F1/F2对象、draft/revision refs及浏览器下载SHA；RR确定性竞争证明同一响应body/ref一致 |
| 16 | 解析/上传/DB失败无READY/错误成功，保正文/旧file、recover不复活 | 13/15 | storage+UI | PASS：Put/READY确认/refs子写失败、delayedPut删除fence；真正Docker JVM KILL→新JVM→recover/cleanup与浏览器重试保正文 |
| 17 | foreign ID不可绑定/delete、完整引用保护/cleanup竞争/失败可重试 | 14 | storage | PASS：foreign/crosspost、FK、DELETING/迟到token、abandonedDelete/失败重试及原FAILED receipt清理后不变 |
| 18 | JPG/JPEG/PNG/WebP/PDF/MD真实结构+扩展一致、MD UTF8/BOM无magic | 15 | storage | PASS（验证深度见下）：实际JPEG/PNG/PDF/UTF8 BOM/0MD；password/PublicKey ObjStm PDF；静态/动画/高压缩WebP及截断；真fatJAR worker |
| 19 | file字节等号/+1、10/50MiB含等号/并发唯一ID、历史不占当前额度 | 13/16/17 | storage | PASS：边界/个数并发/重放/历史已通过；新增并发active50MiB用例41311 exit0/1PASS，真实Put前屏障/最新version+1byte409/旧version原请求同UPLOADING/1PUT/四ID50MiB及下载SHA，正文/refs守恒 |
| 20 | 图片body/lightbox、PDF/MD仅download真sha与safe filename、失败保正文 | 18/20 | storage+UI | PASS：real attachment downloads/image/PDF两浏览器场景、原始SHA/MD不导入、移动lightbox/focus与下载失败重试 |
| 21 | 真RustFS私有bucket无签名拒绝、后端中转无key/cloudURL/redirect | 19 | storage+整合 | PASS：真实SDK/IAM权限、匿名GET/HEAD403、错checksum拒绝、后端完整byte/hash及Nginx链路；Aliyun DEFERRED |
| 22 | 全R2源码/17图及无图状态desktop/mobile/layout/geometry一致，无demo | 21 | UI+整合 | PASS：主会话逐页实际打开参考/17正式图及补真filled feed/saved UUID draft/PNG+PDF+MD对照；最后视觉用例52767禁重试1PASS，见UI/visual-review.md；20源状态只标FIXTURE_ONLY |

## 检查记录

| 最终门禁 | 实际执行与证据 |
|---|---|
| 后端 | Java17/Maven3.9.9 `clean verify` exit0，223/0failure/0error/0skipped；最后目标18亦通过。之后只新增AC19测试方法，定向41311 exit0/1PASS（不合并成新全量224）；产品/JAR不变，实际包装SHA `63b7df371897bd1cede8bc1c11169655f914f4854c70cff5ef2cac55b0d0c74a`。见storage/validation.md、check-report.md |
| 社区浏览器 | 12完整真实场景重跑exit0；初次9PASS/3FAIL诊断保留。包含一条明确HTTP错误夹具场景，其他真实HTTP/数据库/对象存储链路逐项说明。最后另增R2真实视觉第13case，单场景禁重试52767 exit0/1PASS；不冒称一次新的13完整运行 |
| 原工作台/专注回归 | 原76重跑exit0，其中74首次+2重试FLAKY；修正测试初始声lease等待后最新12社区+13focus `--retries=0` 25均首次PASS。没有虚构一次新的88全跑，也没有合并独立通过次数 |
| 构建/静态 | frontend lint/TypeScript/Vite build通过；传统515KiB advisory保留；后端无独立Java lint命令，compile/testCompile和全格式检查为实际证据 |
| Docker/代理/故障 | 官方五服务镜像真实构建；245个输入在最终构建前后一致。verify-compose最终56407 exit0，20MiB/5MiB/50MiB及+1、真实JVM KILL/recover、同浏览器重启/STOMP和Node子进程秘密隔离；d10容器/卷/网络终态全空 |
| Trellis/context | 父14/publishing8/storage12/UI9 implement+check entries，四次validate exit0；已知长文件截断告警均读实际文件，不提高注入阈值 |
| 既有迁移 | tracked V1～V16目录diff为空；V17/V18为新增且单独审核，未用tracked diff遗漏新文件 |
| 本地原生启动 | PASS，driver30223 exit0：已有start/stop/check-safety+独立synthetic EnvFile；真实JAR/Vite/worker环境敏感计数0、新JVM同会话/发布/原SHA200；finally8080/5173及PID文件全无，normal.env/旧依赖/JAR不变。见research/runtime-delivery.md |

当前工作区未提交，所有门禁针对上述基线之后的实际代码；最终源码/任务证据指纹由completion-audit记录。子审查是阶段快照，父矩阵使用后续真实验证结果关闭其历史NOT_RUN。真实用户尚未人工验收界面/声音/持续使用。

## 验证范围与保留记录

- 高压缩WebP分支验证完整RIFF/chunk/frame/encoding-header结构，不证明每个压缩bitstream；常规安全可分配帧实际decode。加密PDF验证外层xref/trailer/Root/Encrypt/offsets，不解密正文；未声称无病毒或通用恶意文件识别。没有擅加像素/页数限制或拒绝合法加密/动画类型。
- crash测试在真实UPLOADING落库后强杀JVM；为缩短验收等待，明确注入过去deadline，然后调用正常author recover。不改变产品5min配置，也不以模拟service异常替代真实进程故障。
- 修复过的原始失败日志/PNG保留，236个trace/video原字节及SHA移入Gitignored `.local-runtime/community-oss-browser-artifacts/`。可提交证据只含截图、geometry、脱敏摘要/报告；121个任务文本对已知本地凭据匹配0。此扫描不是通用secret/病毒认证。
- `.env`、测试env、provider凭据、session网络trace、DB内容/日志与临时构建overlay不提交。业务分支尚无新增commit、无push/PR/远端部署/云API/真实付费模型调用。

## 实际commit-preamble补充

用户已确认具体本地提交/本需求归档/日志。暂存新增CSS时git diff --cached --check发现EOF额外空行；仅将最后换行规范为单LF、完整样式规则字节不变。frontend lint79040/build59573实际exit0、prod资产hash一致，已重新暂存格式门禁exit0，未改产品行为/测试或冒称重跑完整浏览器。此变化单独记录，不将原366字节指纹对这一个格式文件仍称完全相同。
