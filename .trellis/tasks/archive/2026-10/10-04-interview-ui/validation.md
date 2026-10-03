# AI 面试前端实现与验证

本记录只覆盖前端代理所有权，不变更 lifecycle。用户已在主会话明确批准最终规划/P-01；后端、真实模型及存储共同验收未由这些 UI fixture 证明。

## 文件与行为

- `frontend/src/api/resume.ts`、`api/interview.ts`：共享 Cookie/CSRF/Abort/identity request 与 requestBlob；typed DTO，receipt 确认后 owner GET。
- `features/profile/ProfilePage.tsx`、`profile.css`：current/编辑文本/原 File 分离，UTF-8 .md 三门禁，编辑后 multipart 保存；普通 EDIT_CURRENT 保原件，明确 PASTE 替换；独立编辑版次、409 确认、同 tuple/requestId 恢复、私有原件下载与删除。
- `features/interview/InterviewWorkspace.tsx`、`InterviewCreateForm.tsx`、`InterviewSessionView.tsx`、`InterviewReport.tsx`、`model.ts`、`text.ts`、`interview.css`：5 项分页，四方向/三难度/全部 3–20，JD 独立分析/失效/DELETE/手动 retry，多场 retained 草稿，固定题单/锁定/交卷，报告缺结果无总分。
- `components/SafeMarkdown.tsx`、`components/layout/navigationGuard.ts`：共享安全 Markdown，skipHtml/普通 http(s) URL，外链图只显示文本；guard 类型移出 publishing，由当前活动上下文调度。
- `components/dialogContext.ts`、`DialogProvider.tsx` 与 App：logout/401/login 身份边界同步 dismiss 并 resolve(false)，sequence fencing 阻止旧异步确认关闭新账号弹窗；正常 busy/cancel/focus 行为保留。
- App/layout/navigation/routes/AppShell/AccountMenu/styles 及 community/publishing guard imports：第六 retained interview、账号菜单 profile 辅助页；保留账号根、原五页、唯一 focus controller；窄屏六项可达。
- `frontend/e2e/interview.spec.ts`、`interview-fixture.ts`、`playwright.interview-ui.config.ts`；旧 `workbench.spec.ts` 三处导航数量由 5 改 6。

原型内容区 CSS 直接从批准 R1 HTML 迁入并限定 feature 根，保留卡片/分组/色值/900px/520px 分组。源 shell/reset/演示脚本和 mock 业务数据未进入产品。移除 1180px cap，接入原有 page/workspace-scroll 高度链。

## 实际检查

| 命令 | 实际结果 |
| --- | --- |
| `npm --prefix frontend run lint` | exit 0，无错误/警告；末次 JD retry 入口补正后再过 |
| `npm --prefix frontend run build` | exit 0，strict tsc-b + Vite，267 modules |
| `npm --prefix frontend run e2e -- --config=playwright.interview-ui.config.ts` | 最终 **22 passed (33.6s)**，workers 1，retries 0；包含原项目 Dialog 取消零写/焦点恢复/失败留值/busy Escape/双击与后台401跨号确认清理 |
| 同配置 `--grep='401\|403\|交卷\|所有阶段\|retained\|JD\|创建丢'` | owner Dialog 修复后 **11 passed (19.0s)** |
| 同配置 `--grep=六屏` | 四尺寸 **4 passed (10.7s)**；最终完整22项也含四尺寸 PASS |
| 同配置 `--grep='JD\|创建丢'` | 最后 JD 入口补正后 **4 passed (6.5s)** |
| 同配置 `--grep=retained` | 测试收尾等待当前简历 GET 结束后 **2 passed (4.2s)** |

检查进程设置 NODE_OPTIONS=--max-old-space-size=512，未改全局系统资源。Vite JS chunk 约 562 kB 的 >500 kB 优化提示仍存在，未调阈值/放宽 TS 或 lint。

UI-only 配置继承原默认 Playwright 的显式隔离 PG/Redis guard，只启动现有过滤环境的 Vite，不启动后端或 reset。测试 EnvFile 仅白名单导入 E2E_DATABASE_URL、POSTGRES_USER/PASSWORD、REDIS_PORT；凭据不写入本文，不给 Vite 子进程。所有业务用根 `/api/` HTTP fixture，不能据此宣称 DB/storage/model PASS。

