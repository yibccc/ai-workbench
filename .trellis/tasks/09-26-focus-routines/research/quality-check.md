# Trellis 独立质量审查（2026-09-26）

审查范围：当前未提交工作区的 AC-001～007 / TC-001～020、交接包 r2、正式 `prd.md` / `design.md` / `implement.md`、完整 `database-guidelines.md` 与 `check.jsonl` 全部规范。以下是代码、测试和运行证据的范围说明；人工声音和真实设备睡眠仍不能用源码推断。

## 已修复

- `FocusServiceImpl.version` 原把规则更新请求缺 `version` 与过期版本都映射为 409；新错误矩阵规定缺失字段 400。现在 null 返回 400，过期仍返回 409。
- `FocusHttpIntegrationTest` 新增规则更新缺失/过期版本的状态与安全 `detail` 断言，并补 ADMIN 跨 owner 读取及结束写入的 404 断言。此前测试只覆盖普通账号 B 的跨 owner GET。
- `ReportServiceImpl` 原仅限制专注来源进入 ACHIEVEMENTS，模型仍可在 PROGRESS 凭纯专注来源伪称完成。现在对含 FOCUS_SESSION 且不含 TASK_COMPLETION 的进展要点，从冻结来源的标题、结构化净毫秒数及可选用户进展生成确定性文字，保留原全部来源 ID；含真实完成来源的要点维持现有文本。日/周集成测试注入恶意“已完成任务”模型输出，断言持久内容只表达投入且来源可追溯；另验证真实完成来源混合引用仍保留模型文字。
- `FocusConcurrencyIntegrationTest` 与 `FocusIntegrationTest` 补充四组真实 PostgreSQL 回归：独立事务同时 `fillToday` 仅生成一个实例；同时结束只结算一个同日分片且两个响应归同一会话；关联任务完成、重复完成、重开、再完成、删除后专注记录仍有效；模板项目归档给出 blocked 且其他规则照常生成，已开始会话在项目归档后结算仍保留项目关联。
- 复核发现原 `FocusIntegrationTest.settlementFailureRollsBackSessionAndRecordsAtomically` 处于测试外层 `@Transactional`，自行回滚 SAVEPOINT，不足以证明服务事务自动回滚。`FocusConcurrencyIntegrationTest` 现新增无测试外层事务的真实故障注入：首次记录 INSERT 失败时会话/区间/记录全回滚且可重试；跨午夜第二日记录 INSERT 失败时第一日已写分片也回滚，关闭触发器后重试得到两日各 10 秒。测试前后仅清理其专用触发器/函数。并发补齐测试现走两个真正的 MockMvc HTTP 请求和独立服务事务。

## 独立复核证据

| 范围 | 证据与界限 |
| --- | --- |
| PostgreSQL 迁移、唯一键、同 owner 外键 | V15 静态核对；`FocusMigrationIntegrationTest`、`FocusUpgradeIntegrationTest` 和 `FocusConcurrencyIntegrationTest` 运行记录存在。最新补例后独立运行 31 suites、162 tests、0 failures/errors/skipped。 |
| 状态机、恢复、跨日、结算回滚 | 阅读 `FocusServiceImpl`、`FocusStore.xml`、`FocusIntegrationTest`。10:20 迟到检查点计已可靠经过的 FOCUS，再从可见响应开始 BREAK，避免追溯伪造用户未见的休息；该语义有定向测试。可控时钟覆盖 25:30、60 秒两侧、跨日 600/900、恢复无补造休息、时钟倒退、故障回滚。 |
| 报告快照与来源 | `ReportMapper.xml` 冻结 session/task/date/focus/break/progress；`ReportServiceImpl` 禁止 FOCUS_SESSION 支撑 ACHIEVEMENTS，并对无完成事实支持的专注进展要点使用确定性冻结来源文字；日/周测试覆盖两次专注加一次任务完成、恶意模型文字丢弃、混合真实完成来源和旧快照不变。其他模型文字仍由原来源/分段校验规则处理。 |
| 前端类型与 lint | `frontend` 目录执行 `npm run lint`，退出码 0。 |
| 前端类型与构建 | `frontend` 目录执行 `npm run build`（含 `tsc -b`），退出码 0，Vite 78 modules。 |
| 后端完整门禁 | `backend` 目录对隔离 `d9_focus_tests_20260926` schema、独立 `d9_live_acceptance`、Redis `127.0.0.1:26379` 执行 `mvn -s maven-settings-aliyun.xml -q '-DforkCount=0' clean verify`，退出码 0。`backend/target/surefire-reports/TEST-*.xml` 汇总 31 suites、162 tests、0 failures/errors/skipped；包含新 HTTP、报告、并发/归档/任务循环和独立事务故障注入断言。 |
| 浏览器 | 主会话在 `acceptance-audit.md` 记录 Chromium 最终全量 E2E 48/48 无重试通过，包含报告渲染、结束响应丢失、专注被动同步不续期及浏览器历史；本次独立审查没有亲自复跑该 E2E。 |
| 部署 | `application.yml`、`compose.yaml`、`.env.example` 默认 `FOCUS_WRITE_ENABLED=false`；前端 E2E 显式启用隔离写入并校验数据库/Redis 参数；README 写明兼容读取、开放写入和关闭写入的顺序。真实运行时切换由主会话在独立 schema 演练。 |

## 待主会话补证或处理

- 主会话的隔离 JAR 演练已完成，`research/rollout-drill-result.json` 记录 false→true→false 的运行时开关切换与历史读取；本次修改后的完整 Maven 门禁已通过。
- 交接 TC-005/006 的前台提醒 ≤3 秒、Chrome/Edge 真正可听、HTTP/IP/HTTPS 入口以及 TC-008 的真实设备睡眠，目前没有人工设备证据；不能宣称这些项目 PASS。Playwright 注入音频拒绝和浏览器时钟跳变只验证降级/界面逻辑。
- 主会话已同步 `database-guidelines.md` 的三值 `WorkRecordSource` 与通用记录 CRUD 仅 `MANUAL` 的边界，也将专注确定性报告文字写入 `focus-routines.md`。
- `research/acceptance-audit.md` 已把各 TC 细化为 `PARTIAL` / `AUTO_PASS`。本次新增后端测试补强 TC-001/009/010/011/016，其中 TC-001 现有真实 MockMvc HTTP 双请求。TC-009/010 的浏览器响应丢失界面恢复由前端另补测，不能仅据并发与故障注入服务测试判整场景通过。

## 审查判定

前端 lint/typecheck/build、后端补例后 162 项全量测试均通过。人工声音、设备睡眠和真实部署入口仍未有充分验收证据。
