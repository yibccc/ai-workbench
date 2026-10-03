# Research: 最终规划批准门禁完整性审计

- Query: 有限静态审计当前父与三个子任务，对照原始用户要求、R3 FR-01～10/AC-01～22，并核对 B-01～04、FRV-01～05 是否落为统一合同。
- Scope: internal；一轮文档审计，不重做源码探索、不改产品/其他规划、不 task start、不跑工程测试。
- Date: 2026-10-03（Asia/Shanghai）
- Active task: `.trellis/tasks/10-03-community-oss`

## Findings

### 结论

**当前正式规划可以提交用户作最终批准。此轮未发现需要在批准前补齐的确凿阻塞缺漏或相互矛盾。** 父 PRD/design/implement 和 publishing/storage/ui 三子任务各有完整三文档，父design是唯一已选技术合同，子任务明确引用而不产生同义接口/状态。原审阅 B-01～04、FRV-01～05 均已落地；剩余 RustFS 安装、工程实现、并发/真实文件/布局验证属于批准后的执行，不应冒充已验收。

这不是执行批准：四 task.json 均 `status=planning`，没有已批准/start记录。用户原始指令授权创建/更新规划，要求在最终规划确认处可以批准并持续执行；父 `implement.md:21` 与 `planning-review.md:46` 正确保留这一最后门禁。当前尚未收到后续明确批准，不能因为此审计ready而 start。

### 原始用户要求逐项核对

| 原要求 | 正式证据 | 结果 |
|---|---|---|
| 接入指定R3交接包、允许创建/更新Trellis规划 | 父prd:11-15；handoff-check:3-19；父及三子task.json为planning且关联同handoff/parent | 满足规划阶段 |
| 核对真实代码、补齐正式规划 | backend/frontend/storage研究及handoff-check记录真实锚点；12份正式三文档齐全，planning-review:15,42-44有validate记录 | 已有静态事实/规划证据；无工程PASS混淆 |
| 已有原型、代码直接复用 | 父design:9-11从R2 CSS/app.js骨架直接提取React并限定样式；design:95-108完整有图/无图状态表；UIimplement:5-11复用表、17图与真实截图/geometry | 满足；明确去演示/不安全机制 |
| 本地RustFS在WSL Docker | 父prd:7,19；design:112-114固定私有RustFS1.0.0、新资源/Windows与compose端点/最小身份；storageimplement:5-11真实bucket读写及匿名拒绝 | 满足；尚未启动是执行待办 |
| 未来可切阿里云、未申请API先不做 | 父design:116稳定ObjectStorage/key/DTO与后续adapter/复制sha核验；prd:60,65；storageprd:23 | 满足；不把改配置等同自动搬对象、不调用云API |
| 最终规划批准后一直执行 | 父prd:71与implement:9-15,21确定publishing→storage→UI→父整合，Trellis implement/check与依赖明确 | 满足；尚待最终明确批准 |
| 问题最后汇总确认 | 父implement:83-88与design:138，子implement各自末段：普通技术问题自行修复最后报告，真实AC阻塞/新范围/具体兼容或主要布局变化另行说明 | 满足；没有反复重开已确认产品选择 |
| 不擅自兼容/迁移/回退/默认禁用 | 父prd:67、design:31,136；子非目标；保留原工作区hash为当前合同，新增同parser路径，没有旧地址映射 | 满足 |

### R3 FR-01～10 对照

下表父prd/design/implement均指 `.trellis/tasks/10-03-community-oss/`。

| FR | 正式PRD对应 | 设计与交付覆盖 | 结果 |
|---|---|---|---|
| FR-01 仅有效成员且私有隔离 | prd:23,40,48,50,52 | design:7,15,63-93,130；publishing/HTTP owner/CSRF，storage读引用权限 | 完整 |
| FR-02 三类发布与浏览/离线可见 | prd:24,41 | design:43-57,61,75-79,95-106；publishing+UI | 完整 |
| FR-03 本人指定日跨页素材/公开字段 | prd:25,42 | design:67,84-85；implement:32；publishingimplement:8、UIimplement:9,11 | 完整 |
| FR-04 私有save/明确publish/保留历史 | prd:26,43,45,54 | design:44-45,61-65,81-83；publishingimplement:7-9、storage引用集成 | 完整 |
| FR-05 类型/限额/PDF-MD只下载 | prd:27,57-59 | design:120-130；storageprd/implement、UI图片下载布局 | 完整 |
| FR-06 后端上传私有存储不发直读能力 | prd:28,52,55-60 | design:112-130；storage负责V18/adapter/真实权限测试 | 完整，当前供应商RustFS是用户明确覆盖 |
| FR-07 撤回/ADMIN下架/审计/禁止绕过 | prd:29,44,53-56 | design:61,63,65,83,86,128-130；publishing与storage/UI联动 | 完整 |
| FR-08 无点赞评论 | prd:30,51,65 | 设计API无互动，publishing/UI非目标与完整UI核验 | 完整 |
| FR-09 原工作台不退化 | prd:31,46-49,67 | design:7,15,31,67；implement:33,50,55与现有回归 | 完整 |
| FR-10 R2全部布局/无演示工具 | prd:32,61 | design:9-11,31,95-108；UIimplement:5,10-11，源码和17图共同对照 | 完整 |

