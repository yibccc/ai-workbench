# 子任务 PRD：私有实时通知

父任务：`09-25-auth-isolation`。主责 FR-008；AC 唯一定义见父任务 `prd.md`，TC 方法见父任务 `research/handoff/r1/acceptance.md`。依赖认证子任务的稳定 userId/Session 撤销合同和归属子任务的持久事件 owner。

## 目标与边界

保留 `/ws/events` 握手位置，把当前 raw WebSocket 全局广播替换为基于 Session 身份的 STOMP 用户通知；同一用户所有有效会话能订阅 `/user/queue/workbench-events`，其他用户与匿名连接收不到。INPUT/REPORT 事件仅提示客户端重新 GET，数据库仍是事实；断线时 HTTP CRUD 可用、跟踪对象继续兜底查询，重连后重新订阅并 GET。

不引入 RabbitMQ、SockJS、业务广播 topic、客户端业务 SEND、离线持久队列或多 backend 分布式消息；不添加通知中心。旧 raw JSON 协议不与 STOMP 双栈兼容，前后端须匹配交付。

## 可观察验收

- `AC-RT-001/002`：A、B 各自仅收本人事件；A 两个有效 Session 均能收 A 的同一新事件，匿名不可形成可用业务订阅。见 TC-026/028。
- `AC-RT-003`：退出、禁用、重置、角色变化、超时和自改密码撤销的旧会话，在操作成功后不收新事件；被保留的自改当前会话仍有效。共享 `AC-ROLE-005`、`AC-SESSION-002～004/008`；见 TC-029。
- `AC-RT-004/005`：STOMP 单独故障不阻断 HTTP；15 秒跟踪兜底、重连 GET 和重复/旧事件去重保留，不产生重复业务写入或状态回退。见 TC-030。
- `AC-RT-006`：伪造他人 `/user` 路径、底层 queue、通配订阅或业务 SEND 均不能窃听或伪造业务变更；正常同源客户端仍可连接。见 TC-027，并共享 `AC-AUTH-007/015`。

P-001 已由用户选择“仅主动操作续期”：自动 STOMP 重连/订阅、心跳和纯推送不得刷新 7 天空闲期限；到期后的旧连接不能收新事件。自动 HTTP 查询同样不续期，由认证/前端子任务联合落实；验收以父任务 `research/acceptance-local.md` 的 TC-019 为准。
