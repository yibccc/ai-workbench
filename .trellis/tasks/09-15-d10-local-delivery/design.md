# 技术设计

- 当前实现采用controller/service/impl/mapper三层及React feature目录，交付文档以最新代码为准。
- PostgreSQL恢复只在新建隔离验证数据库中演练，禁止覆盖public业务数据；备份置于忽略目录，记录恢复校验和清理清单。
- D9用户已确认功能验收；D10独立启动、连续5工作日使用和人工耗时对比按各自的实际记录验收。
- Windows支持本机Docker CLI或WSL Ubuntu Docker；可增加最小PowerShell启停/备份脚本，启停须校验本项目PID与端口，不停止无关进程。

- 交付面向本机单用户环境，凭据由后端环境配置，前端产物不含密钥。
- README 是运行入口；架构说明和验收证据放在本任务 `research/`，避免重新建立根级 `docs`。
- 数据恢复以 PostgreSQL 标准备份/恢复命令为准，并记录实际演练结果。
- 用户已追加授权Linux部署和单用户入口访问控制，本轮补齐；多用户账户体系不在范围。

## Linux部署

- Docker多阶段构建Java17后端及Vite前端；.dockerignore排除.env、备份、测试产物和工作区配置。
- Compose默认提供postgres、redis、backend、frontend四服务；端口入口默认127.0.0.1:8088，后端8080只在内部网络监听，数据库仅现有回环映射用于本机开发兼容。
- 容器后端覆盖监听为0.0.0.0，通过postgres/redis服务名连接；宿主机开发仍回环监听。
- Nginx保护页面/API/WS的单用户Basic Auth，凭据由运行环境读取且缺失时入口拒绝启动；明文HTTP默认仅本机，公网由域名HTTPS反向代理接入。
- WS允许来源配置化且默认保留开发来源；部署用实际入口URL配置，禁止任意Origin放开。外层TLS代理保留Host/协议/Upgrade。
- 更新README中本机依赖启动为`docker compose up -d postgres redis --wait`，避免未配置服务器认证影响Windows开发。
