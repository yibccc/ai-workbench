# Resume ops 独立检查

2026-10-04（Asia/Shanghai），仅审查 `scripts/local/initialize-storage.py`、`interview-backup.py`、`interview-backup.md`、`pause-backup-writer.ps1`、`tests/test_interview_ops.py` 与 `deploy/nginx.conf`。已读取完整保存的 native hook、任务 PRD/design/implement、ops-validation、父存储研究和真实批准记录。后端/UI由其他代理负责，本检查没有修改其代码。

## Findings (fixed)

- File: `scripts/local/interview-backup.py`
  - Issue: backup/restore 先安装 complete 再删除 incomplete；删除失败会返回失败，但 verify 仍认可完整 bundle。
  - Fix: `finish_run` 先完成未完成标志清理、最后原子安装 complete；安装失败重建最小 incomplete，重建失败保留原异常并输出独立 cleanupStage。verify 拒绝 complete/incomplete 共存。新增标志共存、清理失败、写标志失败回归。
- File: `scripts/local/initialize-storage.py`
  - Issue: 更新 bucket shared named policy 会同时扩大其他绑定账号的权限。
  - Fix: policy 名按明确 bucket+app identity 摘要派生；显式 `--update-policy` 核 known shared 最小 shape 后，只创建指定 app 专用两前缀 canonical 并重bind该 app，不改 shared policy、不旋转密码。普通初始化不接受 shared binding。保留未知权限 fail closed、新旧两前缀探测与 admin deny。新增 canonical 无需维护、shared union 必须明确维护和仅所指身份 rebind 断言。
- File: `scripts/local/interview-backup.py` / `pause-backup-writer.ps1`
  - Issue: 仅验证 Compose project/service 或 native repo/PID，不能证明被停止的是所指 source DB writer；native身份校验原先在停止Compose之后才执行。
  - Fix: Compose核实际POSTGRES配置/直接Spring URL、schema/5432与所指PG网络alias；native要求启动时捕获 envFile/hash/databaseIdentity 并核本次源env、DB/schema/PG映射及原PID身份。先对全部writers进行只读preflight，再停止。缺捕获旧state拒绝，无默认猜测。新增错DB、错网络、Spring URL override、第二writer错误时无stop、native缺捕获/环境变更回归。
- File: `scripts/local/interview-backup.py`
  - Issue: volume/container创建时间差小于60秒不能证明新卷或正确数据角色；合成复现确认交换角色也会通过。
  - Fix: 新增本需求专用 `prepare-volumes`，拒已有目标卷/project容器，明确创建两种正确角色卷，记录ignored身份；restore必填 `--fresh-volumes-record`，校验volume identity、project/role/data mount与唯一当前容器挂载，拒source/recreated/foreign/other-container卷。恢复前后核无未知目标DB连接。新增创建拒旧、两卷精确创建、角色交换、错误路径、重建、其他停止容器引用回归。
- File: `scripts/local/interview-backup.py` / `interview-backup.md`
  - Issue: bundle及恢复记录含local endpoint URL，且文档的维护、native capture、fresh volume接口落后于审查修复。
  - Fix:记录endpointSHA256，不记录URL/凭据；文档同步真实CLI与安全失败行为。

`deploy/nginx.conf` 无需自修：仅 exact `/api/me/resume/import` 为2m，社区attachment regex仍22m，server默认API为1m；身份/业务1MiB/20k仍由后端执行。本次静态检查不代表真实代理请求通过。

## Findings (not fixed)

- 真实 joint backup → fresh restore、新guard/IAM的真实负例、完整Nginx multipart/business boundary：**NOT_RUN**。源库正在其他代理后端测试中，本检查未执行任何真实Docker命令、停写、建卷、备份/恢复；由主会话协调隔离服务空闲后执行。
- 默认 `scripts/local/start.ps1` 旧state未捕获新 envFile/hash/databaseIdentity，本次按ownership没有修改启动模块。native联合备份要求主验证harness随实际启动捕获字段；如需普通native启动直接支持，应由主在启动模块补捕获及验证，不能给未知已运行进程补造证明。该限制在操作文档明确。
- `.trellis/spec/backend/private-attachments.md`、`local-delivery.md`、`linux-deployment.md` 需主会话统一同步：两前缀+app专用canonical维护、env身份preflight、prepare-volumes proof、import2m和bundle完整标志。此轮仅负责ops文件，未越ownership修改spec。

## Verification

- Tests: **PASS**，`python -B -m unittest discover -s scripts/local/tests -v`，29 tests，exit0。所有Docker/admin client均fake；只有streaming size测试启动临时loopback HTTP。
- Lint: 未配置Python lint工具； scoped `git diff --check`、Python AST/compile、PowerShell Parser均 **PASS**。仓库未发现pyproject/ruff/mypy/pylint/pyright/tox配置（rg无匹配exit1，属于无配置结果）。全项目frontend lint由主及UI检查接续。
- TypeCheck: ops未配置静态类型检查器，**N/A**；Python parse/compile与PS语法已通过，不冒称全项目类型检查通过。Backend/Maven及frontend build不由本轮执行。
- CLI: `interview-backup.py --help`、`backup --help`、`restore --help`、`prepare-volumes --help`、initializer `--help` 实际参数已核对；restore需新 `--fresh-volumes-record`。
- 真正RustFS双prefix/IAM探测旧证据见ops-validation；本次IAM专用policy修复后的实际再验证 **NOT_RUN**。联合恢复/proxy **NOT_RUN**，没有用轻量单测替代。
- Windows默认沙箱helper ACL失败，后续经require_escalated执行授权工作区只读/编辑/纯单测。不读取或输出secret env，不调用真实模型/云/API、不做git提交。

主会话真实恢复应选另一全新project，先prepare-volumes再启动容器；先前已存在/stopped目标不得事后补造fresh proof，源writers未释放前不执行backup。
