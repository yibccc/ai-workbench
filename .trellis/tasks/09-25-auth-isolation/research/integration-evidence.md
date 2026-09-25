# 全量验收证据矩阵

来源：父任务 PRD 的 73 个 AC、源包 acceptance.md 的 TC-001～040，以及本地 acceptance-local.md 对 TC-017～019 的补充。本矩阵记录实际证据；计划、静态代码和单项构建通过不等于整项 TC 通过。

证据键：**B** = 现有 PostgreSQL/Redis 的隔离 schema 全量后端 `mvn verify`（最终 25 套件/124 项）；**O** = `BusinessOwnerIsolationIntegrationTest` 及归属 SQL 审查；**R** = `PrivateRealtimeIntegrationTest` 真实 STOMP 帧与事件测试；**E** = 隔离 Playwright 全套 34 项；**D** = `d10deployvalidation` 隔离 Compose 构建/烟测；**W** = Windows 本机 JAR/Vite 合成环境烟测；**L** = 用户授权后现有 `ai_workbench.public` 的本机一次性启用与重启验收。`已核对` 表示所列可观察条件有直接测试或相应跨层证据；`组合证据` 的未直接同场景验证部分写在本行，不应被误读为已实际部署。

| TC | 场景 | 主责 | 当前结论 | 运行证据/剩余项 |
| --- | --- | --- | --- | --- |
| TC-001 | 空库起点及一次性首管理员 | 认证/集成 | 已核对 | B 空表并发 bootstrap；D 首次 ADMIN 与 V14；L 授权清空八表后本机 V14/一个 ADMIN/业务全空，引导凭据已移除 |
| TC-002 | 已有用户时初始化配置无效 | 认证/集成 | 已核对 | B 已有用户 bootstrap 忽略；D 更换配置重建后端仍旧密码可登录；L 删除临时引导配置后重启本机后端，仍仅一 ADMIN 且原登录有效 |
| TC-003 | 受邀账号及普通测试账号 | 认证/集成 | 已核对 | E 管理员建 USER 并登录；B 角色/认证规则 |
| TC-004 | 普通密码登录与界面范围 | 认证/集成 | 已核对 | E 登录页与工作台；B 普通密码登录 |
| TC-005 | 失败登录保护与成功后复位 | 认证/集成 | 已核对 | B 真实 HTTP 限流/成功清本账号；Redis TTL |
| TC-006 | 密码边界一致性 | 认证/集成 | 已核对 | B 三种设密 HTTP 7/65 拒绝、8/64 Unicode 可用 |
| TC-007 | 本地弱密码表与离线校验 | 认证/集成 | 组合证据 | B 三种设密共用 denylist；代码无外部信誉调用 |
| TC-008 | 账号唯一标识与不可变性 | 认证/集成 | 已核对 | B 大小写重复 409；API/UI 无改用户名路径 |
| TC-009 | 固定角色及账号管理授权 | 认证/集成 | 已核对 | B ADMIN/USER 403 与角色；E 管理页入口 |
| TC-010 | 角色改变与重新认证 | 认证/集成 | 已核对 | B 角色变化旧会话 401、新登录按新角色授权 |
| TC-011 | 最后启用管理员保护 | 认证/集成 | 已核对 | B 并发最后 ADMIN 保护与 DB 计数 |
| TC-012 | 管理员自禁用与自降级 | 认证/集成 | 已核对 | B 自降级/最后 ADMIN 409；E 三种自操作 200 后立即卸载管理树/登录页，网络失败不延迟 |
| TC-013 | 管理员重置普通密码并撤销全端 | 认证/集成 | 已核对 | B 管理员重置密码/全端旧 Cookie 401；R 旧 socket 关闭 |
| TC-014 | 自行修改密码 | 认证/集成 | 已核对 | B 自改旧密码失效且仅当前 HTTP Session 保留；R 重连 |
| TC-015 | 禁用/启用不删除业务数据 | 认证/集成 | 已核对 | O 真实管理 HTTP 停用/启用；旧 Session 401、数据/关联原样 |
| TC-016 | 多端登录与当前退出 | 认证/集成 | 已核对 | B 多独立 Session；R 当前退出只关本人 socket |
| TC-017 | 完全静默的7天空闲超时 | 认证/集成 | 已核对 | B 可控时钟 604799/604800；R 到期拒新事件 |
| TC-018 | 主动使用刷新空闲期且无记住我 | 认证/集成 | 已核对 | B 活动标记边界；E 键盘/表单主动信号及无记住我 |
| TC-019 | 后台自动通信不续期（本地补充已确认） | 认证/集成 | 已核对 | B 自动 HTTP 不续期；R 自动 STOMP/推送不续期；E 轮询不发 activity |
| TC-020 | 所有资源读路径与404 | 归属 | 组合证据 | O 五类资源读/列表/分页/ADMIN 404；SQL 全路径审查 |
| TC-021 | 写入owner与跨用户变更 | 归属 | 已核对 | O 跨用户改名/更新/删/撤销/报告编辑 404、原行不变 |
| TC-022 | 项目名与跨用户关联 | 归属 | 已核对 | O 跨用户同名项目、同用户约束、复合 FK 拒跨 owner 关联 |
| TC-023 | 输入与报告幂等按用户分隔 | 归属 | 已核对 | O 输入/报告同 requestId 跨用户独立，同用户幂等回归 |
| TC-024 | 异步AI归属、重试、撤销及恢复 | 归属 | 已核对 | O 迟到 AI、失败/重试/无上下文恢复/撤销与生成行 owner |
| TC-025 | 报告取材、快照和版本链 | 归属 | 已核对 | O 来源快照与双用户同周并发独立链；B 原版本回归 |
| TC-026 | STOMP鉴权与身份不可伪造 | 实时/前端 | 已核对 | R 真实帧匿名/伪造身份/owner 定向；E 客户端订阅 |
| TC-027 | STOMP目的地授权与伪造消息 | 实时/前端 | 已核对 | R 真实帧拒底层队列/外国 user/通配/SEND |
| TC-028 | 同用户多订阅及事务提交后通知 | 实时/前端 | 已核对 | R 实际 INPUT/REPORT 事务提交前后与回滚无通知 |
| TC-029 | Session撤销关联STOMP | 实时/前端 | 已核对 | R HTTP 禁用/角色/重置、退出/自改/到期对应 socket 撤销 |
| TC-030 | 断线、去重与重连恢复 | 实时/前端 | 已核对 | E 单独阻断 STOMP 时 HTTP CRUD/15 秒 GET/重连；D 同一浏览器跨真实后端重启、CONNECTED 与 SUBSCRIBE 均增加，较低 revision 仍 GET 最终数据；重启前重复 eventId/旧 revision 无额外查询 |
| TC-031 | 四工作区与账号入口的响应式布局 | 前端 | 已核对 | E 四区几何/窄屏，390px 实际 ADMIN/USER 手机菜单 |
| TC-032 | 管理表单和副作用提示 | 前端 | 已核对 | E 管理表单、取消/焦点/确认与副作用提示 |
| TC-033 | 认证失效后5秒Toast | 前端 | 已核对 | E 真实撤销即刻登录页、Toast 5 秒/重计/提前关闭 |
| TC-034 | 错误分类不误踢登录 | 前端 | 已核对 | E 403/404/网络失败不踢出；B ProblemDetail |
| TC-035 | 退出换账号后的前端数据隔离 | 前端 | 已核对 | E A 四区草稿/pending/STOMP/迟到响应→B 四区与 reload 隔离 |
| TC-036 | 密码存储与凭据不外泄 | 认证/安全 | 已核对 | B PBKDF2/完整 Unicode 长度、HttpOnly Cookie；D 建号/登录/重置/自改合成口令与 Session 值均不在账号响应或后端/代理日志，口令不在 `user_accounts.password_hash` 字段原文中；浏览器 local/sessionStorage 无登录口令或 Session 值 |
| TC-037 | Cookie、CSRF与Origin | 认证/安全 | 组合证据 | B SecureCookie+HTTP CSRF；R CONNECT CSRF/Origin；D 代理环路；L 本机匿名 401/伪造 CSRF 403，未来外部 HTTPS 待实际服务器入口 |
| TC-038 | 旧认证移除与入口兼容 | 部署 | 组合证据 | D 无 Basic Auth、API/STOMP/健康隔离；外部 HTTPS 待实际入口 |
| TC-039 | 既有数据库业务语义回归 | 全量回归 | 已核对 | B 25 套件/124 项含真实 PG、并发/日期/版本/恢复 |
| TC-040 | 既有UI及本机使用回归 | 全量回归 | 已核对 | E 34 项 UI 回归；W Windows JAR/Vite 合成环境代理登录/CSRF/业务读写；L 正式本机 5173 浏览器登录、工作台和账号/项目读取，HTTP 401/403 |

