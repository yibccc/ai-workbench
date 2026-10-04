# AI 面试实际 native、IAM 与联合恢复验收

2026-10-04（Asia/Shanghai）。本轮真实运维链路 **PASS**：普通 native 启动 → 当前简历/原件/历史/社区 HTTP → 停本次 writer 的联合 bundle → 全新 proof 目标恢复 → 恢复后业务 HTTP → 完整性负例 → 精确 owned 应用清理。未调用真实模型或云 API。所有正文、凭据、dump、对象和带完整输入的harness状态仅在忽略路径；本文件只记录资源、状态、摘要和计数。

## 隔离资源与边界

| 项目 | 实际资源 |
|---|---|
| source数据服务 | `wbinterview20261004`，PG25432 / Redis36379 / RustFS29000，原合成桶`wbinterview-fixtures` |
| source本轮新DB | `wbinterview_native_delivery`，全新schema `d9_interview_native_tests_20261004`；不用fullsuite的`wbinterview_test`或E2E库 |
| source配置 | `.local-runtime/ai-interview-ops-real/native.env`，两个直接datasource URL指向同一新DB/schema；Flyway同schema、独立Session Redis namespace、空模型key、随机合成bootstrap账号 |
| ordinary native应用 | 正式 `start.ps1 -EnvFile`，loopback8080/5173，WorkingDirectory仍repo root |
| 同dump snapshot | `d10_restore_native_r3_snapshot_20261004`，从同一custom dump恢复后提取引用 |
| 成功bundle | `.local-backups/ai-interview/native-real-20261004-r3` |
| 全新restore proof目标 | `wbinterviewrestoreproof20261004`，PG25434 / Redis36381 / RustFS29004；新DB `d10_restore_proof_20261004`，新私有桶`wbinterview-restored-proof` |
| fresh volume证明 | `.local-runtime/ai-interview-restore-proof/fresh-volumes.json`；由prepare-volumes在不存在时创建正确角色卷，再启动容器；未使用旧stopped restore项目 |
| restored业务应用 | 同一packaged JAR，独立owned loopback28080、target DB/storage/app identity与独立Session namespace，空模型key |

源/目标数据服务与源keeper均未停止、删除、重建或共享数据卷。两个source writer运行窗口只涉及本轮新DB。普通`.env`没有编辑，源HTTP及原件不进入跟踪文件/标准输出。

## 实际入口与结果

私有harness为 `.local-runtime/ai-interview-ops-real/verify-real.py`，使用当前正式工具typed Namespace调用同一 `backup/restore/verify_bundle` 函数；CLI readonly verify也独立运行。harness参数与正式CLI合同一致，凭据仅文件，不在argv。

```powershell
python -B .local-runtime/ai-interview-ops-real/verify-real.py prepare
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .local-runtime/ai-interview-ops-real/start-native.ps1
python -B .local-runtime/ai-interview-ops-real/verify-real.py source-http
# 实际缺陷修复后的明确owned restart，保同env与原owned前端：
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .local-runtime/ai-interview-ops-real/restart-native.ps1
python -B .local-runtime/ai-interview-ops-real/verify-real.py backup
python -B scripts/local/interview-backup.py verify --bundle .local-backups/ai-interview/native-real-20261004-r3
python -B .local-runtime/ai-interview-ops-real/verify-real.py restore
python -B .local-runtime/ai-interview-ops-real/verify-real.py negative
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .local-runtime/ai-interview-ops-real/start-restored.ps1
python -B .local-runtime/ai-interview-ops-real/verify-real.py restored-http
python -B .local-runtime/ai-interview-ops-real/iam-selective.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .local-runtime/ai-interview-ops-real/stop-owned-apps.ps1
```

最终上述成功链路各入口exit0。最后table/资源/既有桶核验为本轮一次性Python here-string，结果保存忽略 `final-db-result.json` 与 `final-resources.json`，没有另造产品入口。

### 普通native与环境

- 启动前再次核8080/5173空，且无live owned旧backend/frontend；使用正式start入口，不kill/reuse日常进程。
- 实际backend state的service/module/CWD/env绝对路径+SHA/databaseIdentity与所指新DB/schema一致，`captureError=null`。只在合成调用进程中设置JVM heap/metaspace/code-cache预算，没有改用户或系统Java设置。
- 普通`.env`前后字节SHA不变，start调用者环境摘要不变。
- 通过本次actual Node `--import` audit在真正Vite进程启动边界观察，SPRING_/WORKBENCH_/DB/root/storage/model/JVM相关变量 `forbiddenCount=0`，不是仅检查待传env字典。audit不输出值。

### source真实HTTP

- 经Vite代理真实login/CSRF；UTF-8 `.md`原件带首BOM及CRLF，与最终编辑正文A不同。导入receipt确认成功；GET当前为A，sourceKind=MD_FILE；原件GET byte-for-byte相同，私有no-store/nosniff响应成立。
- 用服务端当前version创建 JAVA_BACKEND / MID / N3 session；正常profile模型key为空，实际异步generation明确 **FAILED / AI_NOT_CONFIGURED**，正文snapshot为A。没有把无模型场景宣称生成成功。
- EDIT_CURRENT保存B，current version=2，原件ID/bytes保持；既有session resumeSnapshot仍是A。
- 独立JD分析实际 **FAILED / AI_NOT_CONFIGURED**，原JD资料/来源保留可读取；没有绕SUCCEEDED分析合同把失败JD加入session。
- 创建私有BLOG草稿，真实multipart上传community MD，随后显式SaveDraft attachmentIds绑定，owner原件读取成功。

### bundle和恢复

