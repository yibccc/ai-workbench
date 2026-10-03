# 交付回流

handoff_id / revision：`WB-20261003-community-oss-3f9aaa` / `3`；界面原型仍为R2。

Trellis任务：`.trellis/tasks/10-03-community-oss`，子任务 `10-03-community-publishing`、`10-03-community-storage`、`10-03-community-ui`。

状态：本地实现与工程验收完成，核心业务代码已本地提交；本报告随第3文档提交记录，随后执行已批准的原生归档/日志。没有推送、PR、合并或远端部署。

业务commit：后端/存储 `bff403f142793d9f30c039f7036604d368946085`；前端 `5180ff121a2e5e0f1e81d48bdf9307a7c3bd416f`。业务分支 `codex/community-oss`。本报告及规范为随后第3次文档提交，其SHA由最终journal记录，不自引用预填。

review base / reviewed HEAD：`1a7928c2b2fa15ce0d3148af187770d85aca96f7`；验证实际覆盖该HEAD后的tracked与untracked完整工作区，不只看HEAD或最后一块改动。网页snapshot `f27eda5f50cf298c5e864acc4961f5838efd4ac6`。

验证记录：父 `validation.md` / `completion-audit.md`，三个子任务 `validation.md` 与 `check-report.md`；最终文件指纹及提交清单由父任务记录。

后续归档/日志commit：用户已经后续明确回复“确认”，由本报告提交之后的原生Trellis生成，实际SHA见journal/最终答复，不预填。归档目标为 `.trellis/tasks/archive/2026-10/` 下同名父/三子目录；原始任务路径为本报告生成时的active位置。

## 实际交付

- 站内登录成员可浏览今日分享、动态、博客和公开作者资料；公开DTO只含昵称/简介与发布投影。本人原始记录、素材、草稿与账号敏感信息保持私有，ADMIN无他人私有旁路。
- 日期素材跨页选择，默认不选，只复制明确选中的公开字段；专注额外勾选且不重复计完成。私有来源冻结、后续来源修改/删除不改变发布快照，同日允许多篇。
- 手动保存私有草稿，预览确认的完整正文/摘要/附件一次事务发布；历史不可变，首次发布时间保持。同逻辑请求重放固定receipt/version，过期409保输入。作者可撤回，ADMIN下架需理由/审计且作者不能绕过重发。
- RustFS私有对象存储，JPEG/PNG/WebP/PDF/UTF8 MD真实校验；精确文件及10个/50MiB额度。受保护后端图片/下载、当前引用鉴权、无云直连/预签名/redirect；PDF/MD只下载。持久上传状态/预留/fencing、显式恢复、引用安全清理及失败重试，历史对象不覆写/误删。
- 直接提取复用R2模板/CSS区域，正式React接真实API，移除演示身份/状态工具/静态计时/虚构数据。严格受支持hash、登录回原目标、共享身份/CSRF/Blob管线与原五工作区/唯一focus控制器，dirty guard和相同请求重试保留输入。
- 普通WSL Docker RustFS9000/9001及私有桶已准备，管理/app身份分离；正常`.env`只追加新键且原字节前缀保留。现成Windows脚本用独立synthetic数据库/schema完成真JAR/Vite/worker/重启下载证据，日常PG/Redis未重建，日常schema未迁移。

## 与原方案的偏差

用户本轮明确改为本地RustFS，阿里云尚无凭据所以不实现API；正式规划已据此批准。ObjectStorage/附件ID/key/数据库和后端HTTP保持供应商隔离边界，后续需新增阿里云adapter、同key字节长度/SHA迁移验收后切配置；本轮没有声称只改一个provider值就能使用尚未实现的云适配器。

沿用本地真实技术栈与当前native hash，未采用源建议中的路由库/旧hash兼容方案。素材使用已在本地合并的真实dailyPresentation投影；PUBLIC只MEMBERS。上传/发布采用完整payload、固定历史receipt与同post短事务，没有独立附件版本。以上技术决定已写正式design并通过最终规划批准，没有新兼容层/回退/flag/默认关闭门禁。

