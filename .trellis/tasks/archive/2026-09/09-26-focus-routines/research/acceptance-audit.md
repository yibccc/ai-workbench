# AC/TC 验收记录（实施中）

来源：[交接验收场景](handoff/r2/acceptance.md)。`PARTIAL` 表示已有自动化证据但整场景仍缺一项或多项；`AUTO_PASS` 表示该场景的工程自动化断言已覆盖，人工设备声明仍按下文范围限定；`SUPERSEDED` 表示用户直接修改了源场景的产品语义。只有实际执行结果、环境和证据齐备时才改为 PASS。原型截图与静态阅读不能作为工程 PASS 证据。

| 场景 | AC | 状态 | 待落证据 |
|---|---|---|---|
| TC-001 当日惰性生成/并发/软删 | 001 | AUTO_PASS | 真实库唯一键、软删重试、首进 E2E 与同登录会话并发 HTTP 补齐 |
| TC-002 星期、缺席、改停规则 | 001 | AUTO_PASS | 可控日期周末/缺席/规则变化与旧实例快照，后端 full verify |
| TC-003 临时/关联开始与净 25:30 | 002 | PARTIAL | 后端精确 1500/30/1530 秒、前端两种开始；前台真实声音另验 |
| TC-004 暂停、提前结束、暂停休息 | 002、003 | AUTO_PASS | 后端暂停余量/跳过/提前结算，前端跨页暂停 |
| TC-005 微休息、跳过、关闭、目标优先 | 003 | PARTIAL | 后端序号/优先级、浏览器全局引导；真实双标签发声与 ≤3 秒未验 |
| TC-006 音频拒绝与入口降级 | 003 | PARTIAL | 浏览器注入失败与视觉反馈已测；可听声音和 HTTP/IP/HTTPS 未验 |
| TC-007 刷新、跨页、双标签、并发开始 | 005、006 | PARTIAL | 后端单未结束/版本，浏览器草稿及跨页；双标签声音需人工 |
| TC-008 源包失联/睡眠确认 | 003、005 | SUPERSEDED | 用户 2026-09-27 明确取消该功能；旧 60 秒待确认测试不再是最终验收合同 |
| TC-008R 后台持续计时与睡眠追时 | 003、005 | AUTO_PASS | 后端长空档/暂停/目标上限、真实浏览器隐藏 62 秒及无确认 UI、最终全量 E2E 已测；物理睡眠可作为补充体验观察 |
| TC-009 结束幂等与空成果 | 004 | AUTO_PASS | 独立事务并发结束、单日唯一/空进展、浏览器丢 END 响应后权威恢复与重试 |
| TC-010 结算原子性与失败反馈 | 004、005 | AUTO_PASS | 独立事务首次写记录及跨日第二分片失败整笔回滚，浏览器丢响应恢复 |
| TC-011 任务完成/重开/删除与投入分离 | 002、006 | AUTO_PASS | 真实库同一任务完整状态循环与 focus 记录保留，原手工记录回归 |
| TC-012 日/周报告来源、快照与去重 | 004、006 | AUTO_PASS | 日周确定性双投入+一次完成、旧快照、恶意模型语义重写，E2E 日报来源 |
| TC-013 跨午夜切日 | 004、006 | AUTO_PASS | 后端上海 23:50→00:15:30 净 600/900 秒，NY 浏览器按服务端业务日 |
| TC-014 A/B/ADMIN 所有权、CSRF、换号 | 006 | AUTO_PASS | 后端 401/403/404/CSRF/owner，浏览器 A→B 状态清理 |
| TC-015 自动计时不续期登录 | 005、006 | AUTO_PASS | 实浏览器 20 秒被动 checkpoint 无 activity、明确暂停有活动信号；Redis 7 天边界集成测试 |
| TC-016 字段校验、归档关联 | 001、002、004、006 | AUTO_PASS | HTTP 400/409、owner 约束、归档模板 blocked 与会话后归档合法结算 |
| TC-017 键盘、窄屏、分页回归 | 003、006、007 | PARTIAL | Playwright 多视口/原四页分页草稿通过；人工焦点/设备仍未验 |
| TC-018 迁移、升级和回退 | 004、006 | AUTO_PASS | 隔离 V14→V15/V16 旧数据/校验和与旧恢复三态续账 + V16 JAR 关→开→关运行进程均已测 |
| TC-019 独立页与记录页职责分离 | 004、006、007 | AUTO_PASS | 工程 Chromium 独立页/默认记录/五入口/来源/刷新与窄屏 |
| TC-020 待办带入、紧凑入口与会话不替换 | 002、005、006、007 | AUTO_PASS | 工程 E2E 显式开始、跨页紧凑暂停、草稿/请求键与浏览器前进后退同会话 |

