# 父任务执行与集成计划

最终规划已获用户批准，四个**子任务**已由原生 Trellis 激活并完成实现及分层检查。父任务不直接作为编码目标；下列顺序保留为执行和集成复核依据，实际证据见 `research/integration-evidence.md`。

1. **核定合同**：P-001“仅主动操作续期”已写入父/认证/实时/前端规划及本地 TC-019；检查 73 AC 与 40 TC 的引用及四子任务依赖。父任务最后再审一遍 `prd.md`，无重复事实或阻塞项后才提请开工。
2. **认证与会话子任务**：先提供稳定 userId、Spring Security/Spring Session 认证、账号管理、空表首管理员、密码和撤销合同；以隔离用户/Redis/PostgreSQL 验证。不得建立公开注册或全局 CSRF 放行。
3. **数据归属子任务**：在身份合同上新增迁移、owner SQL 与异步处理归属；覆盖所有 Controller/Service/Mapper 和数据库约束。用 A/B/ADMIN、并发/恢复样本验证 404、幂等、版本链与原业务回归。
4. **实时子任务**：接入 STOMP 用户目的地与授权、after-commit 发布、Session 撤销到连接的联动；用真实协议测试两用户多端、伪造订阅和断线恢复。与认证子任务联合验证自动通信不续期及到期后旧连接不再收新事件。
5. **前端与部署子任务**：实现登录/账号辅助页、身份切换清理、Toast、STOMP 客户端；同步 Nginx/Compose/README/旧部署规范。应用授权与协议测试通过后才移除 Basic Auth。
6. **父任务集成 review**：逐 AC→TC 检查 `acceptance.md` 全部 40 场景的实际证据；复测跨层和现有业务合同，记录失败原始结果。最终 `trellis-check` 全范围检查后，再按原生后续流程处理规范/提交；本计划本身不授权这些动作。

现有持久库已按 `research/deployment-readiness.md` 的具体目标、用户后续授权、已验证备份与恢复结果在本机启用。八张旧业务表清空、首管理员引导、`localhost` 运行和不保留备份均有执行记录；服务器 IP/HTTPS 入口由用户以后配置并需另验。

现有 Playwright 用匿名请求调用仅 E2E profile 可用的 `/api/e2e/reset` 并直接写业务。各子任务实施测试时同步加入登录/CSRF 与 A/B/ADMIN fixture，继续把 reset 限在 `d9_e2e` 和 `e2e & !live-acceptance`；不能为测试放行正式业务匿名访问。

## 可用验证命令与环境门禁

- 当前 Windows 会话确认有 Java 17、Maven 3.9.9、npm 11.6.2；后端可在 `backend/` 运行 `mvn clean verify`，前端在 `frontend/` 运行 `npm run lint`、`npm run build`、`npm run e2e`（依赖和 Playwright 浏览器须先具备）。不以构建通过代替安全/数据库验收。
- 当前 PowerShell 未找到 `docker`，但 WSL Ubuntu 的 Docker/Compose/daemon 可调用；隔离 PostgreSQL/Redis、STOMP、Playwright、Compose 构建与部署烟测已运行，命令和结论记录于 `research/integration-evidence.md`。临时 `authuiverify`、`d10deployvalidation` 容器/卷/网络在使用后清理，原 `ai-workbench` 数据和卷不变。正式公网 HTTPS 仍须在用户选定入口后验证。
- 真实模型默认禁用，用确定性替身完成 AI/报告/并发验收；前端浏览器核对 5 秒 Toast、四工作区布局、身份切换和真正截图。执行门禁包括 `task.py validate`、相关层规范检查、全量 lint/type-check/test 与跨层数据流复核。

## 风险点与回滚点

重点 review：Session 角色变更竞态和 Redis 失败、STOMP 旧连接继续收消息、漏掉子表/计数/来源 SQL、周报锁只按周期不按用户、同浏览器迟到响应串号、旧 Nginx 入口移除顺序。每层先在独立环境保留可恢复数据库/配置快照；迁移或入口验证失败就停止继续公开访问，不把旧应用连到含多人数据的新库。
