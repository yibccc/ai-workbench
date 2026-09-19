# AI Workbench

个人 AI 工作台的本机开发骨架。当前 D1 只提供前端状态页、后端健康检查、PostgreSQL/Redis 连接探针和显式触发的 DeepSeek 最小调用，不包含业务功能。

## 本机启动

1. 复制 `.env.example` 为 `.env`，本地数据库默认值可直接使用；如需模型探针，再填写 `DEEPSEEK_API_KEY`。
2. 启动依赖：`docker compose up -d --wait`。Windows 用户也可在 WSL Ubuntu 中进入仓库的 `/mnt/<盘符>/...` 路径后执行同一命令。
3. 在仓库根目录用 PowerShell 将 `.env` 注入当前进程，再启动后端：

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^\s*[^#][^=]*=' } | ForEach-Object {
     $name, $value = $_ -split '=', 2
     [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
   }
   Push-Location backend
   mvn spring-boot:run
   ```

   `mvn spring-boot:run` 本身不会自动读取根目录 `.env`；上述注入确保后端与 Compose 使用同一组数据库端口、口令和 DeepSeek 配置。结束后可在另一个终端继续后续步骤。
4. 启动前端：`cd frontend && npm ci && npm run dev`。
5. 打开 <http://localhost:5173>；后端状态接口为 <http://localhost:8080/api/status>，Actuator 健康接口为 <http://localhost:8080/actuator/health>。

Compose 端口由 `.env` 中的 `POSTGRES_PORT` 与 `REDIS_PORT` 控制，后端使用同名变量连接本机映射端口。默认分别为 5432 和 6379；若本机确有不可移除的端口冲突，可在 `.env` 中改为其他未占用端口，容器内部端口无需修改。

后端、PostgreSQL 与 Redis 都只监听 `127.0.0.1`，避免开发口令或可产生费用的模型探针暴露到局域网。如未来需要从其他设备访问，应先补充认证与网络访问控制，而不是直接扩大监听地址。

模型调用不会随普通健康检查自动发生。配置凭据后，手动执行：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai/probe
```

版本选择、验证证据与未验证项记录在 `.trellis/tasks/09-15-d1-technical-foundation/research/technical-validation.md`。
