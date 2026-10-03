# AI 面试 UI 技术设计

## 上位合同与当前事实

继承父任务 `../10-04-ai-interview/prd.md`、`design.md` 的 FR-001–026、INT-AC-001–026、owner/版本/幂等/状态/API 合同。接口路径、状态枚举、错误语义及 requestId 恢复以父设计和后端 DTO 为单一来源；本文件只规定 UI 如何消费，不创建另一份后端生命周期。已核对的真实文件/符号和原型行号见父 `research/frontend-reuse.md`，不重复探查相同问题。

P-01 待最终批准：`interview` 是第六 retained 主工作区；`profile` 是账号菜单进入的辅助视图。以下产品改动仅在批准后实施。本轮前端不持有 RustFS/OSS endpoint、bucket、凭据或对象 key，不创建云切换设置。

## 原生入口与生命周期

新增严格 hash `#interview` 和 `#profile`，分别进入面试主工作区和私有个人中心。个人中心入口放在现有 AccountMenu，关闭菜单后通过共享导航进入；不是社区公开作者主页，也不是第七主导航。面试中心/创建/作答/报告通过 `InterviewWorkspace` 内局部 view state 与真实 `selectedSessionId` 切换，继续/报告必须先 owner GET，刷新后回到面试中心再从服务端继续。首版不增加会话深链/原型 hash alias。

`PageId`/导航只增加 interview；`ViewId`/parseHash/title 明确接受 profile。不能用宽泛 `startsWith('/')` 让 profile 误入社区，也不能接受任意未知 interview 路径。真正 401 仍走共享登录清理；有效 hash 经过登录恢复与非法路由反馈仍按现有根合同。

账户根保持 `Workspace key={account.id}` 和唯一 `useFocusController(account.id, ...)`；新增两个 RetainedView 在同一账户树中首次挂载、隐藏保留。App 仅组装/导航/guard/专注，不存面试分页、简历编辑或模型请求逻辑。切换页只重置可见滚动和焦点，不用变化 key 刷新业务。

App 当前 communityGuard 单注册槽需提升为共享、按活动上下文调度的离开 guard。共享注册类型不得依赖 publishing 业务类型；每次导航只调用当前可见编辑器 guard，不让隐藏 retained 草稿阻拦其他页。一个上下文同时 dirty/pending 时只显示一次共享 Dialog。取消不导航；保存并离开仅在成功确证后导航；Browser beforeunload 复用现有 hook。离开 guard 本身不丢弃本地 File 或 requestId。

## 组件与数据所有权

| 候选文件 | 责任 |
|---|---|
| `frontend/src/api/resume.ts` | current 读取、版本文本保存、multipart 导入保存、删除、原件安全 Blob 读取；DTO 与错误来自正式后端合同。 |
| `frontend/src/api/interview.ts` | JD 解析/状态/移除/重试，场次列表/创建/详情，draft/submit/交卷/报告/删除/retry；统一 request 与 DTO。 |
| `frontend/src/features/profile/ProfilePage.tsx` | current 首读、已生效版本/本地编辑边界、状态/错误、dirty guard 和摘要。 |
| `ResumeEditor.tsx`、`ResumeImport.tsx`、`profile.css` | textarea、码点计数、File 校验/显式保存/放弃与原型范围样式；不把原件当公开 attachment。 |
| `frontend/src/features/interview/InterviewWorkspace.tsx` | 子视图、selectedSessionId 和当前活动上下文；协调相应 owner GET，清除不可读场投影。 |
| `InterviewHistory.tsx`、`InterviewSessionCard.tsx` | owner 分页/状态过滤、真实状态、continue/report/delete，Pagination 使用 size=5。 |
| `InterviewCreateForm.tsx`、`JdParsePanel.tsx` | 四方向/难度/N/currentVersion/JD，解析失效，create pending tuple；不承载临时简历。 |
| `InterviewAnswer.tsx`、`InterviewProgress.tsx` | 固定 2N 题单/currentTurn、每场/每轮草稿与锁定、pending 版本/requestId、提前交卷。 |
| `InterviewReport.tsx`、`interview.css` | 完整轮次评估展示、失败/部分有效结果、手动重试与范围样式。 |
| 共享导航/guard 文件 | PageId/ViewId/parser/title/AccountMenu/AppShell 透传和活动编辑器 guard；不塞 feature 业务状态。 |

组件拆分随实际大小调整，不为了表格生成空转发文件。逻辑归属边界固定；局部 hooks 可以管理 feature 的 request ownership 与状态，不能建立第二套全局 fetch/身份/Toast/provider。

## 直接复用的 HTML/CSS

