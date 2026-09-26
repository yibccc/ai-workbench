# 认证与会话实施计划

前置：父任务最终规划已获用户后续明确确认，当前子任务获原生 `task.py start` 激活。不得在规划阶段执行。

1. 在新 Flyway 版本建立账号/用户名唯一/认证版本约束；用隔离空库和已有用户数据库分别验证首管理员引导与“配置不重置”。旧 V1～V12 保持原样。
2. 引入并解析 Spring Security、Spring Session Redis indexed 依赖；建立稳定 userId Principal、Cookie/CSRF、401/403/ProblemDetail、安全默认拒绝与显式公开端点。核对本机/Compose 入口，暂保留旧 Basic Auth。
3. 统一密码长度、denylist、编码、自改/重置和失败登录状态；以有限阈值/窗口测试且不得静默截断非 ASCII 长口令。
4. 建账号管理与最后管理员事务保护；实现会话认证版本、按主体索引撤销及当前端保留规则，暴露供 STOMP 连接撤销使用的合同。另在每个 Session 维护独立的明确主动操作活动标记；后台 HTTP/STOMP 默认活动不能刷新它，过期判断先于主动信号写入。用同步闸门测试登录/改角色/重置竞争、604799/604800 秒边界及 Redis 故障拒绝旧身份。
5. 验证 TC-001～019（TC-017～019 使用父任务 `research/acceptance-local.md`）、TC-036/037 及 TC-029 的会话部分；和实时子任务联合验证撤销/到期后的新事件。现有 Playwright/E2E 匿名 API fixture 要改为显式登录、CSRF 和多身份样本；`/api/e2e/reset` 继续只在 `d9_e2e` 且 `e2e & !live-acceptance` 可用，不能作为正式匿名后门。检查账号 API 不返回密码/摘要、普通日志和脚本存储无凭据。

验证命令：在 `backend/` 运行 `mvn clean verify`；真实 PostgreSQL/Redis 安全集成须在隔离环境运行。PowerShell 没有 `docker`，但 WSL Ubuntu Docker/Compose/daemon 可用；本轮只确认了 Compose 配置解析，未启动测试环境。环境未准备好时将对应 TC 标为未执行，不把单元测试当全端失效证明。review 重点为初始化竞态、最后管理员竞态、索引/Principal 一致、Session 版本校验、CSRF 和密码边界。回滚前保留隔离库/配置快照；新多人库不能接旧版本。