- fresh launch capture、源env/hash/DB/schema/PG映射和PID完整身份preflight后，工具只stop这次source backend。其他项目、full/E2E writer和数据服务未操作。
- custom dump → 同一dump恢复的新snapshot DB → 精确必要引用manifest；**2个必要对象**，分别current resume原件与community草稿引用。应用Get精确key，无ListBucket授权。
- snapshot、源before/after与恢复目标 **31张表的row count/SHA256**一致，包括Flyway和全部interview数据。源库在backup、restore、restored HTTP及负例后均不变。
- target restore核明确新DB/桶、正确角色fresh卷proof、无其他容器引用、无source卷/容器共享，再同key PUT→GET核size/SHA；完成记录仅在全验证完成后安装。
- restored JAR真实login与GET证明：current正文B/version2，BOM原件，history snapshotA及generation失败状态，独立JD原文/失败状态、community显式draft引用与原件全部正确。
- restored HTTP后再次比较全部31表SHA，均与source停写快照相同，GET未增加AI job/修改业务状态。

对象与bundle摘要：

| 内容 | SHA256 |
|---|---|
| current原始MD bytes | `58717ea68046e245bd6cb32dff3f31c03f580e65b98ea48a79278001338afc5a` |
| community原始MD bytes | `6d7c569a5b1e627d15366da8821a01c288f898cbd251f40db12cd8cc776ef8a9` |
| custom dump | `a645bc725de5ae06a6bf3d774a39733071bfb430457ab1ce82d0e3216018753c` |
| manifest | `eb1056c8a4692ca8f2ea4a3dcab15df5929c81ba16883dc4eee216766132ad4e` |

### 实际完整性负例

全部在忽略bundle copy、unused target DB名或已恢复目标上核验，未损坏源或成功bundle：

| 负例 | 实际拒绝结果 |
|---|---|
| 必要对象copy缺失 | object-file-size |
| 同长度对象copy改字节 | object-file-sha256 |
| custom dump copy追加字节 | bundle-database.dump-checksum |
| copy中断、无complete | bundle-not-complete |
| 重跑指向既有target DB | existing-destination-database；不覆盖 |
| unused DB名但已有target桶 | fresh-target-bucket-required；未创建新DB，目标与源table摘要不变 |

### 修复后真实专用IAM

- source指定合成应用账号明确 `--update-policy` 后，专用canonical双prefix Put/Get/Delete、现有对象匿名GET/HEAD403、outside Put403、ListBucket403、bucket policy/ACL管理403、admin/self管理403均实际通过。
- 全新同隔离RustFS桶 `wbinterview-iam-dedicated-proof`：两合成账号都先绑定known community-only shared policy；无明确操作拒绝迁移；明确操作只将指定账号迁到专用两prefix。root读回证明旧shared shape与另一账号binding不变；另一账号resume Get/Put403，两个原密码仍有效。
- 真正PUT带错误Base64 SHA256 checksum，RustFS返回400。所有本轮IAM probe只删除精确测试key，未清桶或批量删除。

### 普通本地配置已落实

父主会话明确授权后，仅在进程内部读取普通`.env`的storage配置；没有输出任何配置值。先核所指endpoint为明确loopback、所指桶已经存在，再用`--app-identity-env WORKBENCH_STORAGE_ACCESS_KEY --update-policy`的同一正式定向维护函数，落实所指应用identity的专用双prefix canonical。实际两个prefix Put/Get/Delete、现有probe的匿名GET/HEAD403、outside/ListBucket及bucket/admin/self管理拒绝通过；读回确认现应用enabled、专用known canonical shape正确。共享policy不修改、密码不旋转、`.env`前后字节SHA不变，不操作普通业务DB、日常应用进程或业务对象；只清精确临时probe。这补齐普通local start后resume导入所需权限，不把隔离29000验证当日常本地已配置。

## 实际发现并修复的运维缺陷

仅修改 `scripts/local/interview-backup.py` 与对应ops tests，主会话收到并授权局部修复；未改Java/frontend/迁移或并行Maven。

1. Python作为pwsh子进程时继承edition-specific PSModulePath，WinPS preflight无法autoload Get-FileHash。两个WinPS子进程现在在env副本中case-insensitive移除此变量，让明确WindowsPowerShell入口初始化标准模块路径；caller及DB env不变，所有原guard保留。新增child-env独立回归。
2. PostgreSQL json_agg(record)可跨行，旧reader仅取末行，两个prefix对象时截断JSON。psql使用-q抑制SET tag，reader解析完整aggregate；新增双prefix多行回归。

失败r1/r2 run均保留incomplete、无complete，不补造成功；r2已生成dump/snapshot但没有完整对象bundle。修复后按主明确授权重新启动同env的本次backend，使用全新r3运行目录与新snapshot完整重跑到PASS。

补充验证：`python -B -m unittest discover -s scripts/local/tests -v`，**33 tests，exit0**；两个ops修复文件focused `git diff --check` exit0。真实原件/权限/PG/恢复结果来自本节实际运行，不以单测替代。

## 最终资源状态与限度

- 8080/5173/28080全部已释放；source frontend和target backend均按matching PID/created/command/root精确停止并保留私有capture证据。source backend由backup guard停止，没有自动重新启动日常应用。
- source与proof target的postgres/redis/rustfs **6个数据容器均healthy**，数据卷、DB、成功/失败bundle、snapshot和忽略env保留。源keeper、其他项目容器没有停止。
- 在线删除不代表历史dump/bundle删除；本次没有删除私有历史备份。
- 本轮不包含真实NGINX 2m完整运输边界或browser交互，父整合另记录；普通Vite/JAR真实HTTP与fresh restore已验收。
- 正常空模型key验证只证明资料/失败状态/snapshot持久性；成功题目与评估由独立deterministic E2E验证，真实模型质量/费用与阿里云API/adapter/迁移仍按批准规划延期。
