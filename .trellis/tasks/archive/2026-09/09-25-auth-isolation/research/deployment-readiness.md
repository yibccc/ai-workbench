# 多用户本机启用记录

核对和执行日期：2026-09-26。保留切换前快照、用户决定、原子清理边界和本机启用结果。服务器 IP 入口留待用户以后在 `.env` 配置；本次只启用 `localhost`。

## 切换前目标与数据

- 长期运行的 Compose 项目：`ai-workbench`。保留的服务为 `ai-workbench-postgres-1`、`ai-workbench-redis-1`；数据卷为 `ai-workbench_postgres-data`、`ai-workbench_redis-data`。本轮临时 `authuiverify` 与 `d10deployvalidation` 项目的容器、卷、网络均已清理；`agent_memory_neo4j` 是独立项目，未触碰。本机前后端由 Windows 进程运行，不新增常驻应用容器。
- 切换前仓库根目录 `.env` 指向的 PostgreSQL 数据库在 `public` schema 上停留于 Flyway **V12**。只读行数：`projects=20`、`capture_inputs=139`、`todo_items=53`、`work_records=91`、`reports=6`；从属表 `capture_generated_items=141`、`report_sources=241`、`task_events=3`。另有保留的 `flyway_schema_history`。未读取任何业务行内容。
- 用户在交接阶段表示旧库尚未投入使用、内容是测试数据；这是用户决定来源，不是本轮逐行核实。V14 按设计拒绝给这些无可信 owner 的旧行编造归属。直接把当前数据库接给新后端，迁移会在 V14 停止，不能当作已完成启用。

## 已完成的可恢复性准备

- 使用仓库现有 `scripts/local/backup.ps1 -RestoreTo d10_restore_auth_iso_20260926` 对 `.env` 指向的**现有数据库**执行 custom-format `pg_dump`，脚本验证 `pg_restore --list`，退出码 0。
- 临时备份文件曾位于忽略目录 `.local-backups/d10/ai_workbench-8445f56ee5454bcfa8e01e7a689852dc.dump`，SHA-256：`9096C01DEE04011C639A5BDAF3F9938033B6748D24C22965D2D0E4821F7FC9B8`。它未加入 Git 或外发；本机启用成功后按用户“无需备份”的决定，复核哈希并删除了此文件。
- 在新建、命名受限的 `d10_restore_auth_iso_20260926` 数据库中恢复后，独立只读查询到 Flyway V12 和上述五张主表完全相同的行数。该临时恢复数据库已按精确名称删除并验证不存在。切换前原数据库、`.env` 与 Compose 数据卷未修改。
- 切换前 `public` 表清单为八张业务/从属表及 `flyway_schema_history`，没有 `user_accounts`。清理操作**仅针对这八张业务表**，保留 Flyway 历史、数据库本身和 PostgreSQL/Redis 卷；未用 `docker compose down -v` 代替。

## 获批准后的执行边界

1. 再次核对数据库名、当前 schema、Flyway V12、八张表行数和备份哈希；确认没有业务进程写入。若状态变化，重新备份并重新审查范围。
2. 只在用户确认的目标数据库中，以单个 PostgreSQL 事务清空八张业务/从属表（`projects`、`capture_inputs`、`todo_items`、`work_records`、`reports`、`capture_generated_items`、`report_sources`、`task_events`），使用 `TRUNCATE ... RESTRICT` 明确列出全部表；不触碰 `flyway_schema_history`、其他 schema、其他数据库或卷。事务结束后独立查询八表均为空。
3. 为首次启动提供用户指定的 `WORKBENCH_BOOTSTRAP_USERNAME` 和密码，仅在临时忽略文件中传入 Windows 本机后端；密码不记录在此文档。Flyway 在同一干净库上应用 V13/V14，核对一个启用 ADMIN 与空白业务空间，然后用应用登录、CSRF 和私有 API 执行最小烟测。已有任意用户后再次启动不得因初始化配置改变而重置账号。
4. 首次引导成功后，从运行配置移除或封存 bootstrap 密码；日常账号经 ADMIN 界面创建普通 USER（含约定的测试账号）。真实模型密钥不用于验收，默认使用合成数据。
5. 若新版本失败，先关闭公开入口并保留当前数据库；用已验证的备份恢复到**新的隔离数据库**，核对快照，再选择匹配的应用/数据库版本切换。不能把旧无 owner 的应用版本直接接到含多人数据的新库，也不能为方便回滚清空新正式数据。

## 用户决定与执行结果

- 用户确认提交计划、授权对上述八张旧业务表一次性清空；指定首位管理员用户名为 `admin` 并自行给出固定密码。密码只用于临时引导和本机登录验证，不写入 Git、此文档或普通日志。
- 用户明确本机只用 `localhost`，以后由其在服务器的 `.env` 中配置服务器 IP；本轮未开放本机 LAN 或公网入口，也未声称已验证未来的 HTTPS/外部代理。
- 用户不要求保留备份。已验证过的临时备份在本机启用成功后按精确路径与 SHA-256 校验删除；原 PostgreSQL/Redis 卷保留。
- 清理前再次核对 `ai_workbench.public`、Flyway V12、九张表清单、八张业务/从属表的上述行数，确认本机应用端口 8080/5173 未运行。单个 PostgreSQL 事务内取得八表独占锁，复核目标后以 `TRUNCATE ... RESTRICT` 只清空八表；提交后逐表查询均为 0，Flyway 历史仍为 V12。
- 本机 JAR 使用阿里云 Maven 配置重新打包退出 0；Windows `start.ps1 -EnvFile <临时引导文件>` 退出 0，Flyway 应用 V13/V14。数据库查询为 V14、一个启用 ADMIN、八张业务/从属表全空。经 5173 Vite 入口，管理员登录、`/me` 和账号列表正常，项目列表为空。
- 随后停止受管后端/前端，删除临时引导文件，用未修改的原 `.env` 重启。原 `.env` 的 SHA-256 前后均为 `67B917025EA777782912A09312281B3B10F4222225C23583D91820D76B0884E7`；重启后匿名业务 API 为 401、伪造 CSRF 写入为 403、管理员登录/`/me` 正常、账号数 1、八表仍空。真实 Chromium 从本机入口登录并显示工作台，账号/项目读取正常，HttpOnly Cookie 与浏览器脚本存储检查通过。当前 8080/5173 仅监听 `127.0.0.1`，Docker 中只有原 PostgreSQL、Redis 和独立 Neo4j 容器。
- 正式 Docker 镜像也已在本机用阿里云 Maven 公共镜像构建并在独立 `d10deployvalidation` 项目完整烟测；该项目容器、卷、网络已经清理。镜像可供以后服务器交付，未来 IP/HTTPS 入口需按实际服务器环境另验。
