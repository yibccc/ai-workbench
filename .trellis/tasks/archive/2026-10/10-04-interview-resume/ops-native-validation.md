# Native launch capture 实现与验证

2026-10-04，Windows11 / Windows PowerShell5.1 / Python3.11。只落实已批准联合备份的启动身份合同，未运行普通应用start、Maven、真实DB备份/恢复或停止源writer；checker此前的complete marker、app专用policy、fresh volume proof与全writer preflight修复保留，31项ops回归均通过。

## 文件与最终行为

- `scripts/local/common.ps1`：集中resolve真实env文件，读取时计算SHA；`Get-EnvConfiguration`给默认/显式EnvFile同一绝对path+SHA合同。`Get-BackendLaunchIdentity`使用实际注入后的process environment捕获最小非secret字段；`Save-OwnedProcess`保原PID/创建时间/完整command/root/directory，并只给新backend添加捕获。
- `scripts/local/start.ps1`：backend仍WorkingDirectory=repo root，启动参数、端口、frontend生命周期不变；在Start-Process之前解析捕获，在同一实际环境scope内保存新state。Vite进程移除所有SPRING_/WORKBENCH_及DB/model/storage/root/JVM backend配置，随后恢复调用者环境。
- `scripts/local/pause-backup-writer.ps1`：核新service/module/实际CWD、envpath/SHA、明确DB与Get-OwnedProcess原身份；ValidateOnly不stop。缺字段、null identity、安全captureError、错env/DB都拒绝。
- `scripts/local/interview-backup.py`：仅对齐native捕获合同和真实数据库优先级；保原checker全writer preflight/源端口与schema guard，不改policy/complete/fresh-volume算法。
- `scripts/local/tests/test-native-capture.ps1`及既有`test_interview_ops.py`：新增真实捕获/配置优先级/未确认身份拒绝回归，保原checker断言。
- `scripts/local/interview-backup.md`：同步普通start新捕获与未确认身份行为。

新backend state除原ownership字段外包含：

| 字段 | 来源/值 |
|---|---|
| service | backend |
| moduleDirectory | `<repo>/backend` |
| workingDirectory、directory | 实际 `<repo>`，不伪写成backend目录 |
| envFile | 实际resolved默认/显式env绝对路径 |
| envFileSha256 | 当时读取文件的SHA256，不记录内容 |
| databaseIdentity | 已证实的 `{host,port,database,schema}`，或null |
| captureError | 已证实时null；未证实时只有安全code，无原配置/异常文本 |

解析优先级：实际`SPRING_DATASOURCE_URL` → application.yml的`DATABASE_URL` → `POSTGRES_HOST/PORT/DB`及真实默认`localhost:5432/ai_workbench`。缺URL schema为public；非public仅明确single currentSchema。未知/多schema/JSON/JVM或外部配置覆盖会留下`databaseIdentity=null`与`DATABASE_IDENTITY_UNCONFIRMED`；读文件期间/启动前文件变化使用安全ENV_FILE_* code。

这些capture失败**不阻断普通应用启动**，不要求关闭既有JVM/JSON/合法URL配置；联合备份与暂停工具会拒绝未证实身份。没有default猜测来补造proof，也不修改/补造未知旧运行进程state。普通stop继续使用既有PID ownership合同。

## 实际验证

1. `python -B -m unittest discover -s scripts/local/tests -v`：31 tests，exit0 PASS。含checker此前29项边界，新增DATABASE_URL/direct Spring URL优先级、明确非public schema、blank/multiple/unknown option拒绝、无法证明的JVM/Spring配置与native captureError拒绝。
2. `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local/tests/test-native-capture.ps1`：exit0 PASS。

   - 默认EnvFile与显式EnvFile只在新合成root里的`.env`验证，绝对path/SHA一致，没有读取日常用户`.env`。
   - public defaults、DATABASE_URL与direct Spring override；不合法URL/schema、JSON/JVM覆盖只拒绝proof解析；memory-only JVM参数可解析。
   - 新service/module/CWD与envpath/hash；capture/state均无密码、key或配置原值；SPRING_/WORKBENCH_/DB/model/storage/root/JVM变量判定为backend-only，VITE_API_TARGET保留。
   - fake CIM原PID/创建时间/command/root匹配接受，变更拒绝。
   - 在实际注入环境中启动一个新的隐藏短PowerShell自有进程，调用真实capture+Save；真实ValidateOnly不stop且state保留。后续env hash变化、错sourceDB、未知旧state均拒绝，进程仍存活。
   - 再以无法证明的SPRING_APPLICATION_JSON环境启动新的自有短进程：capture返回null+安全code，Start-Process和Save仍成功；暂停guard拒绝且进程存活。证明capture失败不成为应用启动门禁。
   - finally只停止这两个测试创建的自有短进程、删除精确合成测试文件及空目录；没有触碰日常/源backend。
3. `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local/check-safety.ps1`：exit0 PASS；既有真实self PID ownership、变更created/command/root拒绝及所有PowerShell语法检查通过。
4. focused `git diff --check`：exit0 PASS；只提示仓库既有LF→CRLF转换，没有空白错误。

## 尚待主会话验证

最终backend package后的真实JVM/native start、frontend无secret运行验证、真实joint bundle与全新恢复目标、完整nginx multipart/业务边界仍由主协调，未以短进程测试宣称应用或恢复验收通过。新IAM专用policy的真实再验证属于ops-check待项，此轮没有改initializer或跑该真实IAM。

Windows默认exec sandbox ACL锁失败，后续用明确require_escalated执行已授权工作区轻量检查。未输出.env、密钥、expanded Compose；未改后端Java/frontend/spec/git/lifecycle，也未stop source `wbinterview20261004` 或启动/停止任何Docker服务。