R2桌面原图为1440×1040 full-page（高度随内容），正式应用沿现有100dvh内滚动合同，截图记录对应viewport/inner scroll；移动390×844。实际字体回退、真实内容/日期、认证表单与分页数据自然差异保留，不复制原型演示文字/用户/计时器。主区域分组、列宽、导航和主要操作沿用原CSS。

## AC验收

父validation包含完整源AC/TC映射、具名测试及范围；下表是回流摘要。人工列均指用户尚未亲自验收，不把代理自动观察写成人工批准。

| AC | 结果 | 实际证据 | 人工验收 |
|---|---|---|---|
| AC-01 | PASS | CommunityHttp/AttachmentHttp A/B/ADMIN/CSRF及公开DTO私有字段拒绝 | 未进行 |
| AC-02 | PASS | allTypes/member可见/ownerLogout及真实三类型浏览发布 | 未进行 |
| AC-03 | PASS | 两页素材/日期guard/字段默认/focus合并与SQL冻结 | 未进行 |
| AC-04 | PASS | source改删/save不改公开、immutable history/firstPublishedAt、F1/F2 SHA | 未进行 |
| AC-05 | PASS | 撤回/hide GET/HEAD404、真实audit回滚/竞争与禁止重发 | 未进行 |
| AC-06 | PASS | 同request并发固定receipt、真实publish ACK丢失、409/late-save保输入 | 未进行 |
| AC-07 | PASS | 后端223及原浏览器76（2历史FLAKY），最新社区12+focus13无重试25首次通过 | 未进行声音/持续使用 |
| AC-08 | PASS | 真实深链/刷新、安全Markdown/filename、CSRF/source拒绝无局部写 | 未进行 |
| AC-09 | PASS | 真401/匿名无metadata/A→B；403/503消费者另标HTTP夹具；原activity合同保持 | 未进行 |
| AC-10 | PASS | 真登录回原reader、hide/withdraw清投影、strict允许target | 未进行 |
| AC-11 | PASS | 真HTTP构造匿名scope拒绝、members提示与无写入 | 未进行 |
| AC-12 | PASS | 实际Controller/DB/React无互动入口、计数或API | 未进行 |
| AC-13 | PASS | author READY/owner、reader当前refs、匿名401/foreign404 | 未进行 |
| AC-14 | PASS | 全GET/HEAD/conditions/Range新请求引用/状态检查和禁止缓存/redirect | 未进行 |
| AC-15 | PASS | F1/privateF2/publishF2真SHA/history refs及RR一致投影竞争 | 未进行 |
| AC-16 | PASS | 验证/Put/确认/子写失败与迟到Putfence，真正JVM KILL→recover/cleanup | 未进行 |
| AC-17 | PASS | foreign/crosspost/FK/全历史引用/删除token/失败重试及原FAILED receipt | 未进行 |
| AC-18 | PASS（范围如下） | JPEG/PNG、PDF加密/PublicKey ObjStm、动画/高压缩WebP、UTF8/BOM/空MD，真fatJAR worker | 未进行 |
| AC-19 | PASS | 等号/+1、10个/50MiB/history/并发重放；新增active50MiB的最新version+1byte409/原请求旧version重放/只一次真实Put与原SHA，41311单方法exit0/1PASS | 未进行 |
| AC-20 | PASS | 真图片/大图/PDFMD download SHA、filename、失败保正文/重试 | 未进行 |
| AC-21 | PASS（RustFS）；Aliyun DEFERRED | 私有IAM匿名/越权403、SDK原SHA、Nginx→JAR→RustFS及Native链路 | 未进行 |
| AC-22 | PASS | 主会话逐页参考/17正式PNG+源码复用；另真filled feed/saved UUID draft/三附件/下载补图52767禁重试1PASS，20源状态仅FIXTURE_ONLY | 未进行 |

