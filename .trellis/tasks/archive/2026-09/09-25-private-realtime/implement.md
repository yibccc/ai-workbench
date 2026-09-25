# 私有实时实施计划

前置：父任务最终规划获用户后续明确确认，认证 userId/Session 撤销与持久事件 owner 合同已定。只在本子任务被原生 Trellis 激活后执行。

1. 建立 Spring STOMP endpoint、Simple Broker、用户目的地和消息授权；用现有 Origin 清单和认证子任务 CSRF/Principal 接线。不能把 `/user` 路径本身当完整权限控制。
2. 将 `WorkbenchEventHub` 改为按持久 owner 的 after-commit 定向通知；补 Session 撤销映射/旧连接拒绝，避免全连接广播和成功前通知。
3. 与前端子任务协作替换 raw WebSocket 解析为 STOMP 客户端；保留 GET 权威、兜底跟踪、去重和重连，同步身份切换清理。自动 CONNECT/订阅、心跳、推送不更新认证子任务的有效主动活动标记；检查原生 `lastAccessedTime` 变化不会绕过到期门禁。
4. 用真实 STOMP 帧与两个用户/同用户多会话执行 TC-026～030：匿名、伪造 CONNECT/订阅/SEND、事务回滚、撤销后新事件、断线兜底和服务重启后的恢复；与认证子任务联合执行父任务本地 TC-019 及 TC-029。
5. 在 `backend/` 运行 `mvn clean verify`，在 `frontend/` 运行 `npm run lint`、`npm run build`、`npm run e2e`（前端依赖/浏览器可用时）。协议/Session/Redis/数据库联测需要隔离服务环境；可用 WSL Ubuntu Docker/Compose，本轮只确认配置解析，未运行联测时记录未验证。

Review 门禁：用户标识一贯性、目的地矩阵、撤销后发送竞态、HTTP 故障独立性与旧页面兼容告知。失败时回退匹配前后端版本，不把旧全局广播重新暴露给多人库。
