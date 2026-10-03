# 最终规划收敛记录

日期：2026-10-03（Asia/Shanghai）。review base / 本地HEAD：`1a7928c2b2fa15ce0d3148af187770d85aca96f7`。状态：planning，待用户最终批准；没有task start或产品实现。

## 后续开工批准

最终摘要呈现后，用户后续明确回复“开工”，并在最终规划确认问题中选择“批准并连续执行”。该批准覆盖既定 publishing → RustFS/storage → R2 UI 的实现与验证，不再等待重复确认；普通已解决问题在最终回流中汇总。批准前的一轮审计见research/approval-gate-audit.md，22AC覆盖完整，所有原B/FRV发现已落成合同，没有新增阻塞。本文下方“待批准/未开工”结论描述批准前核验时点。

## 需求收敛

- 目标/产品价值、FR-01～10、AC-01～22、in/out scope、非目标和既有账号/日期/专注合同均已在父PRD收敛，不存在临时TBD或未选产品行为。
- R3 confirmed决定完整继承；源R2/draft残留按HANDOFF R3/DEC17解释保留，不篡改不可变源。
- 本轮用户明确覆盖供应商：本地WSL Docker RustFS1.0.0，阿里云API未申请不实施。统一ObjectStorage/稳定key/公开附件URL合同，下次真正切云需要同key复制/sha验证与adapter实测，不能声称改配置自动搬数据。
- 最终技术合同唯一权威是父design：表前缀community、类型DAILY/MOMENT/BLOG、canonical hash/API；research内候选别名均只作证据/建议，不创建双接口/旧路径兼容。
- 三独立可验收子任务顺序publishing(V17)→storage(V18及同发布事务集成)→UI与全量E2E，依赖明确写入各子文档，父负责整合。

## 真实核对与静态审阅

3个research代理核对实际后端、前端/原型所有17图、WSL Docker/依赖/官方版本，分别落盘backend/frontend/storage.md。独立planning静态复核见对应`*-review.md`；这不是工程check通过记录。

| 发现 | 正式处理 |
|---|---|
| 前端发布必须消费当前预览输入 | publish完整payload单事务保存draft+immutable revision+refs/pointer；不两步隐式save/publish旧正文 |
| R2 BLOG summary全链路缺失 | draft/revision/create/save/publish/read/dirty/fingerprint均加入summary，读者从current revision取 |
| draft业务日期会提前改变公共metadata | businessDate冻结在draft/revision，reader日期只current revision；post类型创建后固定，type-switch保存旧稿并新建 |
| 幂等重放错误授予当前新version | revision.result_version固定当次提交，重放不重新应用状态，不从current post/revision_no推算token |
| reserve/外部put/finalize跨tab取消竞争 | upload token/state/reservation条件fencing，移除pending同锁释放，晚到put仅补偿不复活 |
| JVM崩溃永久占quota、失败重试遗失旧key | finite deadline+作者显式recover，FAILED原行/key永久可追踪，新requestId/newattachment/key重试 |
| ADMIN需要作者私有version | hide用public expectedRevisionId+reason，与发布同post锁审计，无私有读特权 |
| 错误e2e script与Windows无docker | 使用真实npm run e2e与WSL python3 verify-compose；--help只读执行exit0 |
| 原型body滚动与工作台100dvh冲突 | `.community-scroll`有界named focusable region，原1320/padding/cards顺序保持，nav/bottom bar在外；geometry实测 |
| 新dirty guard可能改变原工作区retained草稿 | guard只社区编辑上下文；原5工作区仍保留input/filter，唯一focus根不重建 |
| 原型完整页面有无图状态遗漏风险 | 父design列全R2页面/状态表，type-change/dirty/lightbox/copy/empty/error与17图一起验收 |
| 私有stream/secret/multipart | 先权检GET/HEAD，不304/206旁路；SDK流关闭与no-store；storage env前端剥离，21MiB file/22MiB request运输上限统一精确 |

以上均为已确认AC内的常规技术修订，没有新增需要用户选择的产品行为/兼容或主要布局改变。额外拒绝动画WebP/加密PDF/像素数量未作为产品限制；若实际parser要求缩小白名单，需届时说明具体影响并取得同意。

## 已执行与尚未执行

已执行：git只读基线/差异/重复任务查询、Trellis上下文/生命周期代码读取、源36文件路径/有限秘密/两层SHA验证及不可变副本逐字节核验；WSL/Docker/context/compose config只读exit0；RustFS stable tag registry manifest查询两次exit0；9000/9001/19000/19001只读未占用探测；Java/Maven/Node/Chromium安装状态探测；WSL verify-compose.py --help exit0。

尚未执行：所有产品代码/依赖安装/镜像层拉取/RustFS启动/建bucket/迁移/工程test/浏览器AC/云API/git提交/push/部署。全部工程AC当前NOT_RUN，Aliyun本轮DEFERRED。不要从规划审阅或原型59PASS推导产品验收。

## 上下文门禁与最终批准

父及3子任务分别有prd/design/implement，implement/check JSONL仅选真实存在并已读的规范/研究，不放代码路径或空seed规避。具体task validate退出码保存 `research/planning-validation.json`；最终摘要只有四任务都通过才呈现。

本机task validate四个任务均exit0：父14条、publishing8条、storage10条、ui9条，每个implement/check同样有效。容量告警仅database-guidelines38,126bytes与backend研究32,884bytes超过单文件32,768bytes，已在执行dispatch合同加完整原文件子侧读取；不放开全局限制、不移除事实。原5个用户SOP仍未跟踪且未动，产品diff为空。

初始用户指令已经授权创建/更新规划，最终摘要后的后续明确“批准执行”才授权start实际publishing子任务并持续实施/check；本地原型布局和R3已确认选择不重问。非阻塞实际问题最后汇总，不添加兼容/默认禁用门禁；commit/push/部署等未授权动作留到具体可审阅结果阶段，不运行会自动提交的archive/add_session脚本。
