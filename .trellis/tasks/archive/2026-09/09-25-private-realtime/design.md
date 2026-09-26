# STOMP 用户通知设计

## 本地现状

`WorkbenchWebSocketConfig.java` 用 `@EnableWebSocket` 注册 raw `/ws/events` 并检查 Origin；`WorkbenchEventHub.java` 维护全连接集合，事务提交后向每个连接广播。`frontend/src/hooks/realtime.ts` 解析 raw JSON、按 eventId/revision 去重、30 秒内指数重连、15 秒跟踪兜底；pending ID 使用全局 localStorage 键。后端和前端必须共同变更协议与身份边界。

## 协议及授权

采用 Spring STOMP Simple Broker，单 backend 实例内保存订阅；保留 `/ws/events`，配置应用 `/app` 与 broker `/queue`，用户订阅只允许 `/user/queue/workbench-events`。服务端 `convertAndSendToUser` 的用户标识与认证 Principal 的稳定 userId 完全一致；不信任 CONNECT 头、query 参数或客户端自称的 userId。入站消息授权：认证后只可订阅既定本人目的地；拒绝直接 `/queue/**`、其他 `/user/**`、通配目的地和客户端业务 SEND；HTTP 仍承载写入。握手校验明确 Origin，CONNECT 使用 CSRF。Spring Security 官方 WebSocket 安全参考确认 CONNECT 的 CSRF 规则，实际权限矩阵用协议帧测试而不靠路径字符串假设：[官方文档](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html)。

归属子任务在持久 INPUT/REPORT 状态变更后提供 owner；保持 `afterCommit` 才发布，消息载荷沿用 `eventId/kind/entityId/state/revision/occurredAt` 刷新信号，不能携带他人业务正文。只给 owner 的有效 Session 发送；认证子任务的撤销动作必须关闭关联 WebSocket，并在新事件派发/连接活动时验证 Session 及认证版本，弥补删除索引与连接关闭的时间差。Spring Session 的 WebSocket 集成提供 Session→连接映射，但本地配置和撤销竞态需实测：[官方文档](https://docs.spring.io/spring-session/reference/web-socket.html)。

前端以 STOMP 客户端替换 raw `WebSocket.onmessage` 协议处理；仅当前身份持有连接/订阅与 pending 跟踪。收到事件、重连、兜底周期均重新 GET 跟踪对象；修订号重启后不阻止恢复，重复事件不触发写入。身份退出/更换时断开旧连接、取消自动重连与定时器并清理/隔离 pending ID；普通切页保留同身份跟踪。P-001 已确认：自动 CONNECT、SUBSCRIBE、心跳和纯推送均不得更新认证子任务的**有效主动活动标记**；Spring Session 原生 `lastAccessedTime` 可能随入站帧变化，不能作为有效期限权威。连接建立、订阅、事件派发均复核标记，到期关闭连接且不送新事件；用真实帧和可控时钟验证 TC-019。

## 兼容与故障

旧 raw 协议不兼容，部署需匹配前后端并提示旧页面刷新；Simple Broker 不承担离线消息或多实例路由。STOMP 故障只损失即时提示，HTTP 查询恢复最终状态；数据库提交失败不发布成功通知。Redis/Session 不能确认身份时拒绝订阅与发送，不回退全局广播。
