# 前端原型复用与真实接入研究

核对时间：2026-10-04（Asia/Shanghai）。本研究只读取代码/附件与保存研究记录；未运行 HTML/脚本、产品测试、模型调用或依赖安装，未修改产品代码。

当前真实基线：`codex/community-oss@0090d0127b0693c780aa3e587e20a6e6bae969fc`。交接包使用的 `master@cf6ca832bdfffe13628489a99e80bbd3bbe9940a` 不是本地当前 HEAD。本地已存在社区/发布空间、严格 hash 路由、公开昵称资料编辑；没有私有简历/个人中心/面试模块。

## 已读资料

- 交接包 `HANDOFF.md`、`requirements.md`（FR-001–026、INT-AC-001–026）、`acceptance.md`、`solution.md`、`core-code-map.md`、`MANIFEST.txt`。
- `prototype/ai-interview-prototype.html` 全部 100 行、`prototype/design-notes.md` 全部 84 行；静态查看全部 6 张 PNG（history/create/answer/report/resume/delete），未执行原型。
- 本地 frontend specs 的 index/directory/identity/community/viewport/dialogs；component/state/hook/quality/type 文档仍是占位模板，应以 active specs 与真实代码为依据。
- `App.tsx`、AppShell/routes/navigation/AccountMenu/RetainedView、community/publishing 的 profile/安全 Markdown/导航 guard、shared HTTP/Pagination/Dialog/Toast/usePagedList/useBeforeUnload、package/TS/ESLint/Vite/Playwright 配置与 E2E 导航断言。

## 核心结论

可以直接把原型内容区 HTML 转 JSX，把内容区 CSS 迁入 feature 样式并限定根选择器，保留原型分组、类名、色值、间距与断点。原型没有业务实现：唯一 JS 是 `render()` 的 hash 切屏（HTML:96–98），其余按钮多数无 handler 或直接跳静态页，不能作为服务端状态或 API 契约。

必须复用现有 `AppShell`、账户根、RetainedView、focus controller、HTTP、Dialog、Toast、Pagination；不要复制原型 shell、sidebar/topbar、全局 reset、演示账户/计时/样例数据或安装新 UI/Markdown 库。CSS/JSX 生产文件不能运行时读取 `.workbench/inbox` 或 Trellis task 路径。

P-01 仍需最终批准：AI 面试为第六个 retained 主工作区；个人中心为账号菜单进入的辅助视图。当前研究仅给出具体改动点，不视为批准。

## 可直接迁入的原型片段

下表行号均指 `prototype/ai-interview-prototype.html`；候选组件名称是设计建议，尚未存在。

| 原型片段 | 可迁内容/类名 | 候选目标 |
|---|---|---|
| 34–47 面试中心 | `page-head`、`tabs`、`kpis/kpi`、`grid two`、`card card-pad`、`list`、`session/session-main/session-top/session-actions`、`meta`、`status running/done/failed/waiting`、`progress`、`resume-box`、`note` | `features/interview/InterviewWorkspace.tsx`、`InterviewHistory.tsx`、`InterviewSessionCard.tsx`、`CurrentResumeSummary.tsx` |
| 49–58 创建 | `form-card/form-section`、`option-grid/option`、`row`、`resume-choice/resume-box`、`jd-result`、`chips/chip`、`form-actions`；四方向文字和快照/固定追问说明 | `InterviewCreateForm.tsx`、`JdParsePanel.tsx` |
| 60–66 作答 | `answer-layout`、`question-card/q-type`、`locked`、`answer-actions/right`、`side-card`、`timeline/turn done/current/future`、提前交卷说明 | `InterviewAnswer.tsx`、`InterviewProgress.tsx` |
| 68–72 报告 | `report-hero`、`score-box/score`、`report-copy`、`feedback-list/feedback`、计分说明 | `InterviewReport.tsx`；把逐轮原题/答案/反馈/可选参考要点补入既有反馈结构 |
| 74–85 简历 | `resume-layout`、`resume-editor`、`file-card/file`、原型 textarea、保存/删除分组、快照说明 | `features/profile/ProfilePage.tsx`、`ResumeEditor.tsx`、`ResumeImport.tsx` |
| 94 删除弹窗内容 | 标题、影响范围、取消/确认按钮分组；`danger-note note` 的视觉可以保留 | shared `useDialog` 或 `Dialog`；不迁 `modal-backdrop/modal` 自建模态壳 |
| 9 内容区 CSS / 10 响应式 | 内容卡、状态、题目/报告/简历布局、900px 单列与 520px 表单单列规则 | `features/interview/interview.css` / `features/profile/profile.css`，以 `.interview-workspace` / `.profile-workspace` 根作用域限定原选择器 |