迁交付 HTML 内容区为 JSX，保留原型卡片/列表/配置/题目/报告/编辑器的结构、类名、色值、间距和 900px/520px 分组规则；从原型删除 shell、sidebar/topbar、全局 reset、演示 tabs、onclick/hash render、静态账号/计时和示例题/评分。代码迁入 feature 文件后不再运行时读取 `.workbench/inbox` 或任务目录。

原型通用 `.card/.grid/.row/.status/.progress/.note` 类名全部限定于 `.interview-workspace` 或 `.profile-workspace`；通用 CSS 变量复用真实 styles，不全局再声明。删除原型 max-width:1180px cap，按原生桌面可用宽度布局。页头固定，内容进入 `.page` → `.workspace-scroll` 的有界高度链；每级可伸缩祖先 min-height:0。卡片 rows/timeline/长 textarea 内滚动，pager 在 rows 后正常布局；每个滚动区有 role=region、aria-label、tabIndex=0。

HTML 与 PNG 几何有差异，交付 HTML/CSS 是代码来源，PNG 只校验视觉层次、配色和分组。真实数据更长、共享原生 Dialog、原生高度链、六项导航的必要调整写入验收证据，不承诺矛盾来源同时像素一致。原型删除弹窗只迁标题/说明/按钮文案和危险色，壳由共享 Dialog 提供。

现有窄屏五列导航批准后调整为六项均可达的布局；320×520 也保持主体正高度，不能用原型 900px 隐藏导航。既有专注几何/按钮可达断言继续有效，不降低断言来满足导航压缩。

## 私有简历编辑与保存

生效 current `{version, markdownText, source metadata}` 与编辑草稿 `{mode, text, selectedOriginalFile, baselineVersion, revision}` 分离。异步 current 首读成功后才初始化编辑；有current时普通文本编辑显式选EDIT_CURRENT，无current时提供显式PASTE文本输入；导入File为独立IMPORT保存路径。后续GET只更新生效baseline，不覆盖dirty输入。版本/哈希/文件字节/来源只显示服务端确证信息，本地未保存File不标成已生效。

文件选择先检查 `.md`、File.size≤1,048,576、UTF-8 fatal decode 及正文≤20,000码点。失败保留当前生效简历和已有编辑；成功把不可变原 File 与其文本置入编辑器，后续输入只改 text。已有 dirty 文本被导入替换前先显式确认。选文件不写后端、不替换 current；编辑后的 File 模式显式保存发送 `POST multipart {file, markdownText, expectedVersion, requestId}`，绑定原件真实字节与实际保存文本，不重新把编辑文本伪装为原件。

未选择新File的文字保存发送 `PUT /api/me/resume {mode,markdownText,expectedVersion,requestId}`；mode是新API必填的EDIT_CURRENT或PASTE，不设置服务端默认或旧版兼容。EDIT_CURRENT按expectedVersion绑定server current，保留既有sourceKind/current原件关联，编辑正文与不可变原件分别存储，原件下载仍是原始字节。用户显式选择“粘贴替换”才用PASTE，操作前说明“将保存粘贴文本并移除当前原件关联”，成功后清旧原件有效引用并按后端耐久规则清理。模式切换遵守dirty保护，不能因一次普通textarea粘贴事件、File不在本地或刷新就暗改为PASTE。

新File仍只走multipart import；导入成功后继续编辑使用EDIT_CURRENT而非重复上传同一原件。成功ACK只清其对应File选择，若用户在请求期间选择/编辑了新版输入则保留新版revision。所有保存成功后按返回sourceKind/owner GET刷新来源/下载入口，失败仍保留旧current/原件。不能从此前current文件名/大小重建原件，也不能伪造File/Blob/RustFS对象。新选择File的multipart保存期间固定完整tuple/requestId；双击用同步ref锁+disabled防重。未知响应保留原tuple，通过同ID重放原PUT/import确认最小receipt结果，再owner GET current采用当前权威；不能猜新增恢复endpoint，也不能换新key造第二原件。

ACK 只更新其提交文本对应的 saved baseline；用户保存期间又编辑时保留新版输入/dirty，不以成功消息清掉。409 保留编辑、读取 current version，提示用户确认后再发新操作；不自动覆盖他页修改。放弃才清除待保存 File/编辑并恢复最后确证生效文本。删除使用 shared Dialog，业务成功后 current 显示为空且无简历创建可用；历史快照不变，物理原件清理/备份状态由后端验收，UI 不宣称已物理删除。

