# 交付回流：登录、用户管理与私有空间

handoff_id / revision：`WB-20260925-auth-isolation-8cbe4aee / r1`
Trellis 任务：`.trellis/tasks/archive/2026-09/09-25-auth-isolation/`（四个子任务同月归档）
状态：PR 已合并；Windows `localhost` 已启用；服务器 IP／HTTPS 入口尚未部署。
业务 commit：`475749a`、`ff00a0f`、`01ccae2`。
review base / reviewed HEAD：`a4f86e48c1cd76c910b2fae9c94bfec6851277f4` / `314f7b18f5ef9d751544d2ee62b1e4e7ae4ef571`。
工作区指纹：交付回流前 15 个待提交文件的“相对路径 + 文件 SHA-256”排序清单摘要为 `2c85f4025c7001bbf75dd987f65cb1f6561eb537da61168e00143b0d8ae45299`；当时 HEAD tree 为 `08c8ae9dc6781c02da8bcb9bdf496f25fa147e4b`。本报告和新安装的合同不参与该回流前指纹。
验证记录：`.trellis/tasks/archive/2026-09/09-25-auth-isolation/research/integration-evidence.md`、`research/deployment-readiness.md`。
审查记录：上述集成证据及 Trellis check 复核；未生成独立 `wb-review/review.md`，不将同会话复核称为独立审查。
后续归档/日志 commit：`9431919`、`9ad113b`、`e8b71e0`、`6d0ea62`、`9a33070`、`314f7b1`。

## 实际交付

- 建立封闭账号体系：Spring Security、Spring Session Redis、管理员建号/角色/启停/重置、自改、CSRF、失败登录限制和仅由主动操作续期的 7 天会话。
- 对项目、待办、记录、输入、报告及异步来源施加稳定 userId 归属；跨用户资源按 404 处理，ADMIN 也无跨用户业务权限。STOMP `/user/queue/workbench-events` 仅给所属会话发送提交后的刷新信号。
- 增加登录、账号菜单和用户管理辅助页，保持四个业务工作区；移除 Nginx Basic Auth。后端 Docker 构建改用阿里云 Maven 公共镜像，并补隔离 E2E、Compose/断网/网络调用观测脚本。
- 经单事务授权清空现有库八张旧测试业务表，本机迁移至 Flyway V14，生成一个启用的 `admin`；撤除临时引导文件后重启仍可登录，业务表为空。本机应用监听回环地址，项目容器仍为 PostgreSQL 与 Redis。临时验证容器、卷、网络已清理。
- 源交接包 r1 以不可变副本保存于 Trellis 归档任务；本次把其中已核验的 2.0 合同副本安装为 `docs/dev-sop/HANDOFF-CONTRACT.md`，内容 SHA-256 为 `e9bc8d61dd64b4dad8e8ee8c0e29c6b47ae306fb0e186d97c113b2ac949addc8`。

## 与原方案的偏差

- 原包为 draft，P-001 曾待定；用户选择“仅主动操作续期”，已写入正式 AC 与本地 TC-017～019，源包保持原样。
- 清理现有测试数据、固定首管理员凭据、本机 `localhost` 启用以及不保留本次备份，均在规划批准后由用户另行明确决定；交付文件不记录密码明文。原本期望的公网入口由用户推迟到服务器 IP/证书准备后。
- 后端 Maven 下载过慢时按用户指示采用阿里云公共镜像。未增加公网密码信誉服务、额外 broker 或真实付费模型调用。

## AC 验收

本表逐一映射正式 PRD 的 73 项 AC。证据栏的 TC 编号均指向已归档的 `research/integration-evidence.md`；该矩阵写明测试环境、直接证据及组合证据。用户于 2026-09-26 对本轮整体验收表示通过；“整体确认”不表示逐条人工实测。涉及未来服务器外部 HTTPS 的完整条件保守列为 `NOT_RUN`，其已完成的隔离子条件见证据栏。