HTML:8 的变量与真实 `styles.css:2–13` 已基本相同；直接使用既有变量即可。HTML:9 的 reset/通用表单/`.sidebar/.topbar/.brand` 等不可重新全局注入。`.card/.grid/.row/.status/.progress/.note/.tabs` 等通用类名若保留须限定 feature 根，否则会影响已加载但隐藏的其他工作区及社区。

原型 `.wrap{max-width:1180px}` 与本地 viewport spec 的桌面全宽布局相冲突；保留内容分组，去掉居中宽度 cap。原型整页 `.content{overflow:auto}` 把标题也纳入滚动，与原生固定页头合同不同：应接入 `.page` / `.page-header` / `.workspace-scroll` 高度链（`styles.css:118–144`），给滚动区 `role="region"`、`aria-label`、`tabIndex={0}`，内容卡/长 textarea/40轮 timeline 独立有界滚动。不能只把 shell 设 overflow hidden 后裁掉底部动作。

## HTML 与 PNG 的差异

6 张静态图均为 1440×1000 视觉参考，不构成任何交互/真实业务证据。它们与当前 HTML 有可见差异：PNG 页面没有 HTML 的 tabs；history 的列表明显比右侧摘要更宽，HTML `.two` 是 `1.15fr .85fr`；history PNG 没有 HTML 的删除/查看答卷按钮；PNG create 将 JD 解析结果并列，HTML 是 textarea 下方；PNG report/answer 对动作和示例文案略有调整。resume PNG 中示例文字有局部重叠/裁切。

因此复用以交付 HTML/CSS 为代码来源，PNG 校验颜色、分组、层次与目标视觉，不同时承诺两份不一致源的像素几何一致。最终真实 React 页在桌面/窄屏/短屏重新截图，记录必要原生布局调整及真实数据与示例差异。

## 必须替换的模拟状态/行为

