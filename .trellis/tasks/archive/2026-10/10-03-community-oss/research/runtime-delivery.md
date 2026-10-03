# RustFS与Windows原生交付证据

实际执行日期：2026-10-03 Asia/Shanghai。分支 `codex/community-oss`，尚未提交。全部脚本结果来自实际进程/HTTP/散列，不以配置解析或health推导业务成功。

## 普通本地RustFS

- 只新增 `ai-workbench-rustfs-1`、`ai-workbench_rustfs-data`，镜像 `rustfs/rustfs:1.0.0`；API/console只映射127.0.0.1:9000/9001。旧PostgreSQL/Redis container ID前后完全一致，没有重建旧依赖或启动日常应用。
- 忽略的正常`.env`原始字节前缀不变，只追加9个缺失管理/应用storage键，强随机且两个身份不同；原SHA `67b917025ea777782912a09312281b3b10f4222225c23583d91820d76b0884e7`，追加后SHA `fab4ce1dfe8280885289a6ff0b5691d2350aae91441961e9f5bd7615728686de`。原值/注释、ignored备份及之后native期间SHA均保持；未输出凭据。
- 正式 `initialize-storage.py --env-file .env --endpoint http://127.0.0.1:9000 --bucket workbench-community` 两次exit0。真实私有桶、root/app分离、app仅selected prefix Put/Get/Delete与显式拒绝管理，匿名及越权检查通过；冲突配置不得覆盖。
- 实际普通app身份Put200/Get200/Delete204，原字节SHA等于回读SHA，删除后HEAD404；探针对象已删除。服务healthy，原生JVM使用loopback9000，Compose使用rustfs9000。

原始脱敏状态：`.local-runtime/community-oss-delivery-day-state.json`。完整环境只在ignored文件，不提交。

## Windows现成脚本与实际JVM

实际命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/check-safety.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1 -EnvFile .local-runtime/community-oss-delivery-native.env
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/stop.ps1
```

均exit0；最终driver session30223 terminal exit0。使用已经通过223全量门禁的真实Boot JAR SHA `63b7df371897bd1cede8bc1c11169655f914f4854c70cff5ef2cac55b0d0c74a`，不用Maven另启应用。EnvFile只用独立PG15432/db wbcommunityoss_test、先核验不存在的schema `d9_native_storage_tests_20261003`、Redis16379 namespace `wbcommunity:native:20261003`、合成bootstrap、空DeepSeek与普通9000受限app。没有读取日常业务数据、原账号cookie或调用模型。

| 门禁 | 实际结果 |
|---|---|
| 原生server | 最新packaged JAR/default profile8080 + Vite5173/proxy，owned PID/command/root/creation匹配，两者healthy |
| 认证/拒绝 | 真Cookie登录、CSRF、匿名API/附件401、forged CSRF403；无保护metadata或写入 |
| worker/发布 | PNG128B/UTF8 MD38B实际worker验证并READY，MOMENT/BLOG实际发布/当前reader成功 |
| 下载 | 两file原SHA/长度等于community download，no-store/private、nosniff、Accept-Ranges:none、IMAGE inline/MD attachment；无storage redirect |
| 新JVM持久性 | backend28340→46096、Vite20708→45856；同Redis session、post/revision IDs及原SHA重新200可读，非复用旧JVM |
| backend环境 | 实际读取运行进程PEB，只记录计数：RUSTFS管理0、appstorage7；两次JVM均正确 |
| frontend环境 | 实际运行Vite敏感DB/model/storage/root/AWS/bootstrap变量计数0；两次均正确 |
| validator环境 | 实际捕获packaged validator Java子PID46380、19220，均观测到环境且敏感变量0；不是静态剥离函数推断 |
| finally | 正式stop exit0；8080/5173 listener全无，owned PID文件全无，JAR SHA/normal.env SHA及日常PG/Redis/RustFS IDs不变 |

此轮只保留专有验证schema与其中仍有引用的两个测试对象，不把仍被稿件/历史引用的普通桶对象当孤立文件删除。普通应用可按README现成start脚本使用；实际日常schema仍未迁移，本轮不把使用者人工启动/声音/持续使用当作已观察通过。

## harness问题与原始证据

首轮现成start真实exit0且应用healthy，ignored Python driver `capture_output` 的PIPE被Windows隐藏子进程继承，communicate未见EOF；只对driver改真实文件handles并精确stop已拥有两个PID，未改产品start。另一次header断言把HTTPMessage转dict后失去大小写无关读取，只修ignored helper并复用自己已发布post/objects；没有为断言创建新副本或删引用。失败终态与修复记录保留，随后完整restart/download/environment/finally门禁实际PASS。

原始状态：`.local-runtime/community-oss-delivery-native-state.json`、`community-oss-delivery-native-attempt1-state.json`、`community-oss-delivery-native-attempt2-state.json`，以及native notes/logs；秘密EnvFile/PEB原变量不输出或提交。