## 执行证据

环境见 [validation-environment.md](validation-environment.md)。不得用窄检查推断全 AC 完成。

### 2026-09-26 专注前端定向 E2E

- 工作区 HEAD：`3d1293095a3370403f27e051fa0639141a3c24b5`，代码含未提交的当前任务改动；Playwright Chromium `140.0.7339.186`，项目 `chromium-desktop`。
- 环境：`E2E_DATABASE_URL=jdbc:postgresql://127.0.0.1:25432/focus_routines_test?currentSchema=d9_e2e`、`REDIS_PORT=26379`、合成 `POSTGRES_USER=focus_test`、隔离测试密码、`FOCUS_WRITE_ENABLED=true`。未使用日常业务卷或真实模型密钥。
- `frontend` 目录运行 `npx playwright test --grep '专注|微休息|音频|时钟' --retries=0`，退出码 0，8 passed，35.8 秒。HTML 报告 `frontend/playwright-report/index.html`；`frontend/test-results/` 后续运行可能覆盖。
- 覆盖：待办带入/显式开始/跨页暂停及草稿、五导航与 320/390/760/1440×700 视口、关闭新写入时旧会话收尾、全局微休息引导、音频 API 失败可见、失联确认与本机时钟跳变投影、结束记录及日报新来源。日报在确定性 E2E 模型替身下实际生成 `SUCCEEDED`；来源响应含净时长和进展。
- 范围限制：浏览器自动化证明 DOM/API 行为，不证明人耳实际听见声音或真实设备睡眠；该定向批次不覆盖所有原四页回归或完整 AC。

### 2026-09-26 后端完整隔离门禁

- 工作区 HEAD 同上，代码含未提交的当前任务改动。环境：同一独立数据库 `focus_routines_test` 的全新 `d9_focus_tests_20260926` schema（`TEST_DATABASE_URL` 与 `WORKBENCH_TEST_SCHEMA` 一致）；`LIVE_ACCEPTANCE_DATABASE_URL` 指向该库的独立 `d9_live_acceptance`；合成 PostgreSQL 凭据、Redis `127.0.0.1:26379`。
- `backend` 目录运行 `mvn -s maven-settings-aliyun.xml -q -DforkCount=0 clean verify`（后台隐藏进程），退出码 0。实际 `backend/target/surefire-reports/TEST-*.xml` 共 30 suites、149 tests、0 failures、0 errors、0 skipped；`FocusIntegrationTest` 14 tests 全过。完整日志在本机临时目录 `focus-verify-stdout.log` / `focus-verify-stderr.log`。
- 首轮完整运行 148 tests、18 errors，原因是旧迁移测试借用 Hikari 连接后未恢复 `search_path`，导致其他测试读到临时 schema；已在 `TaskMigrationIntegrationTest`、`ReportMigrationIntegrationTest` 的 `finally` 中恢复原 schema，使用全新隔离 schema 复跑后通过。
- 此门禁证明后端测试套件全绿，不代替前端全量 E2E、真实声音、设备睡眠或升级回退的人工演练。

### 2026-09-26 后端补强后的最终门禁

- 同一命令 `mvn -s maven-settings-aliyun.xml -q -DforkCount=0 clean verify` 再次退出码 0；Surefire 31 suites、152 tests、0 failures、0 errors、0 skipped（本机日志同上）。
- 较 149 项批次新增确定性验收：周末不生成/缺席日不补/规则变更和停用不改旧实例；随机独立 schema 中 V14 合成旧 task、record、report、source 升到 V15 并核对 V1～V14 checksum 与 owner FK；周报两段专注投入加一次任务完成、结构化提示输入及冻结来源。
- 该测试证明迁移与读写合同的隔离演练；真实部署进程的开关切换、HTTP/IP/HTTPS 入口和人工声音/睡眠仍需另外记录。

### 2026-09-26 隔离进程发布开关演练

- 用 [rollout-drill.ps1](rollout-drill.ps1) 启动同一已构建 JAR（SHA256 与阶段结果见 [rollout-drill-result.json](rollout-drill-result.json)），仅连接独立 `focus_routines_test.d9_focus_rollout_20260926`、Redis 26379、HTTP `127.0.0.1:18083`；三次启动之间终止脚本拥有的 Java 进程，最终确认该端口无监听。
- 默认关写：`GET /api/focus/capabilities` 返回 `false`，新会话 POST 为 409。启写：返回 `true`，创建并结束一段 2061 ms 净投入，`/api/focus/today` 恰有该会话一条记录。再次关写：返回 `false`，新会话仍 409，原记录仍一条且净投入仍为 2061 ms。脚本退出码 0。
- 本演练证明同一兼容读取二进制的关写→启写→关写运行时路径；未声称旧版只认识两种来源的二进制能够读取 `FOCUS_SESSION`，也未触碰日常业务卷。

