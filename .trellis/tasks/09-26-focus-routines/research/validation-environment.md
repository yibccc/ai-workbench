# 隔离验收环境（实施中）

日期：2026-09-26～27。本文件记录本轮真实执行事实；单项 AC 结论以验收审计为准。

## 已启动

- 使用 WSL Docker Compose，以独立项目名 `focus-routines-20260926` 只启动 `postgres` 和 `redis`。项目拥有独立的 `focus-routines-20260926_postgres-data`、`focus-routines-20260926_redis-data` 卷；未使用现有 `ai-workbench` 项目的卷。
- PostgreSQL 镜像 `postgres:17.6-alpine`，测试数据库 `focus_routines_test`，宿主入口 `127.0.0.1:25432`；Redis 镜像 `redis:7.4.2-alpine`，宿主入口 `127.0.0.1:26379`。`docker compose ... ps --format json` 对两者报告 `healthy`。
- 后端首次定向测试曾用隔离 `d9_backend_tests`。V15 尚未发布时调整了约束校验和，完整复跑改用全新隔离 `d9_focus_tests_20260926`（`TEST_DATABASE_URL` 的 `currentSchema` 与 `WORKBENCH_TEST_SCHEMA` 一致）；另有隔离 `d9_live_acceptance` 用于既有 live-acceptance 门禁。浏览器 E2E 仅用同一独立数据库的 `currentSchema=d9_e2e`，并设置独立 Redis 端口。不指向 `public`，未修改 V1～V14。
- 已核实本机 `mvn`、`node`、`npm` 和 WSL Docker 可调用。V16 后端完整门禁最终 31 suites / 165 tests 全过；前端 lint/build 与隔离 Chromium 全量 E2E 56/56 全过，含真实服务端 62 秒后台隐藏场景。V16 JAR 已在独立 `d9_focus_rollout_20260926` schema 重演关写→启写→关写，并在 `d9_focus_manual_20260926` 自动升级后重启人工实例 `http://127.0.0.1:15174`；Chrome/Edge headless 登录烟测通过。真实扬声器声音与非本机入口人工验收尚未完整取得；用户已取消睡眠失联确认，物理睡眠不是该功能的自动确认门禁。具体证据见 [acceptance-audit.md](acceptance-audit.md)。

## 执行边界

测试凭据为本轮合成账号，不写入产品配置或交接源包。现有日常 `ai-workbench` Compose 项目保持原状。后续每次测试记录命令、退出码、HEAD、覆盖范围和失败信息；完成后按本任务实际交付需要处理隔离资源，不以 `down -v` 触碰日常项目。
