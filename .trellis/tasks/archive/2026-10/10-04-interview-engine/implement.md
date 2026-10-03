# AI 面试引擎执行计划

状态planning；父最终规划/P-01及连续执行尚未批准。父批准后先验收resume读取/单调version/快照锁合同，再由原生Trellis start本引擎子任务，不start父规划任务。每dispatch首行 `Active task: .trellis/tasks/10-04-interview-engine`，原生上下文注入优先，fallback读jsonl+prd/design/implement。

## 有序执行与审查门禁

1. 核git状态/真实HEAD/父最终design/已批准消息，保用户改动与bootstrap任务；完整读数据库规范，重查Flyway下一版和SDK2.0.3 API。
2. 准备全新隔离PG/Redis/RustFS/schema/合成账号，空真实model key；resume接口已验证，所有后端tests和E2E另库。
3. 新增实际下一版engine schema，owner同FK、session/turn唯一、operation receipts、JD source/hash、统一jobs及evaluation；验证fresh migration、原数据升级和历史checksum。
4. 实现JD parse/GET/retry/DELETE，copy不consume、成功分析多场复用、明确改文/移除/取消失效；create receipt优先、server current expectedVersion快照、新场幂等与同ID不同payload409。
5. 实现专用gateway/严格出题2N，显式关闭SDK/transport自动retry；本地fake HTTP实际计数先验证，再接异步job。
6. 实现PENDING→worker claim PROCESSING的queue/lease、专用executor、reject/deadline、启动+周期只FAILED、token+lease+未删除finalize；不加通用队列或扩改旧report/input。
7. 实现当前draft/submit/receipt/version、空SUBMITTED、只推进一次、丢ACK、complete与last-submit原子冻结/首次评估；交付typedAPI给UI。
8. 实现每组评估/固定hash、成功组复用、未答0/失败null、有效2N平均两位HALF_UP、纯read历史、任意阶段delete/最小tombstone及迟到fencing。
9. 针对实际新增测试先读文件再执行其-Dtest，修故障；完整clean verify一次；独立trellis-check→修复→相关复验，父整合接UI/E2E与全部26INT-AC。
10. 回填真实validation/AC evidence，补必要spec；同范围连续执行修复，普通问题末尾汇总。最终父已批准的本地commit/归档收尾照授权接续，不再重复问；push/PR/外部发布、真实付费调用未含。

## 真实可用命令与隔离前提

命令为规划入口，尚未运行。必须先设置并核验POSTGRES_USER/POSTGRES_PASSWORD均属于新隔离库（秘密只忽略env、不打印）；连接端口样例须落实为新服务，不靠只换schema绕过日常库。

```powershell
$env:WORKBENCH_TEST_SCHEMA = 'd9_interview_tests_20261004'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/wbinterview_test?currentSchema=d9_interview_tests_20261004'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '16379'
$env:DEEPSEEK_API_KEY = ''
mvn -f backend/pom.xml clean verify
```

schema guard允许d9_*_tests_YYYYMMDD；TEST_DATABASE_URL.currentSchema=WORKBENCH_TEST_SCHEMA。沿现有test profile，不全局覆盖spring.flyway.schemas影响live-acceptance。迁移测试结束恢复pooled connection原schema。E2E由父/UI用独立库d9_e2e/非默认Redis与PG凭据启动，保ADMIN reset守卫。

具体SDK javap证据在父research/backend-contracts；付费key空，模型endpoint只local计数server，不安装依赖/执行外部参考代码。数据/schema清理仅已核验新目标；不可指向public、现有app库/卷/桶。

## 必须证据矩阵

| 范围 | 实际验证要求 |
|---|---|
| owner/HTTP | A/B/ADMIN/anonymous/CSRF，foreign先404/no正文状态泄漏，private cache；每Mapper与后台owner |
| create/JD | current版本快照+同createID固定结果/不同payload409/删除重放不复活；JD原文或方向改失效，成功同hash多场，无静默ignore/GET调用 |
| generation | 3/5/20和整数范围、少题/超题/空追问/parent重复/坏JSON/截断不READY，配置难度主题一致 |
| answers | 真独立事务并发+barrier、旧tab/轮次、draft无推进、空SUBMITTED/lock、submit丢ACK原ID、complete与last-submit竞争一次 |
| SDK/manual-only | 真实SDK fakeHTTP计数429/5xx/断连/timeout/坏结构单attempt一次，唯一显式retry增加请求；全部GET/report/轮询零新增 |
| jobs/restart | PENDING长排队非PROCESSING、queue拒绝/expiry、有效lease不误恢复、过期/new token/删除迟到成功失败均零写；实际JVM中断→新进程→仅FAILED/手动retry |
| scoring | 主+追每组固定hash、逐轮有效alias/范围/feedback；全未答0不call、空submitted需评分；失败分null/无总分、success复用、BigDecimal固定平均 |
| deletion/multi | 生成/作答/评估各阶段删除；正文/JD/快照/结果无普通读，current/别场不动；两场+第三场独立保存/重登恢复 |
| boundaries/max | JD10k/+1、answer5k/+1含非BMP及无最低长度；20main/40turn/最大resume+JD+answers组合fakeHTTP完整成功或明确失败，无截断/残缺/伪总分 |

不写未运行PASS；mock gateway与真实SDK计数分层记证据；真实模型质量/成本/延迟DEFERRED独立列，不能据此默认禁用20题或更改上限。

## 完成与回退

适用INT-AC以prd映射全部有真实证据，稳定DTO/状态/错误/receipt交接UI，父进行跨层/既有五区/auth/community回归。主代理保存审查记录、权限/范围差异和delivery事实；普通问题记父research/issues，需新范围/兼容/不可逆的只停相关依赖动作。

只回退本任务授权代码，保新表/receipt/objects与既有数据；禁止自动删库/清桶、旧版fallback、feature flag/默认禁用。审批前不start/产品修改/提交/归档，之后遵循父已批准的本地收尾授权。
