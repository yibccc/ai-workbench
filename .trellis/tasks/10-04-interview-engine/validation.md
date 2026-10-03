# Engine实现与真实验证

2026-10-04，Windows11/Java17、AgentScope2.0.3、Spring Boot3.5.16、MyBatis/Flyway、PG17.6/Redis均来自主明确建立的新project `wbinterview20261004`。实际库 `wbinterview_test` / schema `d9_interview_tests_20261004`，native端口 PG25432/Redis36379/RustFS29000；没有public/日常业务、真实模型key或云API。

## 实施合同

- V20新增session/question/answer/JD/job/evaluation/operation-receipt七表，所有owner/same-owner FK与0based轮次；V19及更早迁移未改。V20已经真实迁移，之后checksum固定。
- `InterviewController` methods与当前 `frontend/src/api/interview.ts` 的路径/字段一致，page用PageQueries及摘要，不发正文/答案；read/report/轮询没有dispatch。独立private cache filter在Security之前设置no-store/private/nosniff。
- 当前简历仅由短事务内mandatory snapshot copy owner+expectedVersion+正文/hash，不接client替代正文。JD202明确parse、方向/精确原文hash/owner/SUCCEEDED绑定、成功分析多场copy不consume；delete/cancel擦raw/result与job payload。
- 显式Integer/Long/Boolean/enum/text deserializer只作用新面试请求；拒fraction/string/numericenum/错误标量，不改变旧业务全局Jackson。题数省略默认5、合法3..20；缺frozen bool/enum拒绝。
- 题单完整strict2N、MAIN/FOLLOW_UP/parent索引；当前draft不推进，submit原子锁定且收据重放不重复推进；空SUBMITTED仍需评分，freeze未提交才UNANSWERED0。
- 分组结果固定question/answer/rubric/model hash；成功组不重跑。必要评分全有效后BigDecimal平均两位HALF_UP并持久保存，缺失/失败总分null，反馈由后端有效结果组织，无额外summary模型。
- 专用executor2/queue40，PENDING queue45m，worker实际claim才PROCESSING lease5m，SDKtimeout4m且maxAttempts1。启动+30秒扫描仅expired→FAILED，GET不恢复/不自动retry；terminal owner+token+inputhash+有效lease+未deleted，子写同事务。
- Terminal CAS使用 `clock_timestamp()` 而非事务起点CURRENT_TIMESTAMP，置于所有子写/主体状态写之后；最后失权抛EXECUTION_EXPIRED，整个事务rollback。queue/expired SQL同样用真实DBwall-clock。
- any-stage delete擦本场快照/题答/JD副本/evaluation/job payload并失权；原receipt最小hash/id保留，create重放DELETED防复活；current/他场不受影响。

## 实际命令与结果

忽略环境 `.local-runtime/ai-interview-test/test.env` 只加载应用测试keys到Process，过滤RUSTFS管理keys，不打印secret；DEEPSEEK_API_KEY置空。Java validator/子JVM去掉root/AWS/model变量。当前Windows提交内存已足够，使用bounded heap/CPU和已缓存Mockito显式premain agent，不依赖动态attach。

1. `mvn -o -DskipTests compile`：exit0 BUILD SUCCESS，187/后来188主源文件。首次实际Boot加载中发现XML生成的比较符 `<` 未转义，修为XML escaped SQL；well-formed检查及后续真实context/SQL通过。没有以compile替代事务验证。
2. 首次真实PG空schema升级 V19→V20：exit0，Successfully applied 1 migration；随后V1–V20 checksum validate PASS。
3. SDK/domain：

   ```powershell
   $env:MAVEN_OPTS='-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC'
   mvn -o '-DargLine=-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -javaagent:D:/APPS/apache-maven-3.9.9/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' '-Dtest=InterviewDomainTest,InterviewSdkIntegrationTest' test
   ```

   **exit0，10 tests/0 failures/errors**。Domain4覆盖四方向三难度所有N3..20、bad question shape、严格Unicode边界、emptySUBMITTED及无效评分；SDK6由真实AgentScope→loopback计数HTTP覆盖正常JSON、503、429、断流、200ms timeout、坏结构。一次action一次send，显式第二action才第二send；没有自动JSONrepair/外层或SDK/transport retry。timeout时SDK/Reactor会产生安全fixture中断日志，测试与真实发送计数PASS。

