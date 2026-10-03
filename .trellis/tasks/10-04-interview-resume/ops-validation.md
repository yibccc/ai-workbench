# Resume ops 实现与验证

本文件记录 ops 子代理负责的实际结果；后端、浏览器、代理完整请求和联合恢复结果分开，不把工具单测当业务通过。

## 文件与接口

- `scripts/local/initialize-storage.py`：canonical `community/attachments/*` + `interview/resumes/*` Put/Get/Delete；保 Deny admin；显式 `--app-identity-env WORKBENCH_STORAGE_ACCESS_KEY` 和 `--update-policy` 维护仅已知社区单前缀最小 policy。未知权限/账号绑定/公开桶拒绝，不旋转已有密码。复用同一 SigV4 Client 添加文件 PUT 与有界 exact-size GET。
- `scripts/local/interview-backup.py`：真实 `backup|restore|verify --help`；明确源/目标/env/schema/project/container/endpoint/bucket；停 owned writers，源库零连接后 dump→全新 snapshot DB 读精确引用；原 bytes/length/SHA 与全部表/Flyway核验；完整标志最后原子写入。恢复拒既有DB/桶、source容器/卷、旧卷；同key PUT→GET。失败保留 incomplete 和目标，不自动重启/删库/删卷。
- `scripts/local/pause-backup-writer.ps1`：只停指定本仓库 backend state 且 PID/创建时间/完整命令/root/目录一致的 native writer，数据服务保留。
- `scripts/local/interview-backup.md`：实际 CLI 的运行合同。
- `scripts/local/tests/test_interview_ops.py`：13项有意义的权限维护、完整性、路径/对象namespace、既有库/旧卷和精确字节边界单测。
- `deploy/nginx.conf`：仅精确 `/api/me/resume/import` 设置2m运输限制、对应代理headers/timeouts；原API1m、社区22m保留。

## 已运行

2026-10-04，Windows11/Python3.11；默认exec sandbox的Windows ACL锁失败，后续用显式 `require_escalated` 在已授权工作区/隔离资源执行。

1. `python -B -m unittest discover -s scripts/local/tests -v`，13 tests，exit0 PASS。覆盖旧policy无显式维护不写、已知维护preserve password/adminDeny、未知广policy不写、其他user policy不写、NotResource拒绝；完整bundle、缺失对象、同长错SHA、dump损坏、无complete、路径遍历、外prefix、旧DB/sourceDB、旧/source卷、真实loopback HTTP exact size/size±1。
2. `python -B scripts/local/interview-backup.py --help`、`backup --help`、`restore --help`及 initializer `--help` 均exit0；PowerShell Parser对pause helper无错误。
3. 主会话授权的新project `wbinterview20261004`：

   ```powershell
   python -B scripts/local/initialize-storage.py --env-file .local-runtime/ai-interview-test/test.env --endpoint http://127.0.0.1:29000 --bucket wbinterview-fixtures --app-identity-env WORKBENCH_STORAGE_ACCESS_KEY
   ```

   exit0 `initialized-and-verified`，两个prefix逐个Put/Get/Delete与原bytes一致；匿名现有对象GET/HEAD403、outside Put403、ListBucket403、bucket policy/ACL管理403、admin list users/self user management403。合成probe精确删除；源日常bucket未触碰。
4. 忽略脚本 `.local-runtime/ai-interview-test/verify-policy-maintenance.py` 在同隔离RustFS、独立合成桶 `wbinterview-policy-maintenance` 执行，exit0 `policy-maintenance-real-PASS`：无显式维护时旧policy拒绝且readback不变；明确维护双prefix探测通过；旧secret仍可读取原测试对象；未知全桶policy拒绝且readback不变。仅测试named policy最后恢复canonical，精确测试对象已删。
5. 在Docker真实端口+Windows socket未占用、project/卷不存在检查后准备全新 `wbinterviewrestore20261004`；独立postgres/redis/rustfs卷，ports25433/36380/29002/29003，目标DB `d10_restore_validation_20261004` 尚未创建，cluster adminDB `wbinterview_restore_admin`，目标桶 `wbinterview-restored` 尚未初始化；忽略env `.local-runtime/ai-interview-restore/restore.env`，model key空。容器健康检查PASS。
6. 因主会话Windows memory/pagefile压力指示，已按labels/ID仅stop恢复项目3容器，exit0、卷和env保留；source project继续运行。真实restore前需启动这些保留容器，不能recreate或挂旧source卷。

## 待运行，不标PASS

- 等后端真实集成测试释放源DB连接并准备current MD_FILE及community有效引用，再停该隔离writer，运行真实backup→verify→新目标restore。需记录DB状态、全部必要对象SHA、缺失/损坏/既有目标/中断拒绝、source前后不变。
- nginx import完整multipart运输/businessbyte/text限制和社区入口回归由主会话完整proxy验收接续。
- 恢复后HTTP current正文/version/原件、community读取和历史面试snapshot由父整合接续。

真实数据/密钥/dump/objects/expanded Compose output均未提交Git或写本文件。未执行云API、付费模型、Maven并行打包、日常容器/卷操作或git提交。
