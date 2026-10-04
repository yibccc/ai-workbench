# 最后 ops 窄范围独立复核

2026-10-04（Asia/Shanghai）。复核本轮后续修复，没有重新设计先前已审过的 IAM、同 schema 快照、完成标志或 fresh volume 合同。本轮没有发现新增需修改的安全/状态缺陷；只新增此报告，没有修改并行 producer 的源文件或验证记录。

## Findings (fixed)

本轮无新增修复。已在实际代码路径确认既有后续修复：

- `scripts/local/interview-backup.py` 的 `native_powershell_environment()` 在环境副本中按大小写无关比较移除 `PSModulePath`；两次 WinPS subprocess 均显式使用该 child env。调用者环境与 datasource/password 等必要环境保留，未放宽 native identity guard。
- `Docker.scalar()` 使用 `psql -X -q -At` 及 ON_ERROR_STOP；`referenced_objects()` 对完整输出执行 `json.loads`，不再取 json_agg 最后一行。SET 标签被 quiet 抑制，多个必要对象的跨行 JSON 不被截断。
- `common.ps1` 在真实启动前解析、两次 hash 核 env，并从传给 backend 的实际进程环境捕获 DB identity。直接 SPRING_DATASOURCE_URL 优先，其次真实 application.yml 使用的 DATABASE_URL，再是 POSTGRES_HOST/PORT/DB；未知 JSON/配置/JVM override 记录 null+safe captureError，普通启动继续。
- `start.ps1` 实际 CWD 仍 repository root；state 分别记录 backend service、backend moduleDirectory、actual workingDirectory、envFile/hash 与 DB proof。frontend credential/JVM stripping 和调用者环境恢复保持原路径。
- `pause-backup-writer.ps1` 与 Python native preflight 对齐上述 service/module/CWD/capture 合同；旧/未知捕获或 hash/DB不一致在 stop 前拒绝，PID/创建时间/完整命令/root 核验保持。Python 先验证全部 designated writers，再停止。

## Findings (not fixed)

无本次窄范围新增未修复问题。新 `private-resumes.md` / `private-interviews.md` 与此 ops 变更没有合同冲突；`local-delivery.md` 已同步新 launch capture、unknown capture 不中断业务启动、备份拒未知证明、fresh record CLI 和完整标志要求，不再保留先前“普通 start 不产 capture”的当前限制。

## Verification

- Tests: **PASS**。本轮实际运行 `python -B -m unittest discover -s scripts/local/tests -v`，**33 tests，exit0**；Docker/admin 调用全部 fake，只有 streaming exact-size 测试使用临时 loopback HTTP。
- PowerShell Parser: **PASS**。本轮解析 `common.ps1`、`start.ps1`、`pause-backup-writer.ps1`，零语法错误。
- Python AST/compile: **PASS**。本轮核 initializer、interview-backup 与 ops tests。
- Lint: ops 无配置的 Python linter；本轮 scoped `git diff --check` **PASS**。TypeCheck: ops 未配置静态检查器，**N/A**；不把 parse/compile 当全项目类型检查。
- 真实运维链路本轮**未重跑**，读取 producer 已落盘 `ops-real-validation.md` 的实际证据：普通 native→source HTTP→2-object dump snapshot bundle→CLI verify→fresh-proof新DB/桶恢复→restored HTTP；31表/Flyway、原件SHA、current B/version2、history A、JD/generation AI_NOT_CONFIGURED、community有效引用、source不变、完整性负例与选择性 IAM 均有真实运行记录。33单测不替代这些结果。
- `proxy-validation.md` 的 Nginx 29 HTTP cases/4匿名现物权限证据是另一路验收；本次仅确认 ops 文档保留各自边界，不将其归为本 reviewer 重跑结果。

本轮未启动 Maven、真实模型、云 API、backup/restore、Docker或进程生命周期命令；未读取/输出 secret env；未变更任何 source/target容器、卷、DB或用户进程。所有真实运行结论应与 producer 对应记录一起用于父交付，而不是从本静态复核推导。
