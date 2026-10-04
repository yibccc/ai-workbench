# 最终浏览器真实整合验收

本记录由父整合前端代理执行，日期为 2026-10-04。用户持续实现/审查/隔离验收授权沿用主会话批准；未提交、归档、变更后端/运维/规范，未停止源服务或对象卷。

## 实际环境与运行边界

- 主会话独立启动的最终 e2e JAR，HTTP `127.0.0.1:18080`，启动核验 health=UP、PID 4896；本代理没有启动 Maven/第二后端。
- 专用 PostgreSQL `127.0.0.1:25432/wbinterview_e2e` / `d9_e2e`、Redis 36379、RustFS 29000 / 私有桶 `wbinterview-fixtures`。不是日常应用数据库、schema 或对象桶。
- runner 继承原 Playwright 隔离 URL/PG 凭据/Redis guard，只起经过环境过滤的 Vite 15173；Chromium env 按主会话控制配置白名单过滤，不包含 DB/model/storage/root 凭据。
- 只从忽略的测试 EnvFile 加载 E2E_DATABASE_URL、POSTGRES_USER、POSTGRES_PASSWORD、REDIS_PORT；进程 NODE_OPTIONS=--max-old-space-size=512，不记录秘密值、不改系统配置。
- backend 使用 e2e `DeterministicInterviewAiGateway`；实际 RustFS/HTTP/持久化业务流程运行，模型分数为可控合成结果。不能据此证明真实付费模型的题目/评估质量、费用、上下文容量或延迟。

原控制 `.ts` 在 package 外出现 TS/CJS 与 import.meta 的加载冲突（`exports is not defined`），尚未运行业务；复制为忽略的 `.mts` 明确 ESM 后正常运行，保留原隔离 guard，没有产品或兼容层变化。

## 真实链与断言

实际场景位于 `frontend/e2e/interview.spec.ts` 的真实 HTTP describe，单条多步骤流程设 120 秒上限，正常运行约 9 秒。

1. 新建合成 USER，在 Profile 选择 UTF-8 `real-resume.md`，修改导入正文后保存。owner original GET 200，原件字节逐字等于原 File；SHA-256 等于对原字节计算的摘要，最终 markdownText 为另一份编辑正文 A；返回 `no-store`。
2. Java/MID/N=3，当前简历 A，真实显式 JD parse，持久 analysis 为 SUCCEEDED；create 绑定该 analysisId/raw。面试固定 6 轮，暂存第一场不推进。
3. 通过普通 EDIT_CURRENT 改正文 B，确认新版 current；改方向 React 后旧 JD 不可创建，用户显式移除 JD，SENIOR/N=4 新建第二场。API 证实第一场简历/JD 仍 A/原 JD，第二场简历 B/无 JD，两场配置不同。
4. 两场分别保存独立草稿，刷新后分别恢复；各提交第一轮并进入第二轮，互不覆盖。删除 current 之后第一场简历快照仍 A；可新建无简历通用第三场，第二场进度仍为 1。
5. 退出再登录，第一场恢复到第 2/6 轮。提前交卷 Dialog 显示 **已提交1、未提交5、未答按0**；确认后答卷 COMPLETED，评价 SUCCEEDED，作答输入消失。
6. 真实 report 的首轮有效分 **80.125**，另5轮 **UNANSWERED/0**；总分 **13.35**（80.125/6，两位 HALF_UP）。UI显示同一后端总分，问题组连续显示1/2/3，缺结果占位消失。
7. 连续重复读取持久 report 两次与首次完全相同，浏览器显式 activity 信号不增加；全流程 JD parse POST 只有一次。本项证明可见 HTTP行为，后台模型调用数量依赖 engine counting-gateway/SDK 测试共证。
8. 删除已评估第一场并等写确证，GET为404；第二场仍200。题单/答案/报告消失，其他场次保持独立。

上述真实链主要提供 INT-AC-004/005/006/009/011/012/014/018/020/021/026 的 Browser+API 参与证据；所有权、DB并发、lease/fencing、备份恢复、模型SDK无自动重试仍采用相应后端/运维独立证据。

## 执行命令与结果

以下所有 Playwright 命令先按上述白名单设置隔离变量，以绝对控制配置运行，不触发默认 webServer Maven：

