# 实施计划

1. 记录本机 Java、Maven、Node、Docker 与 Git 环境。
2. 初始化前后端结构、Compose、环境变量示例和健康检查。
3. 逐项验证依赖解析、应用启动、存储连接和最小模型调用。
4. 固定版本并把证据写入 `research/technical-validation.md`。
5. 执行 `docker compose config`、后端构建与前端构建，记录用户验收。

回滚点：若 AgentScope 与 Spring Boot 组合不兼容，仅回退 AI 适配版本，不改变已确认的业务边界。