## 截图与几何

320×520、390×520、760×700、1440×900 每尺寸 history/create/answer/report/delete/resume 六张，共 **24 张**。实际输出目录：

`frontend/test-results/interview-受控-HTTP-fixture：仅-UI-行为证据-六屏几何与-40-轮可达-<宽>×<高>-chromium-desktop/`

每目录文件为 `history-<宽>.png` 等六屏，trace.zip 同存。断言 document 纵横不溢出、所有可见 region 正高度、40 轮末项/提前交卷/末尾反馈/删除动作能进入视口。已人工查看桌面 history 和 320px resume。

最终24张截图已稳定复制至本任务 `artifacts/visual/`，后续 Playwright 清理临时输出不影响该证据。

交付前已读取真实 `backend/.../dto/interview/InterviewModels.java`、`InterviewController.java` 和 ResumeModels/ResumeController：Session/Question/Answer/JD/Report/receipt 字段与路径对齐；page 实际返回 Summary，前端已单独建 InterviewSummary 而不假定包含正文/题单。Resume DELETE 使用 expectedVersion/requestId query，确认框内失败重试保同删除 tuple。没有增兼容层。

调试修正了 Profile 隐藏 File input 的 absolute 静态位置撑高窄屏 document（file-card 定位）；fixture 根路径避免误截 Vite src/api；focus StrictMode 初始读取以基线比较导航后零新增。未降低几何断言。

## INT-AC 覆盖边界

| AC | 当前 UI 证据 | 尚需后端/真实共同验证 |
| --- | --- | --- |
| 001/010/012 | 401 A→B 清 File/草稿，403 保留上下文；脚本不执行/远图不请求；owner Blob 接口 | owner/ADMIN 拒绝、RustFS 原件、prompt 合同 |
| 002/017/025 | 四方向、全 N、40 轮/长反馈可达、正文不截断 | 题单完整性、非法题/值、最大模型输入 |
| 003/004 | 409 留文本、SUBMITTED 锁定、draft 不推进、空 submit；丢 ACK 同 key/全文确认 | 真实刷新/重登、HTTP 并发/幂等 |
| 005/006/007 | 取消零写、未提交草稿仍未答、冻结；有效 0 可显示；必要结果缺失无总分 | 冻结事务、等权计算、坏结构/缺题 |
| 008 | 六导航/retained Profile 草稿，导航不新增 focus 初始化读取 | 完整旧 workbench/focus/community 回归 |
| 009/014/015 | confirmed current version 与不可编辑场配置，创建无 resumeId/正文 | A/B 真快照、rubric 输入 |
| 011/013/020 | File 先编辑不写、原字节/最终文本分开；独立 editVersion、409 需确认；空正文/无current 分开 | DB CAS、原件替换失败/历史保留 |
| 016/024 | 状态/历史只 GET；FAILED 显式 retry；已存在分析不能从原 parse 按钮另建 job | counting gateway、重启/lease/token/fencing |
| 018/019 | 字符/方向变立即失效、改回不能复用、迟到 DELETE；失败/mismatch 留原文、明确移除才 none | exact raw hash 与各场 JD 快照 |
| 021/022 | 各阶段共用删除、取消无写、404 清内容；未宣称对象/备份物理删除 | 敏感擦除、tombstone、PG/RustFS 独立恢复 |
| 023 | 20k/10k/5k/+1 emoji 保全文、File byte/UTF-8/body 门禁分开 | 后端相同码点及 import 原件/最终正文双门禁 |
| 026 | 分场 retained 草稿、create 同 tuple/requestId 恢复、成功 JD 多场复用 | 真不同配置三场/刷新/重登/删除 |

## 待主会话安排

真实 `interview.spec.ts` 用例已写，**尚未运行**：新合成 USER、File 原件下载/最终正文 A→B、不同配置 Java/React 两场快照、独立草稿与提交、刷新与重登、删 current 后无简历第三场、删一场保留另场。

待 resume/engine 独立验收和正式 DTO 落地后，主会话核对 nullable/字段/receipt 并运行默认面试 E2E、旧 workbench/focus-alarm/community 套件。独立 check、规范回流、提交/归档归主会话。真实付费模型质量/费用/延迟、阿里云 OSS 接入/迁移延期，不记 PASS。