### 2026-09-26 前端全量 E2E（后续补例前的稳定批次）

- 与上述定向批次相同的隔离 `d9_e2e`、Redis 端口、合成凭据、`FOCUS_WRITE_ENABLED=true` 和 Chromium `140.0.7339.186` 项目；`frontend` 目录执行 `npm run e2e -- --retries=0`，退出码 0，43/43 passed，耗时 3.4 分钟。HTML 报告为 `frontend/playwright-report/index.html`。新增响应式用例覆盖 320/390/760/1440×700，旧回归另含 320/390/768/1024/1440 像素；覆盖原四页、账号撤销 Toast 及本批新增 9 个专注用例。
- 首轮全量 42/43，唯一失败是导航触发的 `/api/auth/activity` 收到 401 时，旧页面排队的 Toast 清理帧删除了新登录页提示。修改为仅清理调度前已有的旧事件后，撤销/五秒计时/提示替换 3/3 定向通过，全量 43/43 通过。此为实际产品竞态修复，不通过延长测试等待掩盖。
- 新发现的响应丢失请求键与异浏览器时区覆盖还在补测；本批 43/43 不能替代后续新增用例的验证。

### 2026-09-26 前端补强门禁

- 新增开始响应丢失后清理请求键、旧已结算请求换新键、America/New_York 浏览器仍按服务端业务日显示三项 E2E，定向 3/3 退出码 0。
- 随后 `frontend` 目录运行 `npm run lint`、`npm run build` 均退出码 0；隔离 `d9_e2e`、Redis 26379、`FOCUS_WRITE_ENABLED=true` 下执行 `npm run e2e -- --retries=0`，Chromium `140.0.7339.186` 全量 46/46 退出码 0，3.5 分钟。后续报告渲染修复后的最终复跑另记。

### 2026-09-26 独立检查及报告安全修复

- Trellis 独立审查见 [quality-check.md](quality-check.md)：规则更新缺版本改为 400、过期仍 409，补 ADMIN 跨 owner 404；纯专注来源的报告“进展”文字改从冻结事实确定性生成，避免模型误称完成。日/周恶意模型输出与混合真实完成来源测试已加入。
- 修复后 `backend` 隔离 `clean verify` 再次退出码 0，Surefire 31 suites、156 tests、0 failures、0 errors、0 skipped；前端 lint/build 独立复核亦退出码 0。

### 2026-09-26 报告修复后的最终浏览器复核

- 同一隔离 `d9_e2e`、Redis 26379、`FOCUS_WRITE_ENABLED=true`、Chromium `140.0.7339.186` 下，`frontend` 目录再次执行 `npm run lint`、`npm run build`，均退出码 0；`npm run e2e -- --retries=0` 退出码 0，46/46 passed，3.6 分钟。报告 `frontend/playwright-report/index.html`。测试后 Playwright 自启后端已退出。
- 这批包含报告安全渲染修复后的最终前后端产物；后续若仅增加后端测试而不改产品逻辑，沿用本浏览器证据并在后端记录新增门禁。

### 2026-09-26 并发与历史关联补证

- 追加独立事务并发 `fillToday`、并发 `end` 唯一结算、同一任务完成→重开→再次完成→删除后 focus 记录仍有效、模板项目归档给出 `blocked` 且已有会话在项目归档后仍保留合法历史关联并结算。这四组真实 PostgreSQL 测试定向退出码 0，未发现需改产品代码的缺陷。
- 再次运行隔离 `mvn -s maven-settings-aliyun.xml -q -DforkCount=0 clean verify`，退出码 0；Surefire 31 suites、160 tests、0 failures、0 errors、0 skipped。后端构建目录已释放给前端最后一轮 E2E。

### 2026-09-26 结束响应丢失的浏览器补证