若原件入口保留，仅通过共享 requestBlob 的 owner 业务 endpoint 下载；URL 对象在替换/卸载/账号清理时 revoke，decode 后再次身份检查。没有公网/预签名对象 URL、inline 自动预览或 provider-specific 读取分支。

## JD 与创建操作

创建 form 含四方向、三档难度、N 全整数 3–20、current/none、可选原始 jdText。总轮次展示 2N。current 选择记录用户看过并确认的服务端版本；创建不上传 resumeId、旧正文或他人数据。版本已变读取后让用户确认 current，不 silently 更新确认版本。

JD panel 的本地 idle/dirty与后端 PENDING/PROCESSING/SUCCEEDED/FAILED分开，安全中断错误显示为失败/中断待手动重试，不创造新的服务端枚举。显式 `POST /api/interviews/jd/parse {direction,jdText,requestId}` 返回202后，仅 `GET /api/interviews/jd/{analysisId}` 查看持久状态。每次解析固定 exact jdText/direction/requestId及表单revision。任意字符变动、清空或方向改变立即使旧成功分析不可用于create，取消旧GET ownership并忽略迟到结果；显式编辑/换方向/移除/取消创建时对原analysis调用 `DELETE /api/interviews/jd/{analysisId}` 清正文与fence，未确认清理的ID保留待确认记录和本地安全提示，不再绑定create。即使改回旧字串也要求用户显式重新解析，避免恢复旧确认。难度/N变化不改变方向内分析输入，但该次create tuple仍重新确认。

成功analysis的精确ID、原文/方向指纹按父合同与form绑定；非空JD在未解析/失败/不匹配/失效时阻止创建并保留原文。显式移除JD清空本地原文/分析并调用删除合同；DELETE不能触发模型。失败仅点击 `POST /api/interviews/jd/{analysisId}/retry {requestId,expectedVersion}`，查询/轮询不调用parser。只有用户明确移除才以无JD创建，不能catch error自动丢JD或改变方向。

create点击同步防重，固定父POST配置/currentVersion/JD绑定tuple/requestId。只有用户发起new才产生场次，未完成场存在不重用。创建事务复制JD快照后保留当前创建草稿的成功analysis，创建成功不会使其失效或consume；同owner+direction+rawhash可重复用于新建多场，UI保留当前form配置与可用parse，不强迫重新解析。只有任一字符/方向变更、明确移除或确认取消创建草稿才DELETE原analysis并fence；已建各场使用自己的copy不受影响。

响应未知保留旧tuple，同requestId重放 `POST /api/interviews` 后owner GET场次，不猜不存在的receipt查询endpoint；服务端先检查create receipt再校验analysis有效性，后续analysis变化也不妨碍原创建确认。只有上一创建确证后用户显式新建才使用新create requestId；不得为未知结果换ID。恢复ACK不代表当前权威，需GET判断已删除/新状态后adoption。出题显示generation状态，UI不自行补题、增加追问或触发动态出题。

正式场次状态分轴消费：generation=PENDING/PROCESSING/SUCCEEDED/FAILED；answer=NOT_READY/READY/IN_PROGRESS/COMPLETED；evaluation=NOT_STARTED/PENDING/PROCESSING/SUCCEEDED/FAILED。生成SUCCEEDED才可作答，COMPLETED永久冻结，评估失败不回到可答；出题retry和评估retry仅对应父FAILED+requestId/expectedVersion端点。

## 作答、交卷与报告

所有异步结果绑定 `{owner epoch, sessionId, selected turnId, request generation}`；页切换/选场/删除使旧 generation 失效。每场已读题单、当前轮、saved baseline 与本地草稿各自隔离；切到另一场不能把旧场 ACK写入新场。服务端是固定题单/当前轮/锁定的权威，UI只对当前可编辑轮开放输入。

PUT draft发送父合同规定的turn/version/全文tuple，空草稿合法；成功只更新saved baseline，不锁定、不推进。保存并离开先保存确证再回列表；失败/冲突保留文本与当前上下文。提交固定requestId/turn/version/text；不加非空或trim门禁，空字符串可提交，成功仍按SUBMITTED锁定/推进。pending期间同步防重。成功读取服务端当前状态再显示锁定和下一轮，不本地 `currentTurn++`。submit丢ACK保留requestId与原tuple，读取submitted状态/服务端轮次判断成功，不能凭正文非空推断；未成功仍本轮，以父恢复规则重放，禁止第二requestId盲发。409读当前权威且保留本地输入供查看/复制，不覆盖已锁定答案。