| AC | 结果 PASS/FAIL/NOT_RUN | 实际证据 | 人工验收 |
| --- | --- | --- | --- |
| AC-ACCOUNT-001 | PASS | TC-003；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-002 | PASS | TC-003；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-003 | PASS | TC-015；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-004 | PASS | TC-003；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-005 | PASS | TC-008；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-006 | PASS | TC-008；见验收矩阵 | 整体确认 |
| AC-ACCOUNT-007 | PASS | TC-015；见验收矩阵 | 整体确认 |
| AC-AUTH-001 | PASS | TC-004；见验收矩阵 | 整体确认 |
| AC-AUTH-002 | PASS | TC-013；见验收矩阵 | 整体确认 |
| AC-AUTH-003 | PASS | TC-014；见验收矩阵 | 整体确认 |
| AC-AUTH-004 | PASS | TC-004；见验收矩阵 | 整体确认 |
| AC-AUTH-005 | PASS | TC-013、TC-015；见验收矩阵 | 整体确认 |
| AC-AUTH-006 | PASS | TC-004、TC-038；见验收矩阵 | 整体确认 |
| AC-AUTH-007 | PASS | TC-026、TC-038；见验收矩阵 | 整体确认 |
| AC-AUTH-008 | PASS | TC-006；见验收矩阵 | 整体确认 |
| AC-AUTH-009 | PASS | TC-006；见验收矩阵 | 整体确认 |
| AC-AUTH-010 | PASS | TC-005；见验收矩阵 | 整体确认 |
| AC-AUTH-011 | PASS | TC-005；见验收矩阵 | 整体确认 |
| AC-AUTH-012 | PASS | TC-007；见验收矩阵 | 整体确认 |
| AC-AUTH-013 | PASS | TC-007；见验收矩阵 | 整体确认 |
| AC-AUTH-014 | PASS | TC-036；见验收矩阵 | 整体确认 |
| AC-AUTH-015 | NOT_RUN | TC-037；隔离 Cookie/CSRF/Origin 已核，实际服务器 HTTPS 未运行 | 服务器入口延期 |
| AC-ROLE-001 | PASS | TC-009；见验收矩阵 | 整体确认 |
| AC-ROLE-002 | PASS | TC-009；见验收矩阵 | 整体确认 |
| AC-ROLE-003 | PASS | TC-010；见验收矩阵 | 整体确认 |
| AC-ROLE-004 | PASS | TC-011；见验收矩阵 | 整体确认 |
| AC-ROLE-005 | PASS | TC-010、TC-029；见验收矩阵 | 整体确认 |
| AC-ROLE-006 | PASS | TC-010；见验收矩阵 | 整体确认 |
| AC-ROLE-007 | PASS | TC-012；见验收矩阵 | 整体确认 |
| AC-SESSION-001 | PASS | TC-016；见验收矩阵 | 整体确认 |
| AC-SESSION-002 | PASS | TC-016；见验收矩阵 | 整体确认 |
| AC-SESSION-003 | PASS | TC-015；见验收矩阵 | 整体确认 |
| AC-SESSION-004 | PASS | TC-013；见验收矩阵 | 整体确认 |
| AC-SESSION-005 | PASS | TC-016、TC-035；见验收矩阵 | 整体确认 |
| AC-SESSION-006 | PASS | TC-017、TC-019；见验收矩阵 | 整体确认 |
| AC-SESSION-007 | PASS | TC-018、TC-019；见验收矩阵 | 整体确认 |
| AC-SESSION-008 | PASS | TC-014；见验收矩阵 | 整体确认 |
| AC-ISOLATION-001 | PASS | TC-020、TC-021；见验收矩阵 | 整体确认 |
| AC-ISOLATION-002 | PASS | TC-022；见验收矩阵 | 整体确认 |
| AC-ISOLATION-003 | PASS | TC-020；见验收矩阵 | 整体确认 |
| AC-ISOLATION-004 | PASS | TC-024、TC-025；见验收矩阵 | 整体确认 |
| AC-ISOLATION-005 | PASS | TC-021；见验收矩阵 | 整体确认 |
| AC-ISOLATION-006 | PASS | TC-022；见验收矩阵 | 整体确认 |
| AC-ISOLATION-007 | PASS | TC-023；见验收矩阵 | 整体确认 |
| AC-ISOLATION-008 | PASS | TC-024；见验收矩阵 | 整体确认 |
| AC-ISOLATION-009 | PASS | TC-025；见验收矩阵 | 整体确认 |
| AC-ISOLATION-010 | PASS | TC-035；见验收矩阵 | 整体确认 |
| AC-RT-001 | PASS | TC-026；见验收矩阵 | 整体确认 |
| AC-RT-002 | PASS | TC-028；见验收矩阵 | 整体确认 |
| AC-RT-003 | PASS | TC-029；见验收矩阵 | 整体确认 |
| AC-RT-004 | PASS | TC-030；见验收矩阵 | 整体确认 |
| AC-RT-005 | PASS | TC-030；见验收矩阵 | 整体确认 |
| AC-RT-006 | PASS | TC-027；见验收矩阵 | 整体确认 |
| AC-UI-001 | PASS | TC-004；见验收矩阵 | 整体确认 |
| AC-UI-002 | PASS | TC-031；见验收矩阵 | 整体确认 |
| AC-UI-003 | PASS | TC-009；见验收矩阵 | 整体确认 |
| AC-UI-004 | PASS | TC-009、TC-032；见验收矩阵 | 整体确认 |
| AC-UI-005 | PASS | TC-031、TC-040；见验收矩阵 | 整体确认 |
| AC-UI-006 | PASS | TC-031；见验收矩阵 | 整体确认 |
| AC-UI-007 | PASS | TC-031；见验收矩阵 | 整体确认 |
| AC-UI-008 | PASS | TC-009、TC-032；见验收矩阵 | 整体确认 |
| AC-UI-009 | PASS | TC-031；见验收矩阵 | 整体确认 |
| AC-UI-010 | PASS | TC-033；见验收矩阵 | 整体确认 |
| AC-UI-011 | PASS | TC-033、TC-034；见验收矩阵 | 整体确认 |
| AC-UI-012 | PASS | TC-033、TC-034、TC-040；见验收矩阵 | 整体确认 |
| AC-UI-013 | PASS | TC-006、TC-032；见验收矩阵 | 整体确认 |
| AC-BOOTSTRAP-001 | PASS | TC-001；见验收矩阵 | 整体确认 |
| AC-BOOTSTRAP-002 | PASS | TC-001、TC-002；见验收矩阵 | 整体确认 |
| AC-BOOTSTRAP-003 | PASS | TC-003；见验收矩阵 | 整体确认 |
| AC-INIT-001 | PASS | TC-001；见验收矩阵 | 整体确认 |
| AC-DEPLOY-001 | PASS | TC-002、TC-038；见验收矩阵 | 整体确认 |
| AC-DEPLOY-002 | NOT_RUN | TC-038、TC-040；隔离代理与本机通过，实际服务器 HTTPS 未运行 | 服务器入口延期 |
| AC-REGRESSION-001 | PASS | TC-028、TC-039、TC-040；见验收矩阵 | 整体确认 |

