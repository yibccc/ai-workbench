# 验证技术组合并建立可启动骨架

## Goal

在 Windows 11 上验证并固定 Spring Boot 3、AgentScope Java、DeepSeek、React/Vite、PostgreSQL 与 Redis 的可用组合，交付可启动的前后端骨架。

## Requirements

- 初始化 `backend`、`frontend`、Compose、环境配置示例与忽略规则。
- 固定依赖版本，记录依赖树、实际验证命令、结果和未验证项。
- 提供前后端、PostgreSQL、Redis 健康检查和本机访问入口。
- 凭据只由后端环境读取；真实 DeepSeek 调用可独立验收，不进入普通离线回归。

## Acceptance Criteria

- [ ] 后端启动、前端构建、PostgreSQL 与 Redis 连接验证通过。
- [ ] 至少验证一次最小模型调用；若缺少凭据，明确记录为阻塞且不伪装为通过。
- [ ] 用户可打开本机页面查看运行状态及已验证/未验证项目。
- [ ] 版本矩阵、命令、结果和选定配置已有可追踪记录。

## Out of Scope

- 本任务不实现工作记录、待办和报告业务。
