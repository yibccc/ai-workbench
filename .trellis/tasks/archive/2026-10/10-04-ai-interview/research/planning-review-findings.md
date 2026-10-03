# 正式规划静态一致性审阅

日期：2026-10-04（Asia/Shanghai）。审阅对象：父 `prd.md` / `design.md` / `implement.md` 与已保存的 UI 子任务三份规划；源合同使用父不可变 R1 requirements/acceptance 副本。不重复原型调查、不运行门禁或测试、不修改产品及父规划。resume/engine 子 design/implement 在本轮读取时尚未生成，未对其作完整通过结论。

## 已确认一致性

- 静态逐标题提取源与父 PRD：源 52 条 FR/INT-AC，父 52 条；缺失 0、增加 0、正文变化 0。26 个 FR 与 26 个 AC 正文逐条完全相同。
- 父 `prd.md:216–218`、`design.md:5,126,146`、`implement.md:3,7` 与 UI 规划均将 P-01/实施授权标为待最终真实批准；没有伪造实施许可或产品 PASS。
- 父/UI 均要求原型内容 HTML/CSS 直接转 JSX，保留现有账户根/RetainedView/专注/HTTP/Dialog/Toast/分页，并区分源码/PNG、fixture/真实业务/延期证据。
- backend test schema `d9_interview_tests_20261004` 符合真实 `IsolatedProfileSchemaGuard.java:32–33` 的 `d9_[a-z0-9_]+_tests_[0-9]{8}`；E2E `d9_e2e`、18080/15173、非默认 PG/Redis规则与实际配置一致。

以下保留已发现问题与修复证据。2026-10-04 定点复核父/UI落盘文件后，四项均已解决，空输入语义也已同步；后续已读并定点核对resume/engine六份完整规划（见文末），当前审阅范围没有实质开放问题。P-01仍是唯一需要用户批准的产品入口选择。

## P2 — 普通编辑现有导入简历不应隐式解除/清理原件

**发现时证据**：UI `design.md:52` 原文规定未持有本次原 File 的 PUT 固定为 PASTE，清旧原件引用，提示保存后不再保留原件入口。父 `design.md:50` 却允许不可变原件与最终编辑文本不同，源 FR-002 允许编辑，FR-003 要保存导入原件；源原型没有“刷新后普通编辑即丢原件”的规则。普通 textarea 编辑/粘贴并不等于明确替换简历输入来源。

**实际影响**：导入→刷新→改一字→保存将解除原件、触发删除，成为未确认的额外产品语义；用户仅编辑文本也失去原件下载。

**修正**：PUT 新 API 显式 `mode=EDIT_CURRENT|PASTE`；EDIT_CURRENT 按 expectedVersion 绑定服务端 current，保留既有来源/原件，更新最终正文；只有明确选择粘贴替换才解除原件引用。新 File 只走 multipart import，无兼容默认值。

**状态：已解决（静态复核）**。父 `design.md:49,64` 已明确必填 mode=EDIT_CURRENT|PASTE、EDIT_CURRENT保服务端当前原件、仅显式PASTE/新File替换解除旧引用。UI `design.md:48,52,54` 与 `implement.md:26` 同步相同合同；已保留迟到ACK/revision保护与原tuple重放后owner GET规则。没有因刷新/缺本地File/普通textarea粘贴事件暗改来源。

## P2 — 不存在的复用组件与原型类名须换成真实符号

**证据**：父 `design.md:17` 把 `api/client`、`PageControls` 列为现有复用对象；真实共享请求文件是 `frontend/src/api/http.ts`，现有分页是 `components/Pagination.tsx`，选择控件是 `components/SegmentedControl.tsx`，没有 PageControls。父 `design.md:124` 写 `.panel/.settings` 来自交付原型；原型真实类名是 `.card/.form-section/.option-grid` 等（见既有 frontend-reuse 逐段表）。

**实际影响**：implement/check 代理按正式上下文寻找不存在的复用对象，容易独立重建或错抄原生 `.panel`，偏离用户要求的直接复用。

**修正**：复用表写真实路径/符号；源类名写已核实的 `.card/.two/.form-section/.option-grid/.chips/.row`，UI细节引用 frontend-reuse 映射。

**状态：已解决（静态复核）**。父 `design.md:17` 已改为真实 `api/http.ts request`、Pagination/SegmentedControl；`:124` 已改为 `.card/.two/.form-section/.option-grid/.chips/.row`，对应原型直接迁入片段。不再将 api/client/PageControls/.panel/.settings 作为现有复用对象。

## P2 — 成功创建即消费 JD 分析会新增“未改原文也须重解析”限制

**证据**：父 `design.md:83` 指创建复制快照后可清独立分析正文；UI `design.md:68` 明确 consume/scrub、再次使用同一 JD 须新解析，`implement.md:35` 同步了消费合同。源 FR-007 / INT-AC-018 规定原文修改后失效，未规定创建成功也失效；FR-005/013 允许新建独立多场同类会话。现有 owner+direction+精确 hash+SUCCEEDED 已足以绑定分析来源，不需要一次性消费来保证会话快照独立。

**实际影响**：用户相同方向/JD未改也被迫再点解析、再次产生模型调用；这是技术清理决定带来的新产品限制，没有源确认。

