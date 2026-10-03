# 面试引擎独立审查

2026-10-04，`trellis-check` 直接审查，未再派发子代理。已读取完整原生注入文件、子任务 PRD/design/implement、父正式设计与 actual DTO 合同、实施验证记录及真实测试源码。范围为 backend interview DTO/controller/service/persistence/mapper/AI/jobs/config/V20 和对应测试；未改 resume、前端、运维或已应用迁移。

## Findings (fixed)

- File: `backend/src/main/java/com/aiworkbench/service/impl/InterviewPersistenceService.java:151`
- Issue: 报告只从 SUCCEEDED 评估组读取逐轮结果。一组含 SUBMITTED 和 UNANSWERED 时，该组 PENDING/FAILED 会把已经冻结的未作答轮错误返回为 `NOT_EVALUATED/score=null`。这违反 FR-017 和 INT-AC-005/007 对未作答 0 分与系统失败的区分。
- Fix: 报告在同一 REPEATABLE_READ 只读事务内先读取冻结答案，为 UNANSWERED 轮固定填 0/“未作答”；有效组结果再补充已评分轮。已提交而尚未有效评估的轮次仍为 null；未完成必要评分时整场总分仍为 null，不重新计算总分或调用模型。
- Regression: `InterviewPersistenceIntegrationTest.unansweredTurnInMixedGroupRemainsZeroWhileSubmittedTurnWaitsFailsAndRetries` 覆盖 PENDING → FAILED → 显式 retry 成功，未答轮始终 0，已答失败轮 null，重复报告读取不新增模型调用，成功后后端固定平均 13.35。

- File: `backend/src/test/java/com/aiworkbench/interview/InterviewPersistenceIntegrationTest.java:97`
- Issue: 原有实际测试未直接证明同 operation/requestId 不同 payload 的冲突，以及同 requestId 的 owner 作用域；这些是收据硬合同。
- Fix: 补充 `operationReceiptsBindCanonicalPayloadAndOwnerAndCreateReplaySurvivesJdRemoval`，验证不同配置/答案 409 且原锁定答案不变、不同 owner 创建独立场、JD 删除后成功 create 收据仍优先返回、complete 丢 ACK 重放不新增评估任务/调用。没有改变产品接口或收据语义。

- File: `backend/src/main/java/com/aiworkbench/ai/AgentScopeInterviewAiGateway.java`
- Issue: 服务端可信指令/rubric 与不可信资料原先拼接在唯一 UserMessage 内，没有设计第6节要求的系统消息信任边界。原 JSON 标签已区分资料，不能据此声称消息角色已隔离；此发现不是已证明某次真实模型注入成功。
- Fix: SystemMessage 只保存服务端规则及冻结 direction/difficulty/count，UserMessage 保存 `untrusted_input_data=` 与完整原 DTO JSON。输入 DTO/hash、题单/评分合同、空 tools、SDK 单 attempt 均保持原合同；无额外抽象或 repair/fallback。
- Regression: SDK 新增单项 `trustedSystemRulesAndCompletePrivateDataStayInSeparateWireMessagesForAllOperations`，通过真实 AgentScope → loopback HTTP 捕获 JD/出题/评估三次授权动作。实际 wire roles 恰为 system/user，system 不含私有正文 marker，完整 user JSON 按输入 DTO 反序列化后逐值相同，tools 为空，每动作 counter 只增1。包含20主问/40轮生成响应、20k非BMP resume、10k非BMP JD与组内两答各5k码点；伪 SYSTEM 指令与链接仍位于 user JSON 内。这是消息结构和传输合同证据，不是付费模型安全/质量证明。

## Findings (not fixed)

没有发现需要修改公共接口、模块边界或新增数据库版本的遗留代码缺陷。尚未完成的整合验证属于父任务门禁：全量 Maven、浏览器多场/重登、代理、联合恢复与既有模块回归。真实付费模型质量、费用、最大供应商上下文和延迟仍为明确延期，fixture 与源码审查不能证明这些结果。

`.trellis/spec/backend/` 当前没有面试领域规范；已向主会话交接新增 `private-interviews.md` 与 index 入口的建议，由主会话统一维护，避免与其他审查并行冲突。应记录 MANDATORY current 快照、收据重放、冻结 UNANSWERED 独立于评估组、DB wall-clock 最终 CAS、SDK 单 attempt 与仅手动恢复。

## Verification

- Lint: PASS。backend POM 没有独立 Java lint 插件；已执行 `git diff --check`，并单独检查 25 个未跟踪 interview 源码/测试/资源文件的尾部空白；`InterviewMapper.xml` 和 POM XML 解析通过。
- TypeCheck: PASS，信任边界变更后的实际 Maven 重新 compile 189 主源文件、testCompile 54 测试源文件；父最终 `clean verify` 负责完整重新编译与全量回归。
- Tests: PASS，此次新增两项真实 PG 精确方法 exit0、BUILD SUCCESS，2 tests / 0 failures / 0 errors / 0 skipped，Surefire 23.82s、Maven 58.970s。随后信任边界 SDK7 复验 exit0、BUILD SUCCESS，7 / 0 failures / 0 errors / 0 skipped，Surefire 4.559s、Maven 41.925s。原实施 Domain4/SDK6/PG12/HTTP3 合计25项是原实施证据，不当成此次重新运行；SDK7 中包含原6项复验和新增1项。引擎范围现有28项有意义用例，由父最后全量运行统一记录。
- 已批准隔离源 `wbinterview20261004` 的 PostgreSQL/Redis/RustFS 均 `running/healthy`；只使用 `wbinterview_test/d9_interview_tests_20261004`，空真实模型 key、loopback SDK fixture，无日常配置/库/桶或真实付费调用。
- V20 保持未改，SHA-256 `a2539b2ed870ac2469fd922bc5c8a9c96301ffe394bdac80a5ffb1797e3a29c7`。