## 已有局部证据（不能替代逐 TC 联验）

- 后端：使用现有 PostgreSQL/Redis 的独立 `d9_owner_tests_20260925` test schema、独立 `d9_live_acceptance` profile schema，最新 `mvn verify` 25 套件/124 项退出 0，0 失败/错误；覆盖认证、E2E 管理员门禁、V14 空库迁移与旧数据阻断、A/B/ADMIN 归属、异步输入/报告链、同周双用户并发锁以及真实 STOMP。归属审查强化 TC-015/024 的真实 Cookie/无 SecurityContext 断言后，已包含在这次全量 124 项中：最新 backend/src 修改时间 2026-09-26 00:26:18，25 份 Surefire 报告均在 00:27:14～00:28:05 生成，JAR 于 00:28:07 生成。`application-test.yml` 仅用 `WORKBENCH_TEST_SCHEMA` 约束 test Profile 的 Flyway 目标；未清理原 `d9_backend_tests` 旧业务行。
- 实时：真实 STOMP 帧 `PrivateRealtimeIntegrationTest` 定向 7/7 退出 0，覆盖 owner 路由、越权目的地、撤销、改密重连与空闲到期；实际 INPUT/REPORT 外层事务的提交前/提交后/回滚事件和行断言，以及管理 HTTP 禁用/改角色/重置后的旧 socket 关闭与新登录订阅。浏览器单独阻断 STOMP 时 HTTP CRUD、15 秒 GET 和重连由 Playwright 34/34 覆盖；新增隔离 Compose 浏览器脚本在同一页面/Session 中重启真实后端，断言 CONNECTED/SUBSCRIBE 计数增加，较低 revision 经 GET 保持数据库状态；重启前重复 eventId/旧 revision 不产生额外查询。测试行由独立数据库夹具建立及完成，避免真实模型调用。
- 前端与镜像：审查后的 `npm run lint`、`npm run build` 和 E2E TypeScript 检查均退出 0。新建隔离 `authuiverify`（只含 PG/Redis）的最终聚焦 Playwright 3/3、完整 34/34 均退出 0；TC-012 自管理成功即刻登录页、TC-031 手机账号菜单与 TC-035 四区 A→B 迟到状态有直接浏览器断言。正式后端 Dockerfile 使用阿里云 Maven 公共镜像从零下载并构建成功，Maven 2 分 06 秒、完整构建约 2 分 11 秒；此前 Central 构建已运行逾 4 分钟仍在下载，按用户要求停止并换源。正式前端镜像构建 7 秒退出 0。将这两个正式镜像标记给 `d10deployvalidation` 后，隔离烟测 `--skip-build --browser-node /mnt/d/APPS/node/node.exe` 退出 0，覆盖四服务健康、应用登录/CSRF、合法与非法 Origin WebSocket、V14、浏览器跨进程重连/去重/存储凭据检查，以及更换 bootstrap 配置后重建 backend 时账号摘要/业务数据/原 Session 保持、原密码可用而新配置无效。建号、管理员重置、自改的合成口令及 Session 值也在数据库摘要、账号响应、后端/前端日志作本地精确比较，均未外泄。安全断言为显式异常并在完整烟测中执行。两个临时项目的容器/新卷/网络均已按标签独立查询为空，原 `ai-workbench` 服务及卷保留。
- Windows 本机：`scripts/local/start.ps1 -EnvFile <ignored synthetic file>` 与原 `stop.ps1` 的短时 JAR/Vite 运行退出 0；8080/5173 loopback 健康、Vite 代理匿名业务 401、合成 ADMIN 登录及 `/me`、伪造 CSRF 写入 403、有效创建/读回项目均有实际请求。结束后两个受管 PID/端口清零，仅本轮新建的 `d9_windows_local_tests_20260926` schema 删除，原 `.env` SHA-256 前后相同；未新建容器或调用模型。无参 `start.ps1` 对原 `.env` 的解析结果与显式原文件在内存比对一致。
- 正式本机启用：用户另行授权后，清理前再次核对 `ai_workbench.public` Flyway V12、八表行数与完整表清单，单事务锁表、复核、`TRUNCATE ... RESTRICT`，提交后八表皆 0、Flyway 历史保留。阿里云 Maven 配置本机打包约 5 秒，临时引导文件启动 Windows JAR/Vite 后 Flyway 到 V14、一个启用 ADMIN、业务全空；经 5173 入口登录、`/me`、账号/项目列表通过。删除临时引导文件，原 `.env` SHA-256 不变并重新启动，匿名业务 401、伪造 CSRF 写入 403、原管理员登录及 `/me` 正常。独立只读 `frontend/e2e/local-acceptance.mjs` 用真实 Chromium 对本机入口登录，断言工作台、私有 API、账号/项目读、HttpOnly Cookie 和脚本存储无口令/Session，退出 0。两个本机进程仅监听 127.0.0.1:8080/5173；Docker 仍只有原 PG/Redis 与独立 Neo4j。已验证的临时备份按用户“不需要备份”选择删除。服务器 IP/HTTPS 尚待用户以后在服务器 `.env` 中配置、验证，不在本机结果内。
