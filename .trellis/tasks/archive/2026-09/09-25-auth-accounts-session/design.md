# 认证与会话技术设计

## 本地现状与结构

`backend/pom.xml` 有 Web、WebSocket、Redis/JDBC/Flyway，尚无 Security/Session；`application.yml` 的 Redis 目前是基础设施状态用途；V1～V12 没有账号表。新增迁移以稳定 UUID 账号、用户名数据库级大小写不敏感唯一约束、单向密码摘要、ADMIN/USER、enabled、认证版本和创建时间为核心；不改写旧迁移。用户名登录/唯一判断用同一数据库规范化表达式，避免 Java 与 SQL 各自理解大小写。

认证采用既定 Spring Security + Spring Session Redis indexed。Session 主体索引/`Principal.name` 使用稳定 userId，便于按用户撤销全部 Session 和 STOMP 定向。认证版本写入账号并绑定已登录 Session：管理员禁用、重置或实际改角色时原子更新版本；旧 Session 每次受保护请求以及 STOMP 操作均验证版本/启用状态，再按索引删除旧 Session 并关闭关联连接。这样一次索引扫描与并发新登录竞态也不能放行旧身份。用户自改密码更新版本后，仅将当前认证 Session 同步到新版本；管理员重置自己仍撤销全部。Redis/数据库校验失败时受保护访问拒绝，不匿名放行。实现细节须通过真实并发和连接测试证明成功操作后的边界。

至少一个启用 ADMIN 由数据库事务内串行化相关状态修改并再次判断，不能只靠 UI 或无锁 `count→update`。空表首管理员在互斥事务中创建；已有任意用户时初始化配置忽略，不借配置重置密码。启用/禁用不删除业务数据。

密码使用 Spring Security 的自适应单向编码器，最终参数在实现时用依赖解析及 8～64 Unicode 字符无静默截断测试固定；优先无 72-byte 截断的成熟编码方案。用户可见输入长度按 Unicode 码点计，创建/重置/自改共用服务端校验与本地弱密码表。登录失败统一提示，不泄露账号是否存在；建议以 Redis 原子计数实现每账号 15 分钟内 5 次失败后 60 秒等待，成功只清当前账号状态。该阈值/窗口是 DEC-021 授权的局部参数，最终规划确认时一并接受或调整。

HTTP 用 Cookie Session；生产 `HttpOnly`、`Secure`、`SameSite=Lax`，本机 HTTP 开发环境只在 loopback 放宽 Secure。对登录、退出、自改、管理写入与 STOMP CONNECT 接入同源 CSRF 令牌，前端经受保护的初始化/CSRF 能力获取并回传，不全局禁用 CSRF。业务未认证 401，USER 调管理接口 403；管理和校验错误沿用现有 `ProblemDetail.detail`。新端点名/DTO 由实现按当前 Controller 风格确定并在前后端同一变更中固定。

空闲期限不能直接依赖 Spring Session 的 `lastAccessedTime`：普通后台 HTTP 请求和部分 STOMP 入站帧可能刷新该时间。每个独立 Session 在 Redis 维护**只由明确主动操作更新**的有效活动时间/到期标记，登录初始化；标记以 Session ID 为键、原子单调更新并与 604800 秒空闲期一致。共享 HTTP 鉴权边界在业务处理前检查标记是否存在、是否已到期，再决定是否接受主动活动信号；已到期的请求必须拒绝并撤销 Session，不能先写活动时间。主动交互的受认证、CSRF 保护信号可由前端统一发送并限制频率；明确用户发起的业务请求可共用此通道。自动兜底查询、重连/订阅、心跳、服务端推送不得写标记。退出、全端撤销和到期时清理对应标记；合法的 Session ID 轮换（如登录或自改密码保留当前会话）须原子迁移有效时间，不能无故重置空闲起点。框架自身 Session TTL 也设为 604800 秒；自动流量即使刷新原生 TTL，不能绕过有效活动标记。多标签页共享同一 Session 标记，多设备/浏览器 Session 分开计时。

STOMP CONNECT/订阅和新事件派发也需检查同一有效活动标记；到期时关闭旧连接，之后不再发新业务通知。Redis 标记缺失或读取失败时拒绝受保护访问，不退化为匿名或仅信原生 Session。服务端可控时钟与真实 Redis/HTTP/STOMP 联测验证 604799/604800 秒、并发主动信号及自动流量，尤其验证框架原生时间被刷新时仍到期。Spring Session 官方资料说明 WebSocket 集成会把入站消息纳入 Session 活动且维护 Session→连接映射；本任务必须显式配置/验证而不假定默认恰好满足用户语义。[Spring Session WebSocket](https://docs.spring.io/spring-session/reference/web-socket.html)、[索引查找](https://docs.spring.io/spring-session/reference/guides/boot-findbyusername.html)、[Spring Security STOMP CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html)。

## 兼容与回滚

应用认证接线完成前保留旧代理保护；新账号结构与正式多人数据出现后，不用旧无 owner 应用回连新库。启动配置和首管理员凭据仅经部署环境注入，不写仓库；实际清理旧测试库须核实目标与恢复点，绝不作为启动时自动逻辑。