## Review问题与处理

发布独立审查补充audit故障回滚、stale写入/非法附件/foreign publish真实拒绝测试，未发现发布产品缺陷。存储审查修正浏览器verification子进程继承backend/storage环境问题，真实Node forbiddenCount0与必需合成登录字段通过。UI审查修复upload未知状态错误换key、旧READY receipt作为当前version、owner reread dirty基线、ADMIN导航跳过guard、uppercaseUUID与异步profile focus。

实际执行遇到的普通问题已修并保留原失败结果：Spring test contexts超过PG100连接（只限制测试缓存、不降产品并发）；早期隔离V18草稿checksum变更（保留旧schema，启用新schema，不repair/drop）；Docker构建网络ECONNRESET/ETIMEDOUT（临时隔离host build overlay，官方版本/TLS未改）；真实浏览器 selector/发布完成等待与focus测试起始lease竞态；native driver pipe继承和header大小写检查。没有放宽产品验收、隐藏warnings或把失败跑删除后冒称一次全通过。

后端最终223全部通过；后续仅新增测试方法41311 exit0/1PASS单独记录，不冒称新全量224。原76回归终态exit0但两个测试曾retry，另修等待真实lease后最新25无retry通过；未虚构新的一次88全跑。初次社区9/3与后续12通过均保留，之后新增第13视觉case52767单场景1PASS，不冒称13完整重跑；正式补图不以静态/夹具假数据代替业务。

## 规范沉淀

新增 `.trellis/spec/backend/community-publication.md`、`private-attachments.md`、`.trellis/spec/frontend/community-publishing.md`，涵盖完整tuple/receipt、owner与公开DTO、引用/额度/fencing/worker、Blob身份与R2复用。更新两侧index、backend local-delivery/linux-deployment、frontend directory-structure/dialogs。现行runtime status四键合同未擅加一个存储状态键。

## 残余风险与下一轮

- 阿里云adapter/API/资源/对象迁移工具按批准范围延期，申请资源后独立实现和实际供应商验收。
- 高压缩WebP分支只证明完整container/frame/encoding-header结构，不验证每个compressed bitstream；encryptedPDF只证外层xref/trailer/Root/Encrypt/offsets，不解密正文。不声称无病毒，也未加未授权像素/页数/动画/加密禁令。
- 真实用户尚未人工检查视觉、声音和持续使用；Vite约515KiB构建advisory保留，lint/type/build通过。没有把常规advisory作为产品阻塞或进行无关重构。
- 日常应用仍未启动/迁移真实schema；普通RustFS已就绪，用户可按README构建/现成start使用。新native专有schema与两个仍被引用的测试对象留作持久性证据，不误删历史/稿件引用。

## 发布与恢复

当前本地 `codex/community-oss` 核心代码已有上述两个实际业务commit，文档提交/原生归档/journal按已批准顺序生成；无push、PR、合并或远端部署。全部验证使用隔离合成账号/schema/资源，普通RustFS是新增本地依赖。

恢复使用同一新JVM和持久PG/Redis/RustFS数据；不降迁移/旧版fallback/删卷。作者显式recover逾期未完成上传，cleanup需全部引用检查并保留tombstone/key。已下载字节或已授权流不可追回。

原始trace/video包含实际synthetic登录/Session网络记录，236文件保原SHA移入Gitignored `.local-runtime/community-oss-browser-artifacts/`，后续补图raw evidence也只在ignored目录；失败历史PNG和脱敏报告仍保留，不提交env/密钥/网络trace/构建产物。初始5份无关用户SOP明确排除。具体三业务提交+四任务归档+journal已一次呈现并获用户“确认”。提交前暂存门禁另发现新增CSS末尾多空行，已仅规范终止换行（规则字节未变）；实际lint79040/build59573均exit0、产物hash一致，未重复业务E2E或改变页面行为。后续只按批准范围执行本地Git写入。
