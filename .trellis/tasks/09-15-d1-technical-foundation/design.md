# 技术设计

- 后端使用 Java、Maven、Spring Boot 3；AI 适配隔离在 AgentScope 网关后。
- 前端使用 React 与 Vite；PostgreSQL 和 Redis 通过 Compose 提供本机环境。
- DeepSeek 使用 OpenAI 兼容接口，地址、模型名和凭据全部配置化。
- 先做最小探针验证兼容性，再固定版本；验证记录放入本任务的 `research/technical-validation.md`。
