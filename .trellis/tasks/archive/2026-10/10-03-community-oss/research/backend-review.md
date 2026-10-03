# Research: 正式发布与存储规划后端静态审阅

- Query: 审阅父 PRD/design/implement 与 publishing/storage 子规划，检验当前预览 payload 发布、aggregate version、上传 pending 配额、下架、幂等和快照边界。
- Scope: internal；只审阅规划与已核实代码合同，唯一写入本文件，不修改产品或其他规划，不 start、不执行迁移/测试。
- Date: 2026-10-03

## Findings

### 结论

采用父设计作为唯一合同，表名/路径/类型名与前一份 backend.md 的候选不同不构成问题。单一 posts aggregate version、上传 reserve/release 同稿锁并更新 version、前端单稿写队列是可行的简化方案；**没有理由为了采用研究候选而强制换成独立附件版本**。完整当前预览 payload 的一次发布事务也比先隐式保存再发布更贴合 R2。

本次最后复读时，父 `design.md:43,63,65,82,86` 已修复 `user_accounts` FK、publish完整payload/fingerprint与 ADMIN expectedRevisionId；这些不再是阻塞项。仍有以下四项需要在正式合同补一句/字段后才能认为后端规划完整。它们不新增产品功能，属于兑现已批准 AC 的必要技术边界；建议主会话直接修订，不询问用户常规实现细节。

### B-01 · blocking：reserve/put/finalize 之间缺持久化状态 fencing

- 位置：父 `design.md:108-110`，storage `design.md:11`、`implement.md:8`。
- 触发：A tab reserve pending（version+1）后执行外部 put；B tab读取当前version后保存/发布另一集合，或者显式清理；A 的晚到成功再 finalize。若finalize无条件READY/重新绑定，可能复活已移除 pending，覆盖新draft集合，或在对象刚被cleanup删除后产生READY坏引用。单post行锁只序列化两个短事务，**无法自动阻止这段外部put后的过期操作**。
- 最小修订：固定附件状态 `UPLOADING|READY|FAILED|DELETING|DELETE_FAILED|DELETED`（如另设VALIDATING也可，但应确定）；持久上传token/attempt标识，finalize以 `state=UPLOADING AND token匹配 AND 未释放pending reservation` 条件更新。save/publish/remove若移除pending，须在同post锁下将其失败/取消并释放额度；晚到finalize只记录孤立对象，不重绑draft、不修改正文、不回READY。或者明确禁止其它写替换含active pending集合并返回409，直到上传终态；二者选其一即可，不能依赖UI串行作为唯一保证。
- `DELETING/DELETE_FAILED/DELETED` 不可绑定/发布/READY；cleanup必须排除仍有效UPLOADING reservation，即使某draft ref已被移除。所有路径遵循post→attachment锁顺序。
- READY确认为内部上传续作，不要求旧 reserve aggregate version 必须仍等于post.version；应检查token/state及引用资格。若业务选择因跨tab版本变化拒绝，也必须释放旧reservation并保留可清理key，不能悬空。
- 验证：真实并发“reserve→延迟put→另一tab remove/publish/cleanup→释放put”后既无复活引用也无坏READY，正文及历史不变；storage `implement.md:6` 增加这个确定同步点。

### B-02 · blocking：进程崩溃的 pending 永久占额、同 requestId 失败再尝试的旧 key 去向未定

