# 当前简历和社区原件的本地联合备份

`interview-backup.py` 将 PostgreSQL custom dump 与仍被业务引用的私有原件保存为一个 bundle。原件按数据库中的逻辑 key 获取，不需要应用身份拥有 ListBucket。凭据只从明确的忽略 env 文件读取，`--app-identity-env` 传变量名 `WORKBENCH_STORAGE_ACCESS_KEY`，不要传凭据值。

bundle 必须位于 `.local-backups/ai-interview/<新运行目录>`。文件含私有资料，不能提交 Git。在线删除简历或帖子附件不会删除旧 dump/bundle。

## 初始化和明确维护权限

新桶/账号初始化使用：

```powershell
python -B scripts/local/initialize-storage.py --env-file <忽略env文件> --endpoint <明确本地endpoint> --bucket <明确私有桶> --app-identity-env WORKBENCH_STORAGE_ACCESS_KEY
```

应用身份只有该桶 `community/attachments/*` 与 `interview/resumes/*` 的 Put/Get/Delete，保留 `Deny admin:*`。canonical policy 名由桶与指定应用身份摘要派生，身份值不会输出。已存在的共享 policy 不会被静默扩权；明确维护时在上面的命令增加 `--update-policy`，核已知社区单前缀或已知两前缀最小 policy 后，仅创建指定身份的专用 policy 并重新绑定该身份。原 shared policy 不修改，其他账号不会跟随扩权。未知权限、其他账号绑定、公开桶 policy/ACL 都会拒绝。已有密码不会旋转；普通初始化只接受专用 canonical policy。

## 备份

先列出所有连接源数据库的应用 writer 和清理进程。Compose writer 必须是所指 project 的 backend 容器，而且实际数据库配置、schema、端口和网络 alias 必须指向所指 PG 容器。普通 `start.ps1` 新启动的 native backend 会记录 `service=backend`、`moduleDirectory=<repo>/backend`、`workingDirectory=<repo>`、`envFile`（实际绝对路径）、`envFileSha256`、`databaseIdentity={host,port,database,schema}` 与 `captureError=null`，保留原 PID、创建时间、完整命令、root 和实际 directory（repo root）字段。工具把捕获与本次 env、所指源库/schema/PG 端口核对，先验证所有身份才停止 writer，不会自动重启。旧未捕获 state 明确拒绝，不能通过编辑已有未知 state 推定进程环境。还存在任何源数据库连接时会拒绝备份。

```powershell
python -B scripts/local/interview-backup.py backup `
  --env-file <源忽略env文件> --bundle .local-backups/ai-interview/<新运行目录> `
  --app-identity-env WORKBENCH_STORAGE_ACCESS_KEY `
  --source-project <源Compose项目> --source-pg-container <源PG容器> `
  --source-rustfs-container <源RustFS容器> --source-database <源DB> `
  --source-schema <源schema> --source-endpoint <源本地endpoint> --source-bucket <源私有桶> `
  --snapshot-database d10_restore_<未存在的新名字> `
  --writer-container <已核验backend容器> --writers-exclusive
```

有多个 Compose writer 时重复 `--writer-container`。native 使用 `--native-writer-state <启动时捕获环境身份的backend.json>` 替换容器选项，或同时列出两种。捕获依据实际注入的进程配置，优先 `SPRING_DATASOURCE_URL`，再 `application.yml` 的 `DATABASE_URL`，最后 `POSTGRES_HOST/PORT/DB` 默认值；默认 schema 为 public，非 public 仅支持明确单个 `currentSchema` 的 URL。含未知 URL 选项、多 schema、JVM/Spring JSON/外部配置覆盖等无法可靠捕获时，应用正常启动，state 如实记 `databaseIdentity=null` 与不含原值的 `captureError`。备份/暂停守卫会拒绝这种未证实身份，不能靠默认猜测停进程。新启动的捕获也不会补造未知旧进程证明。`--writers-exclusive` 表示列出的确是全部 writer/cleanup，不能用它忽略其他数据库连接。

工具从同一份 dump 恢复的新 snapshot DB 读取 manifest，不从变化中的源库读引用。必要对象包括当前 MD_FILE 简历原件以及社区草稿、所有保留修订仍引用的 READY 附件；失败/删除 tombstone 不要求有可读对象。每个对象都核实际长度和 SHA256，文件名是逻辑 key 的 SHA256，原 BOM/换行/bytes 不改。

源库与 snapshot 所有表的行数/内容 SHA256、Flyway 数据和引用清单都会核对。对象缺失、断流、错长度/摘要或源库变化，均不会产生 `complete.json`。失败 bundle 与 snapshot DB 保留供排查；不自动删库或批量删对象。完成后操作者按原启动方式重启明确的 writer。

## 恢复到全新隔离目标

先选全新 Compose project、未用端口、私有桶名和合成应用账号，model key 为空。在启动任何目标容器前，明确创建两个尚不存在的专用数据卷并记录 identity：

```powershell
python -B scripts/local/interview-backup.py prepare-volumes `
  --target-project <全新项目> `
  --target-pg-volume <全新项目>_postgres-data `
  --target-rustfs-volume <全新项目>_rustfs-data `
  --fresh-volumes-record .local-runtime/<全新项目>/fresh-volumes.json
```

此命令拒已有卷和已有项目容器，只创建这两个明确新卷，不启动服务、删卷或停止进程。随后 Compose 按同 project 使用已创建的两个正确角色卷。目标 PostgreSQL 容器可先启动一个独立 admin DB；env 的 `POSTGRES_DB` 指定随后恢复的 `d10_restore_*` DB，该 DB 必须尚不存在。不要预先初始化目标桶。

工具拒绝与源相同 project/endpoint/bucket/container/volume、既有目标 DB/桶、错误项目/角色/数据挂载路径、记录后重建的卷，以及被其他运行或停止容器引用的卷。新旧判断依据创建前核不存在并保存的卷 identity，不依赖 volume/container 创建时间差。失败演练保留数据，下一次重新准备新项目和新卷，不能给已存在卷补造 freshness 记录。

```powershell
python -B scripts/local/interview-backup.py restore `
  --env-file <目标忽略env文件> --bundle .local-backups/ai-interview/<完整运行目录> `
  --app-identity-env WORKBENCH_STORAGE_ACCESS_KEY `
  --target-project <全新目标Compose项目> --target-pg-container <新PG容器> `
  --target-rustfs-container <新RustFS容器> --target-database d10_restore_<新名字> `
  --target-endpoint <新本地endpoint> --target-bucket <新私有桶> `
  --fresh-volumes-record .local-runtime/<全新项目>/fresh-volumes.json
```

先验证全部本地 dump/manifest/object checksum，再初始化目标桶、恢复 DB 和逐对象同 key PUT→GET 核长度/SHA。所有表/Flyway/引用保持一致且目标无其他数据库连接后，清除未完成标志，最后原子安装独立 `restore-<id>-complete.json`。失败不会留下可恢复的完整标志，已创建的目标不会被自动删除或覆盖；再次运行必须选新的目标。bundle 与恢复记录仅存 endpoint 摘要，不存 URL 或凭据；备份 complete 与 incomplete 共存时一律拒绝恢复。

本地只读检查：

```powershell
python -B scripts/local/interview-backup.py verify --bundle .local-backups/ai-interview/<运行目录>
python -B -m unittest discover -s scripts/local/tests -v
```

业务 HTTP、原件下载和历史面试 snapshot 还需用恢复后的应用验证；脚本的 DB/object 完整性结果不等于浏览器验收。