| 原型当前行为 | 正式实现要求 | 对应合同 |
|---|---|---|
| history 3 条硬编码数据/KPI 2/1/7、假日期/总分 | owner-scoped 服务端分页与真实状态。列表 5 项一页；KPI 若保留必须由同一 owner 完整统计，不拿当前页条数冒充总数 | FR-013/019/022；AC-001/016/026 |
| 面试中心与历史记录都跳 `#history` | 用明确的列表/完成状态过滤或视图状态，选择会话必须携带真实 id；新建始终独立 create，不自动选择旧会话 | FR-005/013 |
| option/resume-box 是无 handler 的 div | 改为有 labels 的 radio/fieldset 或 button + 正确 aria，支持键盘/选中/禁用；只有四方向，当前简历缺失则提供可用通用面试与进入个人中心动作 | FR-004/006/010；AC-014/017/020 |
| 主问题 select 只有 3/4/5/10/20 | 完整允许整数 3–20，默认 5；说明、create 按钮和总轮数随 N 动态变化 | FR-006/009；AC-002/025 |
| JD 空文本已显示 Java 成功解析；解析按钮无动作 | 仅显式 parse 调 API；保存请求绑定方向/JD 原文摘要；任何字符或方向变化使旧解析失效；忽略迟到旧解析；失败/不匹配保留文本、阻止带旧分析创建，用户明确清空才无 JD；只手动 retry | FR-007/008/018；AC-018/019/023 |
| create 直接跳作答 | 点击创建才写会话；同步 pending 锁 + disabled；保留 requestId 与完整 tuple 直到结果确认；服务端 expectedResumeVersion 校验；响应丢失先读取/replay 原请求，不新建重复场次；出题处理/失败/重试都有真实状态 | FR-005/008/009/018/025/026 |
| 作答题/答案/计数都是样例，timeline 仅示范 6/10 | 按服务端固定题单显示精确 2N 轮、MAIN/FOLLOW_UP parent、currentTurn；显示草稿/锁定答案；逐场状态独立，不能由另一场的请求覆盖。恢复通过纯 GET | FR-009/011/012/013/019；AC-002/003/004/026 |
| 保存草稿/提交按钮无动作；“保存并离开”只是跳 history | 服务端草稿成功才标记已保存；save-and-leave 失败保留输入并留在当前编辑；提交携带 turn/version/requestId，成功读服务端后锁定推进；丢 ACK 保留原 tuple 并读真实状态，禁止本地盲增轮次 | FR-011/012；AC-003/004/023 |
| 提前交卷按钮无确认 | shared Dialog 明确已答/未答计数、未答为0；取消不写；成功冻结；最后轮提交触发首次评估；pending 不重复交卷或解锁 | FR-014/015/017；AC-005/007 |
| 列表评估失败“手动重试”直接跳成功报告 | 保持冻结答卷；报告含等待/处理中/失败/成功/部分有效组；必要结果缺失不显示总分、不假0；只 explicit retry；历史 GET 不调用模型 | FR-015–019/025/026；AC-006/007/016/024 |
| report 仅3条示例反馈，全部作答且总分82 | 完整 2N 轮显示已评估/未作答/未评估区别；逐轮有效评分0–100；总分来自后端；未作答固定0并标记，不生成虚假技术错误 | FR-016/017；AC-006/007/025 |
| 简历 SHA/RustFS/已生效/文件大小/字符数静态 | 当前生效版本与本地编辑草稿分开；实际 File.byte size 和码点 count；元数据仅来自后端确证；导入必须 UTF-8 .md + 1MiB，保存文本仍20k；粘贴不造对象 | FR-001–004/021/023；AC-011–014/020/022/023 |
| 选择文件、放弃、保存、删除简历按钮无动作 | File 先入本地编辑器，可改后点保存才替换 current；失败不盖旧版本/当前编辑输入；expectedVersion 冲突显式读取保留输入，用户 retry；放弃明确回到最后有效版本；删除 shared Dialog、历史快照保留 | FR-002/004/021；AC-011/013/020 |
| 删除确认只有静态壳且确认无动作 | 任一阶段均可删除 owner 会话；pending 防重；成功清除选中会话投影并刷新列表；后续刷新失败单独提示；404不继续回显旧私有数据；后台 fencing 在后端验证 | FR-020/021/025；AC-001/021/024 |
| 原型 hash 切屏/render + onclick + 硬编码账号/focus | React state/已批准严格路由、现有 AccountMenu 与真实 focus compact；不添加 #history/#resume 等旧原型 alias，不用 innerHTML 注入资料 | FR-022/024；AC-008/010 |

码点计数不能用 JS `text.length` 或 textarea `maxLength`（它们按 UTF-16 单元）；使用 `Array.from(text).length` 或等价码点计数，超限保留输入并拒绝写/推进，涵盖 emoji 边界。UTF-8 文件可本地 `TextDecoder('utf-8',{fatal:true})` 先检验，服务端仍是权威。编辑器保留原 File 与编辑文本，导入 API 的正式 multipart 合同需要支持保存最终编辑文本与原件的绑定；不能选择文件即替换 current，也不能只 PUT 文本而丢失用户要求的导入原件。

简历/回答/题目/模型反馈作为文本渲染。若添加 Markdown 展示复用现有 pinned `react-markdown` 与 skipHtml/URL限制思路（`PublicationDocument.tsx:17–29`），不直接复用带社区作者/附件/公开链接逻辑的整个 PublicationDocument；禁止远程图片自动加载及 HTML/脚本执行。单纯 resume 编辑 textarea 不需要新增 preview 功能。

## 真实接入路径与符号