提前交卷Dialog从当前已确认答卷的submitted状态展示已答/未答轮数（提交空文本也计已答），说明未提交轮次按0；未提交的草稿即使有正文仍是未答。取消零写。最后轮成功或明确finish成功后答卷冻结，交卷首次评估由后端触发，UI不再补发evaluate。UNKNOWN finish也先读取确认。报告GET、状态轮询和刷新均只读；出题/评估失败露出独立显式retry按钮。

报告轮次分别展示“有效评分0–100”“未提交/未作答（0）”“未评估/评估失败（无分数）”；主问题/追问关系与固定快照一致。已提交空文本可标“已提交（空内容）”，仍需有效模型评估，可得真实0及具体反馈，不能自动视为UNANSWERED或伪造评分。必要评分未完整不显示完整总分，保留已确证有效分组与原答案但不拼出虚假均值。成功总分只显示后端值，按后端FR-017固定等权规则说明，无本地算分兜底。失败答卷被冻结，retry不重开作答或更新资料快照。

## 删除、读取与身份

所有阶段场次均允许确认删除；Dialog 明确题单/答案/JD/报告/简历文本快照范围，不影响 current 和其他场。同步防重，取消无写。成功先清除选中场投影/草稿/待确认请求/追踪，列表刷新失败单独提示可读重试；foreign/deleted 404不继续回显旧私有题目或报告。迟到回调不能恢复删除对象，后端 fencing 另行验收。

HTTP 和 multipart/Blob 都使用共享 request/requestBlob，保留同源 Cookie/CSRF/Abort/identity epoch。真实 401卸载账户树和清理，403/404/网络/409各自显示安全错误；保留原五秒 Toast与 visible toastRef，不在隐藏 retained view 弹消息。后台状态 GET 的生命周期仅追踪当前 owner relevant IDs，离开/账户变更关闭，自动通信不调用 auth activity。状态轮询仅 GET，间隔与后端异步合同一致，不建立重复 STOMP 或新通知系统。

## 输入与文本安全

简历/JD/答案的UI count使用 `Array.from(text).length` 或等价Unicode码点计数，分别20,000/10,000/5,000。textarea不设置会截断文本的maxLength，超限仍保留全文、显示错误并禁用写/推进；服务端再校验。不加源合同未确认的非空/最小长度门禁；空简历正文以exists与“无当前简历”区分，空草稿可保存，已提交空答案仍是SUBMITTED。emoji与代理对边界加入验收，文件bytes与字符数分别显示。File导入的原件解码正文和最终markdownText均独立≤20k；字节≤1MiB不能替代正文门禁。合法UTF-8每码点至多4bytes，1MiB字节等号只证明validator字节门禁，不承诺能构造同时≤20k的1MiB正文通过完整import；完整import必须同时满足各门禁。

正文默认 textarea/文本节点。题目、回答、模型反馈需要 Markdown 时抽取现有 pinned react-markdown/skipHtml/普通 URL约束的共享安全渲染思路，不复用 PublicationDocument 的公开作者/附件业务壳；不引入 raw HTML/GFM、不使用 innerHTML，不执行代码，不加载远程图片、不自动抓取链接。简历编辑不增加未要求的 preview。

## 独立验证与依赖

UI skeleton 在正式批准后可并行迁移；真实联调以 resume → engine 已冻结 DTO和独立服务端验收为前置。`npm --prefix frontend run lint` 与 `npm --prefix frontend run build` 是真实质量命令，build已包含 strict TS检查。新增 `frontend/e2e/interview.spec.ts` 覆盖 PRD 的 26 项前端分工与六屏；原 workbench/focus-alarm/community继续回归。

E2E运行只使用显式隔离E2E_DATABASE_URL（loopback、非5432、非ai_workbench、currentSchema=d9_e2e）、非6379 Redis与合成账号，并事先配置该隔离PG的POSTGRES_USER/PASSWORD凭据，文档和证据不写密钥。现有后端测试schema guard普通隔离测试为 `d9_*_tests_YYYYMMDD`；不能把d9_e2e改给常规测试或降低guard。Vite子进程环境只保留前端允许变量，不能获得DB/model/storage/root/AWS凭据。Playwright测试级retry不改变“产品无自动模型重试”。

视觉在320×520、390×520、760×700、1440×900保存真实页面截图/几何；无 document纵横溢出，每个 scroll owner正高度，六导航/账号/专注不压坏，timeline40轮、长文本和末尾动作可达。fixture可控制错误/丢ACK/迟到/几何；至少保留真实HTTP+DB+Redis+RustFS的私有简历→快照→多场→删除链，记录计数fake gateway只读无模型调用证据。报告分别标记Browser真实业务、HTTP/layout fixture、后端共证、延期，不合并成虚假全栈PASS。
