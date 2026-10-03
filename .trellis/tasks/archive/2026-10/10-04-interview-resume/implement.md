# 当前简历与私有原件执行计划

状态：planning；以下均为批准后执行目标，未运行产品验证。先保存父正式规划/P-01/连续执行的真实批准记录，再启动本子任务。一般问题写父research/issues.md于交付末尾汇总；不把规划授权当commit/push/发布/真实付费授权。

## 有序步骤与产物

1. 读取真实git状态、父最新design与本prd，保留他人工作；核Flyway最新版本/现有profile guard/storage deadline/initializer真实参数。load本任务implement.jsonl/spec/research，不接管bootstrap或派implement到父规划任务。
2. 在全新独立PG/Redis/RustFS准备合成身份/未用端口、空模型key；忽略env不输出密钥。核schema不存在、TEST_DATABASE_URL与WORKBENCH_TEST_SCHEMA一致、RustFS桶/卷归属明确。
3. 新增实际下一版resume迁移及MyBatis typed rows/XML，singleton单调version、同owner对象FK、receipt和耐久状态约束。先完成空current/空字符串区分、owner current读取与engine的expectedVersion快照接口。
4. 提取现有小型Markdown helper并使现worker复用；保其他附件格式/byte规则。实现PASTE/EDIT_CURRENT显式mode、import四parts、双20k/1MiB、原bytes/hash/BOM、receipt优先于version、短事务reserve→外put→短事务CAS。
5. 实现owner下载/HEAD、no-store/private/nosniff/attachment和DELETE；EDIT_CURRENT保原件，PASTE解除引用。实现expired预约/待清理恢复、活动I/O保护、最小tombstone、迟到put补偿和每次delete结果，不持网络事务。
6. initializer增加显式canonical两prefix policy更新与probe；未知shape不自动覆盖，管理/app身份分离。nginx只import2m，原社区22m及其他1m不变。
7. 实现或扩展明确source/target的PG+对象bundle/新目标恢复工具，先真实--help/代码核定接口，再将实际命令记录validation。本任务所需community引用一起纳入必要manifest，不改变其业务清理。
8. 运行有意义的针对验证，失败定位并修复；通过后完整Maven一次和隔离RustFS/proxy/restore。独立trellis-check审阅/fix后交接稳定typed合同给engine和ui；父整合接续INT-AC-009/014/025与浏览器011。

主会话协调，implement/check默认Trellis子代理；每派发首行 `Active task: .trellis/tasks/10-04-interview-resume`，原生注入优先，缺注入子侧加载。独立检查不扩大范围重构现有input/report/attachment协议。

## 真正验证入口

先证明目标为新隔离服务，示例端口冲突时记录实际未用值。以下是已定位入口/计划，尚无PASS结果：

```powershell
$env:WORKBENCH_TEST_SCHEMA = 'd9_interview_tests_20261004'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/wbinterview_test?currentSchema=d9_interview_tests_20261004'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '16379'
$env:DEEPSEEK_API_KEY = ''
mvn -f backend/pom.xml clean verify
```

运行前必须已设置并核验POSTGRES_USER与POSTGRES_PASSWORD，均由同一合成隔离测试环境提供，不写密钥或命令输出。profile guard允许 `d9_[a-z0-9_]+_tests_[0-9]{8}`；不能用只换schema替代日常库隔离，不全局覆盖spring.flyway.schemas污染live-acceptance。现有AttachmentValidationTest、RustFsStorageIntegrationTest、社区Attachment*IntegrationTest是回归依据；新Resume*测试名称以实际文件为准，先读取再写-Dtest参数。

初始化入口当前为：

```powershell
python scripts/local/initialize-storage.py --env-file .local-runtime/ai-interview-test.env --endpoint http://127.0.0.1:<verified-unused-port> --bucket <new-private-test-bucket>
docker compose config --quiet
```

尖括号是必须落实的隔离环境参数，不执行未替换命令；本次新增显式policy更新操作的开关以完成工具真实--help为准。`verify-compose.py`有固定隔离project/port，先核代码与占用再运行；`storage-smoke.py`为verifier helper，不虚构独立CLI。

现backup.ps1固定普通source且仅PG，不直接在本任务隔离环境套用。联合bundle定稿后记录实际导出→新DB/桶恢复→完整性验证命令及exit/目标指纹；native/Compose仅停明确owned writer，保服务/卷，不调用down -v。

## 必须覆盖的证据

| 领域 | 必须实际覆盖 |
|---|---|
| identity/cache | A/B/ADMIN/匿名/CSRF、foreign安全404、所有JSON/GET/HEAD/private原件响应headers、无URL/redirect；Range/条件不绕owner |
| formats/boundaries | md-only、BOM、invalidUTF8/control/surrogate、空MD/空正文；解码与最终20k各等号/+1/emoji；1MiB stage等号/+1与完整import composite；不截断 |
| semantics/receipt | EDIT_CURRENT保来源与指针、PASTE解除引用且不put、缺mode400、新File导入最终正文；同ID同hash不多put/version、不同hash409、丢ACK恢复 |
| realPG concurrency | 同version两个独立事务保存、删除后ABA、过期token/CAS、put与DB确认失败、current至多一个；直接SQL核结果不是同写service自证 |
| recovery/cleanup | 活动预约/current引用保护、进程实际退出于已提交UPLOADING同步点、新JVM恢复、迟到put/finalize、delete失败与重试、原key/tombstone不丢 |
| RustFS/proxy | 两prefix原bytes/SHA真SDK、错checksum、anonymous现物GET/HEAD、outside prefix/admin拒绝；nginx import2m运输和业务byte/text gate，社区入口回归 |
| backup/restore | 停写协调快照；必要对象/manifest/dump SHA；全新目标同key恢复；缺对象/坏SHA/既有目标/断流fail closed；current/version/社区与历史snapshot，源库/桶前后不变 |

记录每个RES-AC命令、exit、合成schema/桶、未运行/阻塞边界，工程通过不等于云/付费质量通过。接口current snapshot合同交接时同时注明mode和receipt优先顺序，避免ui/engine重复规则。

## 完成门禁与非破坏回退

RES-AC-01～08需本子任务独立通过；RES-AC-09的面试场景由engine/父整合验证。check核spec/mapper每条owner/事务分层/SDK无scope泄漏，必要缺陷在本任务修复后复跑相关门禁；通过后不无故扩大测试。

原schema历史checksum不变，代码回退保新增表/原件/receipt和最小IAM，不自动清库/清桶/恢复日常源，不添加旧模式默认或兼容fallback。未决项回流父issues；本轮尚未授权commit/归档，父最终“批准并连续执行”如明确覆盖本地提交/归档收尾则直接按该授权接续，不重复询问。真实云申请/adapter、付费模型质量、push/外部发布仍按独立明确授权。主会话最后更新适用spec和delivery事实，不冒称本轮产品完成。