| 文件/行号 | 事实与复用/修改边界 |
|---|---|
| `frontend/src/App.tsx:25–32,35–55` | parseHash 在账户读取前已解析目标；正常已知 target 登录后可恢复，真正401/网络启动失败不同 |
| `App.tsx:59–127` | 登录/退出/401通过 shared HTTP identity epoch 和 closeRealtime 清理；`Workspace key={account.id}` 是账户生命周期边界 |
| `App.tsx:130–177` | Workspace 内项目共享状态；当前 `communityGuard` 单注册槽 + hashchange async guard。新增 profile/answer dirty 或 pending 离开 guard 应扩展共享注册机制/移到共享类型，不能从面试 feature 依赖 publishing 的业务实现；只注册当前活动编辑上下文，隐藏 retained 工作区不强迫用户丢草稿 |
| `App.tsx:178–186` | 导航只重置可见区域滚动和焦点，不重建组件；新页采用 `.workspace-scroll` 可继续生效 |
| `App.tsx:189,207–234` | 唯一 `useFocusController(account.id,…)`、compact、铃声/微休息 overlay 均在账户根；不得搬入面试/Profile或因页面切换重建 |
| `App.tsx:215–227` | 五工作区 RetainedView + 一个 community retained subtree + ADMIN users；新增 interview retained subtree 与 profile retained辅助页保持同一路径 |
| `components/RetainedView.tsx:3–7` | 首访挂载，hidden 后保持状态；不使用变化的 key 刷新列表或重置屏幕 |
| `components/layout/navigation.ts:3–10` | PageId 与导航均仅五项；P-01获批后增加 interview，已有 Icon `sparkles` 可直接用（`components/Icon.tsx:3,9`） |
| `components/layout/routes.ts:4–15,16–28` | 严格已知 hash parser；`isCommunityView` 目前是所有 '/' prefix + unavailable。推荐独立 `profile` 辅助 view；若选择 `/profile` 或 `/interviews` 必须收窄 isCommunityView，否则错误进入 community shell。子路由若设计采用，需严格 UUID模式/routeTitle/isInterviewView，并让主导航高亮其父工作区；不要宽泛 startsWith接收任意路径 |
| `components/layout/AppShell.tsx:10–18,23–37` | 复用现有 shell和共享菜单；加 onProfile并透传。menu在桌面sidebar footer/mobile topbar/community chrome共享；保留space switch、focus compact、真实 date |
| `features/auth/AccountMenu.tsx:4–24` | 现有用户管理/密码/退出/工作台/广场；新个人中心按钮使用 action()关闭菜单后导航。保持 Escape/触发焦点/ADMIN规则/data-no-activity logout |
| `features/community/CommunityPages.tsx:20–35,60–74` | author主页和 ProfileDialog仅公開 nickname/bio，不是私有简历。resume应另起 profile feature/api，不塞 PublicProfile/公开 DTO或社区帖子 |
| `api/community.ts:5,16,25–27` | 现有 public profile接口 `/api/me/community/profile`；不可复用为简历 endpoint |
| `api/http.ts:10–22,28–36,58–100` | request/requestBlob同源 Cookie、CSRF、identity epoch、调用方 AbortSignal、安全 ApiError(currentVersion)，可直接用于 interview/resume JSON/multipart；禁止自建 fetch旁路 |
| `hooks/usePagedList.ts:5–41` / `api/pagination.ts:1–9` / `components/Pagination.tsx:3–15` | 可直接用于历史分页、一次请求所有权、Abort/late响应丢弃、末页删除回退；WORKSPACE_PAGE_SIZE=5，pager置于scroll rows外 |
| `components/Dialog.tsx:4–17` / `dialogContext.ts:3–14` / `main.tsx:5–10` | 共享原生模态与provider已安装；取消focus/Escape/busy/危险确认遵守spec，不用confirm/alert/prompt |
| `components/toastContext.ts:14–22` | useToast toastRef避免隐藏retained页的迟到请求弹消息；储存失败、validation、dirty保留当地提示 |
| `hooks/useBeforeUnload.ts:3–9` | 可直接保护尚未保存的resume/答案；应用内离开仍用共享Dialog/route guard |
| `App.tsx:91–124` | 全局明确交互才signalActivity（60s限制）；被动轮询/API读取不能主动调用它，新功能保持规则 |

新候选 API 文件 `api/interview.ts` / `api/resume.ts` 统一 DTO/请求；新UI逻辑位于 `features/interview/` / `features/profile/`，不要堆入 App 或扩张根 API。RustFS/将来OSS完全在后端 ObjectStorage之下，前端只认识 owner业务 API/安全元数据；不持 AWS/阿里云凭据、endpoint/bucket/presigned链接或provider-specific分支，也无需“切换云”UI。

## P-01 的确切影响

