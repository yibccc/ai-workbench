# AI 面试 UI 独立检查报告

检查日期：2026-10-04。角色：独立 trellis-check。用户批准与前端所有权来自主会话派发；未改后端、运维、规范、Git 或 lifecycle。

已读取完整 hook 保存文件、当前 PRD/design/implement、validation、frontend active directory/identity/viewport/dialog 规范、父 frontend-reuse/acceptance/API 合同。按实际代码检查 Profile File/文本/版本、JD tuple、场次状态轴、submit/complete、报告、删除、身份边界、共享 Dialog、导航和局部 CSS。

## Findings (fixed)

- File: `frontend/src/features/profile/ProfilePage.tsx`
  - Issue: 慢 GET 的旧版本虽未更新 current 摘要，仍继续执行编辑器初始化/重置，可能覆盖已经保存的新正文；ACK 已确认新版本但权威 GET 未返回时也有同类时序。
  - Fix: adoption 返回成功/拒绝结果，整个后续正文初始化受同一门禁约束；拒绝低于已确认 editVersion 或已采纳 current.version 的旧读取。
  - Evidence: 新增迟到 GET 回归先实际失败（正文退回 `# 当前 A`），修复后通过；最后将权威 GET 暂停，先释放 ACK 前的旧 GET，再确认编辑器仍保留已保存的新正文。

- File: `frontend/src/features/interview/InterviewSessionView.tsx`
  - Issue: submit/complete 已成功而后续 GET 失败时，旧轮仍可编辑并可发新写请求；同样不能用低于 receipt.resultVersion 的读取恢复写入。
  - Fix: 确证 receipt 后保存 operation/resultVersion，在权威读取达到该版本前阻止下一次写；submit/complete 的旧输入立即锁定。DRAFT 保持正文可编辑，等待权威版本后再开放写按钮。拒绝旧版本 adoption 后不执行保存并离开。
  - Evidence: submit/complete 两个用例先实际失败（textarea 仍 enabled），修复后分别通过；重新读取后采用服务端下一轮或冻结答卷。

- File: `frontend/src/features/interview/InterviewCreateForm.tsx`
  - Issue: JD retry 响应未知后修改 JD，旧 retry tuple 未失效；新分析的首次手动 retry 会误重放旧分析的请求。
  - Fix: 任一文本/方向失效时同步清除旧 retry tuple，与原分析 DELETE/fencing 生命周期一致。
  - Evidence: 新回归先实际失败（新分析未进入成功），修复后只向新 analysisId 发 retry 并显示成功。

- File: `frontend/src/features/interview/InterviewSessionView.tsx`
  - Issue: 评估轮询收到 404 仅显示错误，仍回显已删除场次的旧题单、答案与报告。
  - Fix: 在所有 owner detail/report GET 路径统一处理 404，清除 session/report/文本基线/当前轮/冲突文本/unknown tuple/receipt 等本地投影；同一清理用于写操作的 404。
  - Evidence: 新轮询回归先实际失败（私有答案仍有 1 个 DOM 节点），修复后内容和删除入口均清除。

新增五项浏览器回归均位于 `frontend/e2e/interview.spec.ts`；保留其余既有用例与真实业务链。

## Findings (not fixed)

- 本轮范围内没有遗留的已确认产品缺陷。
- 适用 `.trellis/spec/frontend/` 的五项导航表述、共享 guard、receipt 后权威读取及完整 adoption 门禁需同步；依派发所有权交主会话更新，检查代理未写 spec。
- 默认真实面试 HTTP/PG/Redis/RustFS E2E、原 workbench/focus-alarm/community 全套回归本轮未运行，主会话接续联调；27 项 HTTP fixture 不证明后台 owner/DB CAS/存储/模型调用计数/fencing。
- Vite 约 563 kB chunk 的既有 >500 kB 优化提示保留。代码拆分属于本轮以外的性能设计决策，未调阈值或改公共模块边界。

## Verification

- Lint: **PASS**，最后 `npm --prefix frontend run lint` exit 0，无 lint 错误/警告。
- TypeCheck: **PASS**，最后 `npm --prefix frontend run build` 内 strict `tsc -b` exit 0；Vite 267 modules、build exit 0。
- Tests: **PASS（UI 层）**，`npm --prefix frontend run e2e -- --config=playwright.interview-ui.config.ts`：**27 passed (43.5s)**，workers 1/retries 0。包含原 22 项与本轮 5 项，四尺寸六屏/40 轮几何均通过。
- 最后 Profile ACK/权威 GET 顺序补正后，同配置 `--grep='迟到旧读取'`：**1 passed (2.9s)**；lint/build 再次通过。未无理由重跑其他未受影响用例。
- 首轮四项新增用例 **4 failed**，修复后 **4 passed (6.1s)**；新增轮询 404 用例先 **1 failed**，修复后纳入完整 27 项通过。
- 并行全 fixture 与 lint 曾因 Playwright 删除 `frontend/test-results` 导致 ESLint 枚举 ENOENT；浏览器退出后独立重跑 lint 成功。未放宽 lint/TS/布局断言。

执行环境仅白名单读取隔离 test.env 的 E2E_DATABASE_URL、POSTGRES_USER/PASSWORD、REDIS_PORT，NODE_OPTIONS=--max-old-space-size=512；继承既有隔离 PG/Redis guard。UI 配置只启动经过环境过滤的 Vite，未启动后端、未 reset、未读日常 .env、未调用付费模型/云；浏览器窗口已释放。

## 原型与布局复核

已人工对照六张源 PNG 与六张桌面实际截图，并检查 320px Profile：卡片、状态配色、方向/设置/简历/JD 分组、作答/进度、报告/计分说明、简历/原件/删除分组均沿源结构；原生 account/focus/menu/Dialog 和可达滚动替换演示壳。HTML/PNG 差异采用父 research 的源码优先规则，不以两份矛盾源要求像素同时一致。

只读脚本从源 style 选取内容类规则、去除声明空白后逐条核对：interview/profile CSS 各保留 **82/95** 个源 selector/declaration pair（仅增加 feature scope）。统计包含源响应式重复规则；这个数量只证明内容 CSS 直接复用，不代表业务覆盖率或像素匹配率。

feature CSS 全部以 `.interview-workspace` 或 `.profile-workspace` 限定；无原型全局 reset/shell/demo 运行时代码。移除 1180px cap，继续原生 page/workspace-scroll 高度链。六屏×四尺寸的 24 张稳定截图位于本任务 `artifacts/visual/`；本次完整用例再次验证 document 不纵横溢出、可见 region 正高度、40 轮末项、提前交卷、末条反馈和删除按钮可达。

实际代码复核还确认：四方向/全整数 N=3–20、Unicode 码点且无 maxLength 截断；空 current/空 draft/空 SUBMITTED 的区分；不可变 File 与编辑正文分别 multipart；EDIT_CURRENT 保原件和显式 PASTE 解除关联；JD 失败仅显式 retry、变化后失效且成功分析可多场复用；GET 只读、隐藏场次停止 poll；总分 null 不伪造 0；SafeMarkdown skipHtml 与远图仅 alt 文本；账号根 key/唯一 focus controller、401 Dialog 清理与迟到回调身份门禁保留。
