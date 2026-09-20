# AI Workbench

个人 AI 工作台的本机应用。当前已支持项目创建、改名和归档，当天或历史工作记录的维护，以及带项目、备注、优先级、可选期限、乐观锁和完成记录联动的待办管理；同时保留后端健康检查、PostgreSQL/Redis 连接探针和显式触发的 DeepSeek 最小调用。

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

后端启动时由 Flyway 自动执行数据库迁移。项目接口位于 `/api/projects`，工作记录接口位于 `/api/records`，待办接口位于 `/api/tasks`。待办列表可按 `status`、`projectId`、`unassigned`、`priority` 和 `due`（`ALL/OVERDUE/TODAY/UPCOMING/NONE`）筛选；更新和删除必须携带当前 `version`，过期版本返回 409。记录列表的 `date=YYYY-MM-DD` 与待办期限筛选均按 `Asia/Shanghai` 解释。项目归档后历史记录和待办仍保留原归属，但不能再把新数据关联到该项目。

待办状态只能通过 `POST /api/tasks/{id}/complete` 和 `POST /api/tasks/{id}/reopen` 修改，不能通过通用编辑接口修改。完成待办会在同一数据库事务中生成一条自动工作记录；重复完成请求不会重复生成记录。`PUT /api/tasks/{id}/completion-result` 可补充完成结果，`GET /api/tasks/{id}/events` 可查询状态历史。重开或删除待办只会使当前自动完成记录失效，既往结果和事件保留，手工工作记录不受影响。

AI 统一输入使用 `POST /api/inputs`：后端先保存原文、客户端 `requestId`、Asia/Shanghai 解析基准和 `PROCESSING` 状态，再在事务外调用 DeepSeek。用 `GET /api/inputs/{id}` 读取状态与生成条目；失败后可调用 `POST /api/inputs/{id}/retry`，并沿用首次解析基准。同一 `requestId` 始终指向同一批次，相同文本使用不同 `requestId` 时会创建不同批次；PostgreSQL 处理令牌和租约保证并发重发、重试或服务重启后最多生成一批结果，Redis 不参与最终正确性。默认模型调用超时为 4 分钟、处理租约为 5 分钟；启动时会拒绝“租约不长于模型超时”的配置，避免仍在执行的有效请求被错误回收。未被编辑、完成、重开或删除的成功批次可通过 `POST /api/inputs/{id}/revert` 整批撤销，重复撤销幂等；原文和批次历史继续保留。AI 只关联已有活动项目，无法匹配的项目保持未分类。

Compose 端口由 `.env` 中的 `POSTGRES_PORT` 与 `REDIS_PORT` 控制，后端使用同名变量连接本机映射端口。默认分别为 5432 和 6379；若本机确有不可移除的端口冲突，可在 `.env` 中改为其他未占用端口，容器内部端口无需修改。

后端、PostgreSQL 与 Redis 都只监听 `127.0.0.1`，避免开发口令或可产生费用的模型探针暴露到局域网。如未来需要从其他设备访问，应先补充认证与网络访问控制，而不是直接扩大监听地址。

模型调用不会随普通健康检查自动发生。配置凭据后，手动执行：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai/probe
```

版本选择、验证证据与未验证项记录在 `.trellis/tasks/09-15-d1-technical-foundation/research/technical-validation.md`。