- 位置：父 `design.md:53,108`，storage `prd.md:16`；storage研究已有 `research/storage.md:138-140` 指出durable key/attempt和不确定结果恢复，但正式合同尚未落地。
- 触发：reserve提交后JVM崩溃，或put完成但finalize数据库不可用；没有failure回调可删除pending/释放quota。十次这类中断可永久耗尽稿件数量上限。另一风险是同attachment row失败重试换object_key，覆盖唯一旧key记录后把原实际对象遗失，显式cleanup无法再识别。
- 最小修订（简单确定方案）：attachments 增 `upload_token,reservation_expires_at,updated_at`，每次上传采用有界storage总调用时间对应的有限reservation期限。作者下一次读取状态/重试/显式cleanup可在post锁下核对逾期UPLOADING，不确定对象按固定key/size/sha核实；匹配且资格仍有效才READY，否则标FAILED、删pending、version+1释放额度并留下旧key补偿记录。**这是上传操作超时回收，不是产品禁止的“固定天数自动删除对象”。** 实际delete仍只对无任何有效引用的孤立对象。
- 用户重试可用**新requestId+new attachment ID+new key**：原FAILED请求ID重放返回原失败，不再次reserve；旧FAILED行保留其key供cleanup。这样无需新attempt表，成功同requestId重放仍返回同READY附件，异sha/size/规范化文件类型/文件名409。若坚持同requestId多attempt，则须另有持久attempt/key记录与token fencing，不能只更新一个key。
- 固定成功/失败/冲突响应的aggregate version契约：release确实变draft refs时version+1；上传失败ProblemDetail可有 `currentVersion`，前端既保留输入又更新写队列token；旧响应不能把较新的版本倒退。
- 验证：reserve提交后直接停止进程、put已成功但finalize失败、响应丢失重放、失败新ID重试；恢复后数量/字节只占一次或归零，无永久reservation/失踪key。

### B-03 · blocking：可编辑 businessDate 尚未属于 immutable published snapshot

- 位置：父 `design.md:43-45,63,65,80-83`：business_date只在posts；draft/revision表只列title/body，publish请求又携带businessDate。
- 触发：已发布DAILY改日期并只保存draft；reader若仍从posts.business_date读取，将先于明确publish改变公开日期。历史revision同样不能保持当次日期。AC-04的快照覆盖公开展示字段，不只是body。
- 最小修订：business_date存入draft和revision，与title/body同事务冻结；reader和卡片日期从current revision读取。posts.type可保持不可变；posts.business_date若保留，明确为创建日/不作为可变公开日期权威。若规定业务日永不变，则所有save/publish businessDate都须匹配原日期并明确拒绝变化；优先完整快照方案，符合当前payload可编辑结构。
- 同样检查R2博客的summary：原型 `assets/prototype-r2/src/app.js:89-90` 有摘要input，feed/detail使用该值。父schema/API暂无summary。若正式保留该R2控件，应一并加入draft/revision与publish完整fingerprint，不能输入后不保存或从private draft直接给reader。摘要遗漏是当前布局/数据保存合同缺口，无需新增页面。
- 验证：改title/body/date/summary并只save，reader全部旧公开字段与散列不变；publish后一次切换，历史仍保留旧值。

### B-04 · blocking：发布重放 resultVersion 需保存当次值

- 位置：父 `design.md:45,63,82`。revision记request_id/fingerprint，返回发布revision/result version，但schema未列提交当时aggregate version。
- 触发：发布成功resultVersion=5后其它tab保存到version=8，再重放原发布。若API返回当前posts.version=8但旧revision/input，客户端可能以新token继续保存旧输入，绕开“旧写必须409”；从revision_no也不能推出aggregate version（save/reserve/release均可能递增）。
- 最小修订：immutable revision增 `result_version`（或原命令收据存该值）；同requestId/fingerprint重放返回原 revisionId/revisionNo/publishedAt/resultVersion，不返回当前draft或当前version冒充当次结果。当前status/版本另由作者GET查询；重放不能应用状态，下架/撤回以后仍不恢复。
- 规范化fingerprint包括完整accepted payload、expectedVersion、visibility、业务日、摘要及去重后顺序确定的attachmentIds；不能根据后来draft/references重算。集合展示顺序若重要，preserve首出现顺序；否则固定排序，但正式明确一种。
- 验证：响应丢失后成功重放、重放前另一tab已保存/发布、重放前已下架、同key异payload；receipt值固定，数据库revision只有一次，客户端晚到结果不覆盖新输入。

### 非 blocking / 已接受