- 前端在 `/end` 已由后端提交后模拟浏览器响应断开；权威 GET 恢复结束状态，同会话同日记录仅一条，重复结束不增记录，进展入口可保存。定向 Playwright 1/1 退出码 0。
- 最终 `frontend` 目录 `npm run lint`、`npm run build` 均退出码 0；同一隔离 `d9_e2e`、Redis 26379、`FOCUS_WRITE_ENABLED=true`、Chromium `140.0.7339.186` 下 `npm run e2e -- --retries=0` 退出码 0，47/47 passed，3.6 分钟。报告 `frontend/playwright-report/index.html`，Playwright 后端已退出。
- 该浏览器用例验证响应丢失后的界面恢复；结算事务回滚另由下节独立事务故障注入验证。

### 2026-09-26 独立事务与 HTTP 并发复核

- TC-001 用同一真实合成登录会话发起两个并发 MockMvc HTTP `fill-today`，真实 PostgreSQL 仅生成一条；TC-009 用独立事务并发 `end`，读到同一结算与同日一条记录。
- TC-010 新增无测试外层事务的故障注入：首次记录 INSERT 抛错后会话版本、阶段、区间和记录自动回滚，移除触发器后可成功重试；跨午夜第二日 INSERT 抛错时第一日已写分片与 ENDED 状态一并回滚，重试得到两日各 10 秒。
- 定向两类 Focus 集成测试退出码 0；隔离 `mvn -s maven-settings-aliyun.xml -q -DforkCount=0 clean verify` 退出码 0，Surefire 31 suites、162 tests、0 failures、0 errors、0 skipped。仅补测试与测试身份设置，未再改产品后端代码。

### 2026-09-26 登录活动与历史导航最终浏览器门禁

- 新增真实隔离登录/专注会话的 Playwright 用例：20 秒被动 checkpoint 至少一次，期间 `/api/auth/activity` 请求数为 0；经过活动节流窗口后明确点击暂停，活动请求变为 1。定向 1/1 退出码 0。浏览器后退/前进用例核对同 sessionId 和记录草稿不丢。
- `frontend` 目录 `npm run lint` 退出码 0；`npm run e2e -- --retries=0` 在 `d9_e2e`、Redis 26379、`FOCUS_WRITE_ENABLED=true` 与 Chromium `140.0.7339.186` 下退出码 0，48/48 passed，3.7 分钟。HTML 报告 `frontend/playwright-report/index.html`。本批只改前端测试，无产品代码变化。

### 2026-09-26 实际安装的 Chrome/Edge 自动烟测

- 独立人工实例 `http://127.0.0.1:15174`、合成账号与 `d9_focus_manual_20260926` schema 已启动，后端 health UP、前端 HTTP 200、`focus.writeEnabled=true`。
- [manual-browser-smoke.cjs](manual-browser-smoke.cjs) 在 headless Chrome `153.0.8010.53` 与 Edge `153.0.4234.48` 登录并进入专注页，各见五个导航入口；命令退出码 0。浏览器版本和页可达性已证实，实际扬声器可听、前台提示 ≤3 秒及设备睡眠仍待人测。

### 人工试用反馈与界面调整

- 用户反馈“声音能够听见”，并要求计时页左下快捷按钮从 25 分钟改为 45 分钟、移除“声音与提示”卡片。所用浏览器、声音阶段、≤3 秒时延与双标签/设备睡眠结果尚未提供，因此不能据此将 AC-003/005 判 PASS。
- 此项用户要求直接授权本轮 UI 调整；完成后需复测声音在新会话和刷新恢复会话下仍可启用，并更新交付回流。

### 2026-09-26 45 分钟快捷值与声音入口调整

- 计时页左下快捷按钮改为 45 分钟并赋值，任务/重复规则自有默认时长不变；独立“声音与提示”卡片移除，计时主面板单列。
- 点击“开始专注”的用户手势内尝试启用 AudioContext 并发出短试听音；刷新恢复的活动会话在主面板有紧凑启用/试听入口与音频拒绝时的可见反馈。隔离定向 E2E 3/3 退出码 0，断言 AudioContext.resume/oscillator.start 调用、拒绝降级、45 快捷值及无侧卡。
- `frontend` 目录 `npm run lint`、`npm run build` 退出码均为 0；同一隔离 `d9_e2e` / Redis 26379 / `FOCUS_WRITE_ENABLED=true` 下 `npm run e2e -- --retries=0` 退出码 0，Chromium 全量 50/50 passed，4.1 分钟。HTML 报告 `frontend/playwright-report/index.html`。自动化不能替代前台实际可听和 ≤3 秒时延的人体观察。

### 2026-09-26 最终时长下拉与跨页声音反馈