汇总：71 项 `PASS`、2 项 `NOT_RUN`、0 项 `FAIL`。`AC-AUTH-015` 与 `AC-DEPLOY-002` 的延期仅针对未来服务器外部 HTTPS 完整场景；当前本机及隔离环境相关保护已有运行证据。

## Review 问题与处理

- 管理员对自己降级、停用或重置密码后，UI 曾依赖后续列表请求的 401 才卸载管理树；已改为操作成功响应即刻退出，新增三项浏览器回归并通过完整 Playwright。
- 断线重连、重复/旧 revision、离线弱密码和多身份 HTTP 归属的原始证据不足；已补真实浏览器跨后端重启、隔离内网与短时网络调用观测、A/B/ADMIN 分页及安全 404，并纳入后端 25 套/125 项和前端 34 项门禁。
- 未进行独立 `wb-review` 报告；本轮依赖 Trellis check、运行测试和用户整体确认。未来服务器外部 HTTPS 未验，按用户决定保留为非阻塞延期，不写成已经部署。

## 规范沉淀

- 新增 `.trellis/spec/backend/identity-isolation.md`、`.trellis/spec/frontend/identity-session.md`；同步数据库、运行集成、Linux/本机交付、前端目录及索引规范。
- 明确只有用户主动操作刷新会话、异步 owner 从持久请求恢复、STOMP 撤销与提交后通知、自管理成功即刻卸载 UI、测试 schema/Redis 隔离，以及临时 IP HTTP 与未来 HTTPS 的入口配置。

## 残余风险与下一轮

- 当前仅启用 Windows 回环入口；未来服务器 IP、外层代理/证书、Secure Cookie 和精确 WebSocket Origin 仍需在真实服务器配置并执行 TC-037/038。HTTP IP 直连阶段的凭据和会话传输不加密。
- 用户选择不保留本次旧测试数据备份；已验证的临时 dump 在本机启用成功后按指定路径删除，因此旧测试数据不能再从该文件恢复。多人库不得接回无 owner 的旧版二进制。
- 独立 `wb-review`、真实付费模型调用与服务器部署未在本轮执行；不把本机或隔离烟测写成这些场景的证明。
- 本次一并提交的 `wb-review` 技能仍引用 `docs/dev-sop/SOP.md` 与 `docs/dev-sop/WORKBENCH-CHECKLIST.md`；当前仓库尚无这两份文件，后续独立审查前须补齐或调整引用，不能把该技能视为已可完整执行。

## 发布与恢复

- 分支 `feat/auth-isolation` 的 [PR #3](https://github.com/yibccc/ai-workbench/pull/3) 已于 2026-09-26 合并到 `master`；merge commit 为 `fc6e3bb5976c4844ada97c46e90b512e3ceddff2`。本报告及配套技能/模板是合并后的工作区回流，不属于 PR #3 的已审查 HEAD。
- 本机 `ai_workbench.public` 当前为 Flyway V14、1 个启用 ADMIN、业务表为空；Windows 后端/前端在 `127.0.0.1:8080/5173`，PostgreSQL/Redis 数据卷保留。
- 未来服务器配置按 `README.md` 的 `APP_BIND`、`WORKBENCH_WS_ALLOWED_ORIGINS` 和 `WORKBENCH_COOKIE_SECURE` 入口说明操作；上线前保留匹配版本及新备份，再以服务器实际地址完成代理、WebSocket、Cookie 验收。