4. PG/HTTP首次9+2通过后，新增局部strict输入/真实JVM/queue/JDdelete验证，最终命令：

   ```powershell
   $env:MAVEN_OPTS='-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC'
   mvn -o '-DargLine=-Xms32m -Xmx384m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -javaagent:D:/APPS/apache-maven-3.9.9/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' '-Dtest=InterviewPersistenceIntegrationTest,InterviewHttpIntegrationTest' test
   ```

   **exit0 BUILD SUCCESS，14 tests/0 failures/errors，32.644s**；HTTP3 7.647s，PG11 14.29s。真实HTTP账户A/B/ADMIN/匿名/CSRF、缓存、JD202/取消、receipt/current字段、完整asynchronous worker流程、分页摘要与删除；fraction题数/turn/version、String数量/Boolean、numericenum及缺frozen字段均400且不推进。真实PG独立事务双submit、last-submit/complete竞争、草稿恢复、current A→B snapshot保A、JD exact raw/hash多场复用、成功评估组复用与失败null、queued/running expiry、delete与旧token、bad题单零子写。

5. PG11包含**实际子JVM强制终止已提交PROCESSING→新JVM启动恢复FAILED且计数0模型调用→手动retry才成功生成**；不是以进程内mock代替重启。最大组合20main/40turn、20k emoji resume、10k emoji JD、每答5k emoji全部完整持久读回，fixture分数80.125平均80.13；混合empty/unanswered平均13.35，failed分组重试后53.42。

6. 最终SQL复核发现txn-start时间/过早terminal check不足以覆盖长写，按既定fencing合同补强到DBwall-clock+最后CAS；新增真实PG trigger每条question insert `pg_sleep(0.075)`、候选lease300ms、共6条题。最终失权EXECUTION_EXPIRED，直接SQL question/answer count=0，session generation保持原PROCESSING，随后recover仅FAILED。最新同第4项实际命令**exit0 BUILD SUCCESS，15 tests/0 failures/errors，33.509s**（PG12 14.80s，HTTP3 7.850s）。没有修改已应用的V20。

本子任务已25项有意义针对测试分批PASS。报告在 `backend/target/surefire-reports/com.aiworkbench.interview.Interview*.txt`，shared target后续Maven会覆盖，需要主按实际最终gate记录，不把旧报告当新的PASS。

独立 check 补充：修复 mixed-group PENDING/FAILED 报告把冻结 UNANSWERED 错映射 null，新增2项真实PG精确回归 PASS（2/0/0，Maven58.970s）；另经主会话采用的最小信任边界修复将规则/冻结enum/count放 SystemMessage、完整原 JSON data 放 UserMessage，新增1项真实SDK三操作最大正文 wire 捕获。最新 SDK7（含原6复验）实际 exit0 BUILD SUCCESS，7/0/0，Surefire4.559s、Maven41.925s，compile189/testCompile54 PASS。首次 capture 断言因 JsonNode 内部整数类型而失败，改按完整DTO等值校验后复验通过。现engine共28项；这些新增与复验命令/证据详见 `check-report.md`，最后全量仍由主会话按最终源码与隔离env统一执行。API/hash/schema/V20保持不变，未真实付费调用。

## 交接和验收边界

| 目标 | 本代理证据 |
|---|---|
| INT-AC-001/002/003/004 | 真实私有HTTP/CSRF/cache和PG owner、strict2N/domain全参数、独立事务CAS、draft/empty提交/receipt |
| INT-AC-005/006/007 | freeze一次、分组score shape/failed null、BigDecimal两个小数、成功组复用，无extra模型summary |
| INT-AC-009/014/018/019/020/021/022 | 服务端固定resume/JD与strictowner/hash、可重复copy；delete擦本场payload且旧token/create重放无法复活 |
| INT-AC-010/016/023/024/025 | 自有prompt+JSON数据界限/无工具，真实SDK发送计数；Unicode/最大组合fixture、queued/lease/restart/manual-only |
| INT-AC-026 | 多场UUID/config/snapshot独立、删除不动其他，真实重登/浏览器多场留父E2E |

这些是工程验证，fixture不能证明真实付费模型质量/成本/延迟。浏览器/代理/联合恢复、独立engine check、全量Maven与既有五区/社区回归、spec、本地commit/归档由主会话接续；本代理不提交/归档/触碰日常资源。