- 用户进一步指定目标净时长无关联时默认 45，点击输入框显示 15/25/45/60 快捷下拉，删除下方 45 分钟按钮。时长输入仍可填合法 1～480 分钟；任务和重复规则实例带入时长优先，已结束后新无关联会话重置 45。定向 E2E 4/4 通过，覆盖点击、Escape、Tab/Enter、自定义、规则/任务优先与 320×520/390×520 选项可见可点。
- 独立检查补全跨页声音失败可见性：报告页等其他页面显示账号根 `role=alert` 与返回/重新启声动作；微休息遮罩内无论当前页都显示该错误，重试成功后清除。定向 E2E 1/1 通过，含 320 像素无横向溢出。
- 最终 `frontend` 目录 `npm run lint`、`npm run build`（含 `tsc -b`）退出码均为 0；隔离 `d9_e2e`、Redis 26379、`FOCUS_WRITE_ENABLED=true`、Chromium `140.0.7339.186` 下 `npm run e2e -- --retries=0` 退出码 0，**52/52 passed、无重试、4.2 分钟**。HTML 报告 `frontend/playwright-report/index.html`。中途因需求更新主动中止的旧界面批次不计最终证据。
- 该批证明新交互和模拟音频拒绝的 API/DOM 行为，不证明移除侧卡后扬声器实际可听、微休息开始/结束各自可听、≤3 秒时延或真实设备睡眠。

### 2026-09-27 用户取消失联确认后的 V16 修订（进行中）

- 用户指出开始专注后会切到其他页面/应用工作，并直接要求去掉睡眠失联功能。旧交接 TC-008 的 `>60 秒待确认/确认或舍弃` 语义被替换为 TC-008R：会话从服务器锚点连续计时，设备睡眠也可能计入；用户停工需主动暂停或结束；后台检查点不能凭空形成已完成微休息。
- V15 脚本保持不变，新增 V16 将旧 `RECOVERY_REQUIRED` 行恢复到 RUNNING/PAUSED/MICRO_BREAK，从旧待确认起点接续并移除 pending 字段。隔离随机 schema 中已用新服务对三态执行后续 checkpoint/end：RUNNING 达目标且跨日分片 60/540 秒、PAUSED 只增加 pause 360 秒、旧微休息余 10 秒后继续，历史 PENDING 不重复入账。
- 后端隔离 `mvn -s maven-settings-aliyun.xml -q -DforkCount=0 clean verify` 退出码 0，Surefire 31 suites、165 tests、0 failures/errors/skipped；V16 新行为含精确前台 25:30、长空档追时、目标优先、暂停和旧行升级续账。
- 前端第一轮 V16 全量隔离 E2E 56/56、lint/build 退出码均为 0；另有真实服务端经过 62 秒的隐藏标签用例，后台 checkpoint 发生且未调用 `/api/auth/activity`、会话仍 RUNNING/无虚构休息，回前台只触发一次 BREAK_DUE，无恢复弹层。展示文案进一步改为“会话计时/会话净时长”，提示不检测实际工作、停工需主动暂停或结束；最终文案版相关定向 4/4、全量 E2E **56/56**、lint/build 均退出码 0，Chromium `140.0.7339.186`，隔离 `d9_e2e` / Redis 26379 / `FOCUS_WRITE_ENABLED=true`，HTML 报告 `frontend/playwright-report/index.html`。
- V16 JAR SHA256 `061C40F7B96BE646E86D8C5998E72CBC65D32F3402D952C5314FC6E05E42B1CF` 在隔离 `d9_focus_rollout_20260926` 重演关写→启写→关写：新会话 409 / 启写形成 2071 ms 单条记录 / 再关写仍可读 2071 ms 且新会话 409，脚本退出码 0，证据见 [rollout-drill-result.json](rollout-drill-result.json)。V16 JAR 同时在 `d9_focus_manual_20260926` 自动迁移后恢复本地人工实例 `http://127.0.0.1:15174`；登录与开关读均成功，实际安装 Chrome/Edge headless 五导航烟测退出码 0。

### 2026-09-27 按用户要求停服与提交

- 用户明确要求停服务、收尾、提交并提 MR。专属前后端用 [stop-manual-acceptance.ps1](stop-manual-acceptance.ps1) 按记录 PID 与命令行核对后停止；独立 `focus-routines-20260926` Compose postgres/redis 执行 `stop` 后 `ps -a` 均为 `Exited (0)`，两个测试数据卷保留。URL `127.0.0.1:15174` 暂不可访问。
- AC-003 的新版本前台真实可听、双标签唯一发声及 ≤3 秒时延仍无完整人工证据，保持 `NOT_RUN`。本轮收尾及草稿 MR 以此为公开残余项，不把既有“声音能够听见”的笼统反馈扩写成全部声音阶段通过。