1. navigation增加第六项；ViewId/parser/title增加已批准的 interview/profile路由。子页路由是本地设计选择，应在design明确；原型hash不作为旧版兼容合同。
2. App新增InterviewWorkspace/ProfilePage的retained视图、guard协调；保留account-key/唯一focuscontroller和五现有页。
3. AccountMenu增加个人中心，AppShell所有布局透传；profile是私有辅助页，不变成社区作者主页。
4. `styles.css:455` 当前窄屏是 `repeat(5,minmax(0,1fr))`，增加第六项需重配六列/可达布局。不能复制原型900px隐藏侧栏导航；320×520、390×520、760×700、1440×900都验证六项、账号入口与专注正文正高度/最终按钮可达；不降低既有几何断言来绕过压缩。
5. `frontend/e2e/workbench.spec.ts:479–500` 标题/484断言五项；`:2212`、`:2266`也断言五项。批准后更新为六项，同时保留五原页行为断言并增加面试/Profile回跳验证。`focus-alarm.spec.ts`、community账号/原草稿保留流程继续运行。
6. frontend specs `directory-structure.md:36`、`identity-session.md:5,16,36,54`、`viewport-layout.md:5`、`community-publishing.md:10,16,26,52`存在五项字样；获批实现后更新新合同，不能把尚未批准P-01写成现有事实。

## 前端遗漏与验收补齐

原型涵盖主流程的视觉与说明，FR/AC并未因原型缺状态而被删除。正式计划至少补齐：空当前简历、列表空/加载/读取失败、JD idle/解析中/失败/方向不匹配/失效、出题pending/处理中/失败/手动重试、40轮长题单、FOLLOW_UP作答、保存失败/冲突/丢ACK、双标签页旧版本、提交成功/锁定、提前交卷确认与取消、最后轮交卷、评估等待/处理中/部分失败/未评估/未作答、所有状态可删除、简历空/dirty/导入失败/保存失败/替换冲突/删除确认、登录恢复与A→B迟到响应隔离。不要用原型点击跳转替代这些验收。

26个AC在前端的覆盖建议：

- **Browser主负责**：AC-004、005、008、011、018、019、023、026；通过真实API/隔离库验证可恢复草稿、交卷说明、retained/focus、文件选择保存失败保留、JD失效与手动移除、Unicode码点边界、多场独立。
- **Browser + API/DB共证**：AC-001、003、009、012、013、014、020、021、024；模拟迟到/丢ACK/409/404/401，但不得把HTTP fixture记为真实owner/存储/fencing PASS。
- **服务端/AI契约主负责，UI必须正确展示**：AC-002、006、007、010、015、016、017、022、025；计数fake gateway证明读取不调用模型，错误结构禁止总分，服务端完整题单/等权得分/快照/清理/最大容量结果。前端不能用本地示例评分冒充。

建议新增 `frontend/e2e/interview.spec.ts` 覆盖原型六屏及上述补全状态；存储/AI测试由后端agent实施，Browser必须至少有独立真实HTTP+DB的多场恢复/快照/原文件/删除链，fixture仅用于可控错误和几何，不与真实业务证据混记。

## 已存在的验证入口（本次未执行）

在 `E:\projects\workbench\frontend`：

```powershell
npm run lint
npm run build
npm run e2e
npm run e2e -- e2e/interview.spec.ts
npm run e2e -- e2e/workbench.spec.ts e2e/focus-alarm.spec.ts e2e/community.spec.ts
```

`package.json:6–12`：lint=eslint，build=`tsc -b && vite build`，e2e=playwright。TS strict/noEmit已开启（`tsconfig.app.json`）。无需独立新增typecheck脚本才能验收。

E2E前置强制来自 `playwright.config.ts:3–11`：loopback的显式 `E2E_DATABASE_URL`，非5432端口、独立数据库（非ai_workbench）且currentSchema=d9_e2e；非6379 `REDIS_PORT`，POSTGRES_USER/PASSWORD。配置自动启动backend e2e profile(:18080)与隔离env的Vite(:15173)，workers=1，test-results/和playwright-report/；**测试重试一次是Playwright test级重试，不授权产品自动模型重试**。原有README:191–202说明隔离卷与 deterministic model substitute；新功能需后端e2e假网关覆盖，不能把 `live-acceptance` 真实付费调用混入常规检查。

`frontend/e2e/local-acceptance.mjs` 是现有安装的登录只读smoke，断言空projects/单ADMIN，不适合作为面试业务验收的替代。`scripts/local/browser-env-check.mjs` 可核对前端环境边界；前端child不能获得DB/model/storage/root/AWS凭据。

此研究没有声称任一验证已通过。
