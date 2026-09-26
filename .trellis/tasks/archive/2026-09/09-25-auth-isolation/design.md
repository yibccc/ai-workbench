# 集成设计：身份、归属与入口合同

本文件把源包 `solution.md` 的建议与本地 HEAD 的实际结构对齐。父任务不直接写产品代码；各子任务的 `design.md` 负责本层细节。需求与 AC 唯一定义见 `prd.md`，TC 见 `research/handoff/r1/acceptance.md`。

## 边界与数据流

1. **身份**：新持久账号以不可变 UUID `userId` 为业务归属键，用户名仅用于登录/展示。Spring Security 在每个受保护 HTTP 请求建立认证身份；Spring Session Redis indexed 承载多端 Session。`Principal.name`、Session 主体索引与 STOMP 用户定向使用同一稳定标识。匿名只能访问登录所需静态资源/认证入口；`/api/status` 等应用 API 按身份保护，`/actuator/health` 仅供现有内部健康探测且不得经公开代理暴露。业务 API、STOMP 订阅和后台管理均按角色及归属鉴权。
2. **业务**：Controller/Service 从已认证身份取 `userId`，不接受客户端指定 owner；Service 和 Mapper 的读写/计数/联表均限制 owner，跨用户资源在内容/状态判定前统一呈现 404。PostgreSQL 仍是业务权威；主业务表存 `user_id`，子/快照记录通过已验证父归属约束。AI 输入/报告的持久请求携带原始 owner，事务外模型调用、恢复、重试和 after-commit 通知都沿用它。
3. **实时**：`/ws/events` 握手保留，帧协议从 raw JSON 改为 STOMP；Simple Broker 仅在当前单 backend 实例中使用。客户端只订阅 `/user/queue/workbench-events`；服务端按稳定 userId 定向发布 INPUT/REPORT 刷新信号。HTTP GET/数据库仍判定真实状态；断线兜底查询和重连后 GET 保留。身份撤销后旧连接不能收到新事件。
4. **浏览器**：登录态成为四个 `RetainedView` 的上层边界。同身份切页保留草稿；退出/换账号销毁旧身份的视图、pending 跟踪、请求与连接，并阻止迟到响应写进新身份。应用认证失效才进入登录页；403、404 与网络/业务错误保持各自语义。Toast 在登录切页后仍可显示既定 5 秒。
5. **入口**：应用鉴权完整覆盖 HTTP/STOMP 后，同步移除 Nginx Basic Auth 与共享入口密码。保留 HTTPS 外层代理、WebSocket Upgrade/Origin、健康检查不可公开、Windows 本机和 Compose 路径。现有 `.trellis/spec/backend/linux-deployment.md` 是旧合同，实施时与 README/config 一起更新。

## 共同合同

| 合同 | 冻结行为 | 细化责任 |
| --- | --- | --- |
| HTTP | 未认证业务 401；已认证非管理员调用账号管理 403；他人业务 UUID 404；原有业务 400/409 等保持。错误沿用 `ProblemDetail.detail`，但身份/权限细节不可泄漏。 | 认证与归属子任务定义后端 DTO/URI，前端消费同一错误分类。 |
| Session | 每个会话以最近一次用户主动操作为起点计 7 天空闲；后台轮询、自动 STOMP 重连/订阅、心跳、纯推送不续期。多端并存；当前退出仅撤销当前端；禁用/重置/角色变化全端失效；自改密码保留当前端。变更报告成功或空闲到期后，旧会话下一次请求和新事件均不能生效。 | 认证子任务维护独立于框架自动 `lastAccessedTime` 的有效活动期限；实时子任务在连接及派发边界复核，前端子任务只在用户主动交互时发活动信号。 |
| 数据 | 所有业务主实体归本人；项目名与输入/报告 request ID 的唯一语义按用户划分；报告周期版本锁和链同样按用户划分。 | 归属子任务维护迁移、SQL 与异步 owner。 |
| STOMP | `/ws/events`、`/user/queue/workbench-events`；不接收客户端伪造业务 SEND、底层 queue 或越权通配订阅；CONNECT 校验同源/CSRF。 | 实时子任务与认证子任务联合验证。 |
| 前端 | 密码/会话令牌不进入脚本可读存储；登录/管理辅助页不改变四业务主导航；生成图只作视觉参考。 | 前端/部署子任务。 |

## 兼容、上线与回滚形状

- 旧 raw WebSocket 客户端与 STOMP 不双协议兼容；前后端作为一次匹配版本交付，旧页面需刷新。Simple Broker 不承诺离线可靠队列或多 backend 路由。
- V1～V12 已应用迁移保持不变；新迁移先在隔离空库验证。设计阶段尚未核实实际数据库和清理目标；后续经用户另行授权，按 `research/deployment-readiness.md` 核对并清理旧测试行，再在本机应用 V13/V14。含多人数据的新库不能接旧无 owner 应用版本；切换期间若失败，应先关闭入口并恢复到匹配的应用/数据库状态，不能直接回退旧代码。
- 认证切换期间不得先撤掉 Basic Auth 而暴露匿名业务 API。Redis 不可用或主体无法验证时拒绝受保护请求，不退化为匿名。未调用真实模型；本机启用已由用户后续授权，未来服务器入口仍待实际 IP/HTTPS 环境。
- P-001 已确认“仅主动操作续期”；正式 TC-019 在 `research/acceptance-local.md`。原始交接包保留 draft 文本作为来源记录，不让 Spring Session 对 HTTP/STOMP 的默认访问时间更新替代产品规则。其余框架版本、具体 URI/类名、参数均可在已选产品边界内由本地实现细化，但必须通过子任务验证门禁。