- 当前预览payload一次提交：父 `design.md:63` 与UI `design.md:9` 一致；校验全部成功才改draft+immutable revision/current pointer，失败输入保留。无需先save，也无需采用backend.md原候选的save成功收据表。
- save/reopen/withdraw是否另加requestId幂等不是AC-06强制：旧version明确409且失败输入保留可满足要求；不要求为此增加命令系统。
- aggregate version+reserve bump：用户跨tab可能409，属于已明确并发合同；只要reserve/READY/release响应和晚到version处理固定，不是缺陷。
- ADMIN expectedRevisionId是正确合同，不读取私有version；父 `design.md:65` 已要求当前可见revision。非PUBLISHED/不存在同安全404，不需给ADMIN读私有draft；同post锁能阻止hide/publish竞态恢复。
- HIDDEN允许私有文本维护但禁止publish，不违背已批准“不能绕过下架再发布”；没有恢复API。
- 当前reader统一PUBLISHED+current pointer、作者独立profile、firstPublishedAt只首次、同日多篇、source fields/owner/day和raw/report保留都符合AC。来源追踪不得FK限制手工record删除；作者审计结构可JSONB私有冻结，读者mapper不得输出。
- RustFS后续Aliyun抽象边界和延后实测，不是自动迁移承诺；本次无需云API、双写/双读fallback。
- no-store、HEAD先auth、忽略Range完整200、async stream只用已授权不可变descriptor与无OSS重定向都与AC一致。read授权已开始之后发生withdraw可继续流，AC允许。
- 子任务结构publishing→storage→UI，storage明确修改原publishing同一事务，不建副本，合理；text-only阶段不声称父全部AC通过。

### 最小规划修订清单

1. 父design表字段补draft/revision业务日与summary、revision.result_version、附件state/token/deadline/updatedAt；仍V17/V18所有权，不需要另加研究候选所有表。
2. 父upload段明确release/finalize条件、pending移除行为、FAILED新ID重试和逾期不确定结果恢复；success/failure version响应写清。
3. publishing implement加metadata save不公开与fixed resultVersion重放场景；storage implement加延迟finalize/remove/cleanup竞争与进程崩溃quota恢复场景。
4. 父API create/save/publish字段统一 `bodyMarkdown`（当前create/save仍写body缩写）；三子文档引用父最新合同即可，不复制同义合同造成漂移。

### Files found / code patterns / related specs

- 已审阅 `.trellis/tasks/10-03-community-oss/{prd,design,implement}.md`、`10-03-community-publishing/{prd,design,implement}.md`、`10-03-community-storage/{prd,design,implement}.md`，另核对UI设计的preview/write队列合同与storage研究的崩溃/幂等证据。
- 真实schema账号名：`backend/src/main/resources/db/migration/V13__accounts.sql:1` 为user_accounts，父已修正。
- 源手工record物理删除：`backend/src/main/resources/mapper/WorkRecordMapper.xml:88`；新revision/source audit不应阻塞原record删除。
- 既有 conditional version + 全事务示例：`backend/src/main/java/com/aiworkbench/service/impl/TaskServiceImpl.java:142-160`；同日投影与raw/report分离：`WorkRecordMapper.xml:43-81`、`DailyRecordPresentationIntegrationTest.java:61-104,142-185`。本审阅未重新遍历已核实代码。
- 相关规范：backend `database-guidelines.md`、`identity-isolation.md`、`focus-routines.md`、`pagination.md`、`error-handling.md`、`directory-structure.md`；前一研究已读，审阅没有重新加载implement/check manifests。
- External references：无新增外部检索；本次全部为本地合同与竞态推导，未宣称实际运行已复现。

## Caveats / Not Found

- 文件由主会话同时收敛，本报告行号对应本次复读快照；主会话收到B项后若已修订，应将其标resolved，而不是重复要求用户确认。
- 未发现需旧行为兼容、恢复入口、默认禁用门禁或扩大产品范围的理由；本报告所有修订均是既定AC内实现合同。
- 所有工程测试仍NOT_RUN；规划通过不代表DB/HTTP/OSS/缓存/并发或配额实际通过。