```powershell
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix frontend run e2e -- --config E:/projects/workbench/.local-runtime/ai-interview-test/playwright-real.config.mts --grep '真实 HTTP'
npm --prefix frontend run e2e -- --config E:/projects/workbench/.local-runtime/ai-interview-test/playwright-real.config.mts e2e/workbench.spec.ts e2e/focus-alarm.spec.ts e2e/community.spec.ts e2e/interview.spec.ts
npm --prefix frontend run e2e -- --config E:/projects/workbench/.local-runtime/ai-interview-test/playwright-real.config.mts e2e/workbench.spec.ts e2e/interview.spec.ts --grep '真实会话撤销|受控 HTTP fixture|真实 HTTP'
```

| 检查 | 当前实际结果 |
| --- | --- |
| Lint / strict TS + Vite build | 两次局部修复后均 exit 0；267 modules，保留既有约563 kB chunk优化提示 |
| 增强真实链 targeted | **1 passed (11.9s)**，exit 0 |
| 完整四文件初跑 | **116 passed / 1 failed (7.2m)**，exit 1；唯一失败为旧撤销测试等待已销毁导航的竞态，首次和retry都复现 |
| 获准局部修复后相关范围 | **29 passed (59.9s)**，exit 0：27项面试UI fixture + 真实链 + 旧撤销测试 |
| 最终完整四文件 | **117 passed (6.0m)**，exit 0；没有 failed/flaky/retry：community 13、focus-alarm 14、interview 28（27fixture+1真实）、workbench 62 |
| 最终UI完成态 targeted | 加入 UI“已完成”明确断言后 **1 passed (11.0s)**，exit 0；等待状态与Report两次只读之间的短暂时差消失再截图 |

## 修复与测试时序澄清

- 初始真实链原测试在 login click 后立即额外请求 CSRF/管理员API，与登录自身 CSRF/cookie 竞争；增加“工作台已显示”的真实登录完成条件。删除后立即GET也可能先于写入确证；改为等 Dialog/私有卡消失再验证404。这些是测试等待补正，没有改认证或删除业务。
- 真实“难度”substring label也匹配到无简历说明中的radio；改用 actual combobox角色定位，不降低表单断言。
- 旧工作台撤销用例：账号禁用后初始GET或鼠标pointerdown的activity 401 已正确移除业务树，Playwright还重试点击被移除的链接直到30秒超时。获准改为初始networkidle、禁用前focus导航、禁用后原生键盘Enter；仍发送真实保护请求、仍断言Login/无业务树/从Toast实际出现到完整5秒消失，未吞401或缩短提示断言。
- 实际 backend ReportGroup.mainIndex 是主问turnIndex（freeze步长2），UI此前显示问题组1/3/5。仅把展示改为 floor(mainIndex/2)+1，fixture同步真实0/2/4，live断言1/2/3；评分、协议、分组映射不变。

## 证据保存

- 父任务 `artifacts/browser-real/targeted/`：首次真实链 trace与三个截图。
- 父任务 `artifacts/browser-real/focused/`：局部修复后真实链 trace、`real-profile-saved.png`、`real-two-sessions.png`、`real-evaluated-report.png`；报告截图已回到内容顶部，分数和组编号可读。
- 忽略目录 `.local-runtime/ai-interview-test/evidence/full-first/html/`：原117项初跑完整报告；同目录保留撤销失败两次的trace/截图/错误上下文。
- 忽略目录 `.local-runtime/ai-interview-test/evidence/focused/html/`：29项全部通过报告。
- 忽略目录 `.local-runtime/ai-interview-test/evidence/final-117/html/`：最终完整117项全部通过报告。
- 父任务 `artifacts/browser-real/full-117/`：最终整套的真实链trace与三个截图；`artifacts/browser-real/final-state/` 额外保存明确等UI“已完成”后的最终报告截图/trace。
- 原任务24张六屏×四尺寸稳定截图/27fixture独立检查证据继续有效，未回退其5项修复。

结束核验：15173没有监听，Vite/Chromium已随runner正常结束；18080仍为主会话原PID4896、health UP。没有停止任何源数据库、Redis、RustFS或后端服务。

本轮改变 `frontend/e2e/interview.spec.ts`（真实链与明确等待）、`interview-fixture.ts`（真实主问索引）、`workbench.spec.ts`（撤销测试确定性键盘触发）、`features/interview/InterviewReport.tsx`（组序号展示）及本文/验证图迹；没有修改后端、运维或规范。原UI独立check的5项修复和27fixture保留。

浏览器整合没有已确认遗留失败。真实付费模型/阿里云API/OSS迁移仍延期；本代理没有这类调用。后台owner/SDK计数/fencing及原件备份恢复继续由父任务引用后端/运维共证，不能把117项混合Browser/fixture结果统称为117条真实存储链。
