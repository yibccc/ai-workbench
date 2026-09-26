# 实施计划：专注与每日重复任务

状态：用户已在最终规划摘要之后批准执行。实施者先读 `prd.md`、`design.md`、`research/handoff-check.md` 与 `implement.jsonl`；检查者另读 `check.jsonl`。源包只读。`database-guidelines.md` 为 36,674 字节，超过当前单文件注入上限 32,768 字节；实施/检查代理必须从磁盘完整打开该规范，不能只依赖截断的注入片段。

## 有序步骤与阶段门禁

1. **实施前复核**：重读当前 HEAD、工作区、活动任务和 V* 迁移，确认本规划与代码未漂移；用户已在规划摘要之后批准执行，可按本机 Trellis Phase 1.4 启动任务。对已存在的 `00-bootstrap-guidelines` 与 5 个未跟踪 SOP 文件保持原样。若出现新的同 ID revision、迁移号或产品冲突，先修订规划。
2. **迁移与领域约束（AC-001/004/005/006）**：在新的 V15 或实施时下一个空闲版本增加模板、实例归属、会话、区间、记录来源/分片和 owner 外键/唯一约束；不改 V1～V14。先写真实 PostgreSQL 迁移与并发测试：软删不补造、单未结束会话、同会话同日只一条、跨 owner FK 拒绝、旧数据/约束回归。此阶段还不能开放前端入口。
3. **后端服务与 HTTP（AC-001～006）**：沿用 Controller/Service/Mapper 分层和 `CurrentUser`/CSRF。实现模板 CRUD/启停、显式当日补齐、开始/读取当前、条件状态转换、检查点与恢复确认、原子结算、专用进展补充和今日汇总。服务端 `Clock` 可注入，测试 20/60 秒阈值两侧、精确 25:30 样例、暂停中的休息、目标优先、跨 00:00、时钟跳变、归档/软删关联、丢响应后重试与故障回滚。事务提交之后才发刷新通知；通知失败可由纯读恢复。
4. **记录与报告整链（AC-004/006）**：新增 `FOCUS_SESSION` 后端枚举/DTO、Mapper、前端类型和来源显示。保持通用手工记录写入及自动完成记录的原边界。日/周候选、冻结 snapshot、模型输入和来源展示携带结构化净时长与会话/任务关系；确定性整理避免把投入写成完成。用确定性模型替身验证两段投入+一次完成、旧版本冻结、任务重开/删除仍保留投入。不得调用真实付费模型。
5. **独立页面与账号级控制（AC-003/005/007）**：加入 `#focus` 导航、`FocusPage`、任务带入回调和 `AppShell` 正文外紧凑入口；保留 `records` 默认入口和 `RetainedView` 草稿。账号根集中管理会话/声音租约，401/换号清理，自动同步不触发活动续期。专注页承载完整计时、模板、今日汇总；记录页只保留新来源列表项。按当前 `styles.css` 断点核验 320/390/760/1440 像素、键盘焦点和可滚动控制，不照搬原型 HTML。
6. **隔离全链验收（AC-001～007）**：逐项执行 `research/handoff/r2/acceptance.md` 的 TC-001～020，保留真实命令、HEAD、隔离 schema/卷、退出码、浏览器版本、失败与人工声音证据。补 400/401/404/409、CSRF、A/B/ADMIN、7 天显式活动边界、跨标签主控与登录失效；人工测前台声音/拒绝、实际设备睡眠和现有 HTTP/IP 与可用 HTTPS 入口。若支持矩阵或 20/60 秒参数测试失败，先修设计/测试，再继续。
7. **规范、兼容与审查门禁**：在行为落地后最小化更新相关 `.trellis/spec/` 中四入口现状描述及新增来源/计时合同；独立隔离环境演练“读兼容版本→启用写入→关闭写入”的升级/回退路径。以 `trellis-check` 做规格、数据流、类型、lint、测试和跨层审查；未通过 AC 或存在未验证的回退版本时阻止交付。本任务仍须遵循后续原生 Trellis 提交/收尾步骤。

## 实际仓库验证命令与前置条件

以下命令来自当前 `README.md` 与 `frontend/package.json`，本轮仅核实存在，尚未运行。使用 PowerShell；执行前按 README 准备**全新隔离** PostgreSQL schema、Redis 与确定性模型配置，显式设置 `TEST_DATABASE_URL`、`WORKBENCH_TEST_SCHEMA`（同一非 `public` schema）；E2E 使用独立 Compose 项目/卷/非默认端口、`E2E_DATABASE_URL` 的 `currentSchema=d9_e2e`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、非 6379 的 `REDIS_PORT`。不复用日常业务卷/密钥。

```powershell
Push-Location backend
mvn -s maven-settings-aliyun.xml clean verify
Pop-Location

Push-Location frontend
npm ci
npx playwright install chromium
npm run lint
npm run build
npm run e2e
Pop-Location
```

测试重点不是只看命令退出码：用真实 PostgreSQL 断言唯一约束和事务、可控时钟断言毫秒/跨日守恒、HTTP/Redis 断言 owner/CSRF/登录活动、Playwright 多页断言单会话/无双响与草稿分页、人工音频断言可听与降级。`assets/prototype-checks.json` 只证明交接包静态 HTML 的局部渲染，不作为工程 AC 证据。

## 风险与回退点

- **迁移 V15**：旧来源 CHECK 和同 owner 外键最易产生兼容故障；先在隔离旧数据副本迁移并验证校验和，不触碰日常库。迁移已应用后不编辑/删除旧脚本。
- **会话与日分片**：响应丢失、重试、跨午夜和恢复确认最易双计；数据库唯一键与结算事务是回退点。失败时关闭入口/写入，保留原始会话及分片供审计。
- **报告来源**：旧二进制不识别新来源；新写入只在兼容读取实例确认后启用。回退用认识新来源的版本并关闭写入，不能直接接回旧版或删除专注记录。
- **浏览器声音**：API 成功不代表人听到，后台节流与设备睡眠没有准点保证；只能报告测试过的前台浏览器/入口结果，失败时保持视觉引导与明确反馈。

## 执行进度（2026-09-26）

实现、V15 迁移、前后端接口、独立专注页、重复规则、报告来源及默认关写开关已落地。后端完整隔离 `clean verify` 最终 31 suites / 162 tests 全过；前端 lint/build 与 Chromium 隔离全量 E2E 48/48 全过，含恶意报告文字、结束响应丢失、被动检查点不续期登录。兼容读取 JAR 的关写→启写→关写演练通过。命令、环境、失败修复和范围限制见 [research/acceptance-audit.md](research/acceptance-audit.md)；独立审查见 [research/quality-check.md](research/quality-check.md)。真实扬声器可听、前台提示延迟、设备睡眠及 HTTP/IP/HTTPS 实际入口仍待人工证据，当前任务不能据自动化结果宣称所有 AC 已最终通过。
