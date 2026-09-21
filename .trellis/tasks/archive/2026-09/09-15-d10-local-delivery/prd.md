# 本机交付与验收收尾

## 前端工作区改版子任务

用户追加的四工作区改版由 `09-21-d10-ui-workspaces` 承载，参考 `changes/workbench-ui-refactor.zip` 和重构指引。桌面采用侧栏导航，移动端采用紧凑入口，记录/任务编辑使用抽屉；具体接入与真实接口回归见子任务工件。

## 追加范围：Linux Docker整套部署（用户已授权）

- 配置一次.env后，`docker compose up -d --build`能够构建并运行前端、后端、PostgreSQL、Redis。
- 前端生产静态资源由Nginx服务，并代理API/WebSocket；后端连接容器服务名，模型凭据只存在后端运行环境。
- 提供单用户入口认证，默认回环访问；HTTPS域名由外层反向代理配置示例接入，不要求本轮购买域名或证书。
- 保留Windows手工/脚本开发模式，其Compose命令显式只启动postgres、redis。
- 使用隔离Compose项目验证镜像、启动、认证、健康、API、WebSocket和重启持久化，不覆盖现有业务数据库；不调用付费模型。

## Goal

让用户能在 Windows 11 本机独立启动、停止、恢复和演示工作台，并完成最终验收记录。

## Requirements

- README 说明启动、停止、配置、访问地址和 PostgreSQL 数据保留方式。
- 提供并演练数据库备份与恢复命令，不增加备份产品页面。
- 提供一页架构说明，解释事务、幂等、来源快照和 AgentScope 职责。
- 准备使用脱敏或示例数据的五分钟演示路径。
- 验收记录如实区分通过、失败和仍需观察。

## Acceptance Criteria

- [x] 用户按 README 自行重启、录入并生成报告（2026-09-22用户确认“验收全部通过”，按最终验收反馈记录）。
- [x] 服务重启与数据库备份恢复已实际演练（新JVM及两次新隔离库恢复，内容哈希一致）。
- [x] 架构说明和五分钟演示可用。
- [x] A13—A14、G01—G05 有最终状态；未满 5 个工作日的 G01 保持待观察（见 research/delivery-validation.md）。

## Coverage

A13—A14、G01—G05。