实际命令（从 workspace 根运行，不打印 env 文件内容；Maven 缓存/settings 为只读，本地 source 服务未停止）：

```powershell
wsl.exe --exec docker run --rm --network wbinterview20261004_default --env-file /mnt/e/projects/workbench/.local-runtime/ai-interview-test/maven.env -e 'TEST_DATABASE_URL=jdbc:postgresql://postgres:5432/wbinterview_test?currentSchema=d9_interview_tests_20261004' -e REDIS_HOST=redis -e REDIS_PORT=6379 -e WORKBENCH_STORAGE_ENDPOINT=http://rustfs:9000 -e DEEPSEEK_API_KEY= -e 'MAVEN_OPTS=-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC' -v /mnt/e/projects/workbench:/workspace -v /mnt/d/APPS/apache-maven-3.9.9/repository:/root/.m2/repository:ro -v /mnt/d/APPS/apache-maven-3.9.9/conf/settings.xml:/tmp/maven-existing-settings.xml:ro -w /workspace/backend maven:3.9.9-eclipse-temurin-17 mvn -o -s /tmp/maven-existing-settings.xml '-Dmaven.repo.local=/root/.m2/repository' '-DargLine=-Xms32m -Xmx384m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -javaagent:/root/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' '-Dtest=InterviewPersistenceIntegrationTest#unansweredTurnInMixedGroupRemainsZeroWhileSubmittedTurnWaitsFailsAndRetries+operationReceiptsBindCanonicalPayloadAndOwnerAndCreateReplaySurvivesJdRemoval' test
```

Flyway 在该命令中实际 validate 21 migrations，schema 当前版本20且无需新迁移。针对报告 `backend/target/surefire-reports/com.aiworkbench.interview.InterviewPersistenceIntegrationTest.txt` 此时计数2；父后续全量会覆盖共享 target，不能据旧报告伪造新运行。Maven 窗口已归还主会话。

信任边界变更后实际 SDK7 命令（纯 JUnit/loopback fixture，不加载 DB/bootstrap/对象管理或真实模型 env）：

```powershell
wsl.exe --exec docker run --rm --network wbinterview20261004_default -e DEEPSEEK_API_KEY= -e 'MAVEN_OPTS=-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC' -v /mnt/e/projects/workbench:/workspace -v /mnt/d/APPS/apache-maven-3.9.9/repository:/root/.m2/repository:ro -v /mnt/d/APPS/apache-maven-3.9.9/conf/settings.xml:/tmp/maven-existing-settings.xml:ro -w /workspace/backend maven:3.9.9-eclipse-temurin-17 mvn -o -s /tmp/maven-existing-settings.xml '-Dmaven.repo.local=/root/.m2/repository' '-DargLine=-Xms32m -Xmx384m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -javaagent:/root/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' '-Dtest=InterviewSdkIntegrationTest' test
```

首次新捕获测试失败是比较 JsonNode 的整数内部节点类型（expected LongNode / actual IntNode，同值1），原6项已通过；修为完整 DTO 值比较并避免输出大正文后，上述同命令 SDK7 全部通过。timeout fixture 仍有既有 SDK/Reactor 中断日志，但发送次数和全部断言通过。窗口再次释放，父最后 clean verify 必须覆盖最终源码并重新打包最新 gateway。

## 审查路径证据

- 所有 HTTP owner 来自 `CurrentUser.requireId()`，后台使用 JobRow 持久 owner；mapper resource 查询含 owner/id，same-owner FK 约束七表关联。过期全局扫描只是内部发现工作，后续恢复重新锁 owner/主体/job，没有 ADMIN 业务绕过。
- create 收据检查早于 current snapshot 和 JD 状态检查；resume 用独立 Spring bean 的 MANDATORY 快照事务；成功 JD 按 owner/direction/精确 raw hash 绑定，create copy 多场而不消费。
- 完整题单通过 `InterviewOutput.questions` 后同事务写全 2N；当前答案只改 DRAFT，submit/freeze/receipt 同事务，空 SUBMITTED 仍进入必要评分，提前交卷清草稿并冻结 UNANSWERED。
- 专用 executor2/queue40，只有初次用户写与显式 retry 调度；worker 领取时建立 PROCESSING lease。启动/30秒扫描只将过期作业 FAILED，没有重调度；所有 GET 为只读，不调用 SessionActivity。
- 最终成功/失败 CAS 在所有子写后执行，并绑定 owner/token/input hash/PROCESSING/`lease_expires_at > clock_timestamp()`；失权抛异常整事务回滚。delete 撤 token/清 job payload 与本场敏感子行/快照，原 create receipt 返回 DELETED，不能复活。
- 真实 SDK 使用 `ExecutionConfig.maxAttempts(1)`，无工具、repair 或 fallback；system 服务端规则/user 完整 JSON 资料通过真实 wire 捕获验证，严格 JSON 与模型输出结构检查。分组成功结果按固定 hash 复用，所有必要逐轮评分有效后才持久化 BigDecimal 两位 HALF_UP 总分，GET 不重新平均或额外汇总调用。