### AC-01～22 逐条覆盖

父prd:40-61的22项逐条继承源requirements第4节。source R2/draft抬头残留已由prd:14和handoff-check说明，不能重复降级DEC-17。只有AC-21供应商按当前用户指令改为RustFS；其他产品边界没有缩小。

| AC | 判定目标与正式设计证据 | 执行所有权 / 规划状态 |
|---|---|---|
| 01 | 私有foreign404、owner+public投影分离、不含source/account字段；design:7,44-46,63-93 | publishing+storage，已计划 / NOT_RUN |
| 02 | DAILY/MOMENT/BLOG、仅当前PUBLISHED、作者离线不限制、独立作者profile；design:43-57,61,75-79,105 | publishing+UI，已计划 / NOT_RUN |
| 03 | 全日分页/跨页选择、默认none/公开字段、focus独立、同日多篇；design:67,84-85 | publishing+UI，已计划 / NOT_RUN |
| 04 | immutable title/date/summary/body/type与refs；save不公开、首发时间固定；design:44-45,57,61-65 | publishing+storage+UI，已计划 / NOT_RUN |
| 05 | withdraw/hide状态、旧请求404、ADMIN expectedRevisionId+reason/audit、不恢复；design:61,65,83,86,130 | publishing+storage+UI，已计划 / NOT_RUN |
| 06 | 整payload fingerprint/当次result_version、不重做、stale409、本地输入保留；design:63-65、UIdesign:9 | publishing+UI，已计划 / NOT_RUN |
| 07 | 账号根唯一focus/RetainedView、真实401/换号epoch/abort、旧业务回归；design:15,31,67、implement:33,50,55 | 三子+父整合，已计划 / NOT_RUN |
| 08 | strict hash/直开门禁、safe Markdown/协议/文件名、CSRF/source校验；design:17-33,67,71,120-122 | 三子，已计划 / NOT_RUN |
| 09 | anonymous/失效401、其他错误独立、不后台续期；design:15,31,71,130 | 三子，已计划 / NOT_RUN |
| 10 | 原允许站内target登录返回、404不回显、无外站redirect；design:31、UIdesign:11 | UI，已计划 / NOT_RUN |
| 11 | visibility MEMBERS拒绝其它scope、全部读取受现有auth链；design:63,71,75-93 | publishing+UI，已计划 / NOT_RUN |
| 12 | 非目标与API不新增互动/count/空占位；prd:51,65、UIprd:19 | publishing+UI，已计划 / NOT_RUN |
| 13 | author同owner/post READY与reader当前ref；HEAD先鉴权；design:88-89,130 | storage+UI，已计划 / NOT_RUN |
| 14 | 当前pointer/status授权、no-store/no304、Range完整200仍先auth；design:61,128-130 | storage+UI，已计划 / NOT_RUN |
| 15 | F1/F2新key、draft/revision引用分开、历史 retained；design:53-55,63,128，implement:42 | publishing+storage+UI，已计划 / NOT_RUN |
| 16 | pending/FAILED不可publish、失败保持文本/其他附件、token fencing/recover；design:124-128 | storage+UI，已计划 / NOT_RUN |
| 17 | same-owner/post refs、全历史/reservation检查、DELETING防绑定、失败可追踪重试；design:53-55,126-128 | storage，已计划 / NOT_RUN |
| 18 | 结构+扩展白名单/严格UTF8 MD/无转换，合法子类型不静默拒；design:122 | storage，已计划 / NOT_RUN |
| 19 | 精确5/20/1MiB、10/50MiB、含等号、去重ID/历史不计、并发预留；prd:58、design:120,124-128 | storage，已计划 / NOT_RUN |
| 20 | 图片正文/大图、PDF/MD仅Blob下载无导入/预览、safe filename；design:33,103,122,130 | storage+UI，已计划 / NOT_RUN |
| 21 | RustFS后端stream/私有bucket/无直连签名或redirect、无匿名对象读；design:112-116,130 | storage+UI，已计划 / NOT_RUN；Aliyun DEFERRED |
| 22 | 完整R2源码直接提取/17图/无图状态/桌面移动/geometry/无demo；design:9-11,95-108、UIimplement:5,10-11 | UI，已计划 / NOT_RUN |