**修正**：成功分析允许同 owner/direction/hash复用，每次create独立复制快照；用户明确修改/换方向/移除/取消解析草稿时清理/fence，创建本身不使有效分析失效。同requestId成功重放仍优先receipt，再读取owner会话权威。更新父、engine、UI、研究决策记录里的consume/scrub语义。若确实保留一次性产品限制，必须在最终摘要明确批准该具体行为，不能标成已确认需求；优先修正规划以保持P-01为唯一产品待决项。

**状态：已解决（静态复核）**。父 `design.md:83` 明确成功分析可在同owner+direction+exact raw hash下多场创建、不因创建成功失效/追加解析；父 `implement.md:18,81` 采用同来源多场复用与明确删除后fencing。UI `design.md:68`、`implement.md:35` 保留当前form配置和成功analysis，无consume或强制重解析；只有明确改文/换方向/移除/确认取消才DELETE。旧研究 `backend-contracts.md:5–7` 已明确正式设计覆盖旧consume/scrub建议，不能将其注入为执行合同。

## P2 — 父验证命令前置必须点名隔离 PostgreSQL 凭据变量

**证据**：父 `implement.md:39–63` 列 schema/URL/Redis/空模型key，但未点名必须已设置 `POSTGRES_USER`、`POSTGRES_PASSWORD`。真实 `frontend/playwright.config.ts:9–10` 缺任一会在启动时直接拒绝；`backend/src/test/resources/application-test.yml:4–5` 缺变量会落到日常默认账号。UI `implement.md:74` 已正确列明这两个变量。

**实际影响**：按父命令块逐步执行仍不能启动E2E，后端测试也可能尝试默认凭据，不能形成父宣称的“实际可用验证入口”。

**修正**：在父命令前置声明已从隔离测试资源设置 POSTGRES_USER/PASSWORD（不在规划写密码），并核对凭据归属相同隔离库；这属于命令文档补全，不需要新产品授权。

**状态：已解决（静态复核）**。父 `implement.md:39` 点名先配置并核验专用 POSTGRES_USER/POSTGRES_PASSWORD，`:65` 列明Playwright四项强制环境前置；UI `design.md:100`、`implement.md:74` 也明确隔离PG凭据，不写密钥。命令本身未运行，结论仅为静态前置完整。

## 空输入语义已同步

主代理已明确去掉研究中未经源确认的最小长度：空 MD可保存、空 draft可暂存、空 submit仍为SUBMITTED并评估，可真实0分+反馈，不能当UNANSWERED。此决定符合源仅规定上限/提交锁定的合同。

**状态：已解决（静态复核）**。父 `design.md:104` 已改为评估所有SUBMITTED（含空字符串）、空草稿可暂存、空提交锁定推进且需有效评分反馈，不能当UNANSWERED。UI `design.md:78,80,82,92` 与 `implement.md:42–43,50` 同步：已答/未答按提交状态，空提交计已答，未提交草稿即使有正文仍未答；空MD正文以exists区别无current，不增最小长度。旧 `research/backend-contracts.md:5–7` 明确0-based turnIndex、PENDING、统一receipt及空输入正式合同覆盖旧1起点/QUEUED/consume/业务非空/空答400建议，防止过期研究注入反改。无须增加用户确认项。

## 复核结论

本次限定范围的四项问题与空输入合同已全部解决，无实质开放问题。后端六份子规划落盘后已按文末定点复核，与父/UI当前mode、JD来源复用、SUBMITTED/UNANSWERED、码点边界、0-based/PENDING保持一致。P-01与完整规划持续执行仍等待最终真实用户批准。

本审阅没有运行产品测试、Trellis门禁或模型调用，不作产品通过结论。

## 最终后端子规划定点复核

已读取 resume/engine 两子任务各 `prd.md` / `design.md` / `implement.md` 六份落盘文件；仅复核最终字段/状态/权限合同，没有重复源码或FR全量调查。engine `design.md:14,31,33,39–41,47` 明确0-based turnIndex、PENDING、空SUBMITTED及SDK maxAttempts(1)；`:24` 和 `implement.md:10` 明确成功JD分析多场reuse。resume `design.md:23,38,45` 明确必填mode、编辑保原件、空正文exists=true；schema/无真实key/隔离凭据继承父且engine `implement.md:20–31` 明确PG变量。engine `prd.md:43` / `implement.md:16,55` 正确遵循父已批准本地提交/归档收尾而不重复询问，外部发布和真实付费未包含。

字段、状态、SDK和输入语义没有发现实质冲突。两处文档精确性问题已落盘并复核解决：resume `prd.md:17` 已正确标 `FR-008 / INT-AC-014`；resume `implement.md:31` 已点名隔离POSTGRES_USER/PASSWORD，`:62` 明确继承父最终已批准的本地提交/归档收尾、不重复询问，push/外部发布/付费另授权。另 `design.md:29` 明确GET只读，无singleton返回exists=false/version=0，首写才创建，保证读取不写业务状态。最终本次静态审阅无实质开放问题；P-01仍等待最终真实批准，产品测试与主代理最后Trellis validate不由本审阅宣称通过。