父implement:81明确各交付及所有TC-01～21继承，最终父整合负责全AC状态/证据；text-only publishing阶段不宣称附件/父全量通过（implement:34）。不存在漏编号、只测最后UI或把mock布局当业务PASS的合同。

### 上轮审阅逐条闭环

| 发现 | 当前统一合同及子执行锚点 | 状态 |
|---|---|---|
| B-01 reserve/put/finalize失效复活 | design:53,124-128 token+state+有效reservation，移除pending同事务失效，晚到仅补偿；storageimplement:8,10 | resolved |
| B-02 崩溃永久占额/旧key遗失 | design:53,91,124-126 finite deadline、显式recover、FAILED新requestId/new行/key保留原key、tombstone；storageimplement:8,10 | resolved |
| B-03 业务日期/summary快照缺失 | design:43-45,57,63,80-82 current revision metadata、summary全链路；publishingimplement:7、UIdesign:13 | resolved |
| B-04 幂等resultVersion不固定 | design:45,63,124 持久result_version、重放不授予当前新token；publishingimplement:7,9 | resolved |
| FRV-01 R2 BLOG summary缺失 | 同B-03，UIdesign:13 dirty/预览/卡片/新输入持久化 | resolved |
| FRV-02 e2e脚本不存在 | 父implement:72,75与UIimplement:12 使用真实 `e2e`，无新脚本alias | resolved |
| FRV-03 有界滚动/portal样式缺失 | design:9 明确100dvh、`.community-scroll`、nav在外/1320分组在内、geometry；UIdesign:3、implement:10 | resolved |
| FRV-04 leave guard误改原Retained草稿 | design:31 只guard社区编辑上下文，原5区保留、不global discard；根唯一focus | resolved |
| FRV-05 type switch/无图状态遗漏 | design:26-29,57,95-108、UIdesign:13保存旧稿并新建/DAILY sources，不原地type mutation，完整状态对照 | resolved |

storage-review的运输上限、先查upload成功重放再旧version、失败key持久化、parser限制等补充，同样已在design:112,120,122,124-128固定。未因这些技术修订新增兼容、恢复文章、云API或产品门禁。

### 规划 ready 的证据与权限状态

- 已完整审阅父和三子12份PRD/design/implement；task.json父列三children，三子parent一致，全部planning。
- `planning-review.md:42-44` 与 `research/planning-validation.json` 记录四任务实际task validate exit0，implement/check条目分别14/8/10/9；本次仅读记录，不重跑、不读取角色隔离manifest。
- **本次实际只读摘要核验**：Get-FileHash SHA256 对12份当前规划文件逐一比对planning-validation.json，12/12 `RecordedDigestMatches=true`。因此既有validate记录对应当前12文档版本，没有发现记录之后未同步的正式规划变更。
- 容量告警database-guidelines/backend研究超过32768注入上限，父implement:17已要求子侧完整读取原路径、随后父三文档并以已选design覆盖research候选；不提高全局限制/删事实规避。
- 批准后以真实task current路径启动publishing子任务，再按清单持续实现/check/整合。额外git提交、push、部署、云操作仍无授权，不能由ready推导。

### Files found / related specs / external references

- 审计输入：`.trellis/tasks/10-03-community-oss/{prd,design,implement,task,planning-review}.md/json`（task实际为task.json）、`research/{backend-review,frontend-review,storage-review,handoff-check}.md`、`research/planning-validation.json`及不可变R3 requirements的FR/AC索引；三个同级子任务各三文档与task.json。
- Code patterns：不新增源码探索；采用已完成backend/frontend/storage研究的真实CurrentUser/PageQueries/日展示merge/identity epoch/RetainedView/Flyway/multipart/WSL证据。
- Related specs：父与子context已记录 backend identity/database/focus/pagination/error/directory、frontend identity/viewport/dialogs/focus/directory 规范；本轮不重读implement/check JSONL。
- External references：无新增外网查询、SDK/镜像版本新选择或产品方案；已有官方研究来源仍保留原适用范围。

## Caveats / Not Found

- 没有发现还需用户在批准前选择的产品项；R3 OQ-01～05关闭，旧hash/回退/flag不引入。常规实现细节不得借此重开确认。
- 这是**规划完整性ready**，不是工程check/PASS：产品代码、依赖安装、镜像拉层、RustFS启动/bucket、迁移、实际HTTP/Redis/PostgreSQL并发、浏览器/视觉/声音、文件流/额度/ACL均未由此审计执行。
- 所有AC工程状态保持NOT_RUN；Aliyun本轮DEFERRED。源码和原型自带历史59PASS不能替代真实验收。
- 唯一尚待外部输入的门禁是用户对已经呈现最终规划的后续明确执行批准；此报告不能替用户批准或触发start。
