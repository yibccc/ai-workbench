# 当前简历与私有原件技术设计

## 权威合同与真实基线

以父 `../10-04-ai-interview/design.md` 为准，本文落实 resume 子任务边界；父 PRD 的 FR/INT-AC 语义不变。已核实的真实符号和行号见父 `research/backend-contracts.md`、`storage-baseline.md`。这是规划，以下新类/表/脚本不是既有实现；不执行产品修改、迁移、IAM或验证。

直接复用 `storage/ObjectStorage.java` 的 put/open/delete、`RustFsObjectStorage`、`AttachmentValidator.stage`/`ValidatedAttachment`、UUID handler、CurrentUser、Session/CSRF、ProblemDetail 与 owner SQL。社区 `AttachmentServiceImpl`/`AttachmentPersistenceService` 仅提供短事务和耐久token模式，不调用其post锁、配额、表、API或清理入口。

## 1. 分层与修改边界

按现有目录新增 ResumeController、ResumeService 接口、ResumeServiceImpl、独立 ResumePersistenceService、ResumeMapper/XML、dto.resume、entity.resume 与所需typed异常；Controller不含SQL，事务在独立Spring持久bean避免self-invocation。HTTP缓存匹配明确加入resume路径，不能假定 CommunityCacheControlFilter 已覆盖。

`AttachmentValidationWorker.markdown(Path)` 目前private，提取storage内小型共享Markdown文本检查helper，原worker继续调用。严格decoder `REPORT`、NUL/ISO控制字符拒绝、CR/LF/TAB允许，JSON输入不成对surrogate拒绝；不建立第二套规则或改变既有附件格式。新helper提供解码/检查，20k业务上限仍归resume，不把社区1MiB MD降成20k。

操作脚本仅增加本需求的明确IAM更新、对象bundle与隔离恢复；不建通用备份平台或重构全部运行脚本。

## 2. PostgreSQL模型与不变量

实施前重查Flyway编号，预定新增V19或实际下一版，不修改V1–V18。UUID、TIMESTAMPTZ、lower_snake_case、owner非空、同owner复合FK和CHECK按真实规范。

| 正式表 | 字段与不变量 |
|---|---|
| user_resumes | user_id PK；version单调非负；markdown_text nullable、source_kind nullable/PASTE/MD_FILE、current_object_id nullable、content_sha256、updated_at。null正文且无source/pointer表示无current；空字符串是已保存正文，exists=true。MD_FILE需同owner READY对象，PASTE无对象。 |
| resume_objects | id/user_id唯一、immutable storage_key、verified content_type、original_filename、actual byte_size、原bytes sha256；status=UPLOADING/READY/FAILED/DELETING/DELETE_FAILED/DELETED、operation token/lease、safe_failure_code与时间。当前引用/活动预约禁止delete；清理后只留必要不可读key/状态/token/原receipt联系，不保留旧正文/可读历史文件名。 |
| resume_write_receipts | owner+operation+request_id唯一；payload_hash、状态、result_version、最小结果和候选objectId。无正文/文件bytes；同key不同payload409。原上传结果不可被清理token/状态改写成另一操作成功。 |

key固定为 `interview/resumes/<userId>/<objectId>.md`，全部UUID由服务端产生，不能含用户filename、provider、bucket、endpoint或签名；每候选新key只put一次、不覆盖。user_resumes当前指针与resume_objects用同owner复合FK；version删除后继续增长，不能删singleton行重建到0。

GET只读：尚无singleton行时返回exists=false/version=0，不由读取创建数据库行；首写事务安全创建无正文最小槽位，并发首写由PK+owner锁保护。已有槽位删除不删行，无current GET稳定返回单调version。engine在同owner短事务锁定current并校验expectedResumeVersion后复制正文/version/hash；历史仅复制文本，不持原件引用。

锁顺序统一 owner current → object（多对象按稳定ID排序）→ receipt；创建面试读取resume时沿相同current锁边界与engine共享明确服务合同，避免各自无锁读。所有mapper路径带owner，不通过ADMIN扩权。

## 3. 输入和HTTP合同

| 方法/路径 | 输入与响应行为 |
|---|---|
| GET /api/me/resume | exists/version/markdownText/sourceKind/originalFile安全metadata；exists=false仍带version。无key/云URL。纯读、不触发模型或原件put。 |
| PUT /api/me/resume | 必须 `{mode:EDIT_CURRENT|PASTE,markdownText,expectedVersion,requestId}`。缺/未知mode=400，不加默认兼容模式。EDIT_CURRENT绑定服务端current版本，保留source_kind/current_object_id；无current不能EDIT_CURRENT。PASTE显式替换为无原件文本，解除旧原件current引用。 |
| POST /api/me/resume/import | multipart恰好file、markdownText、expectedVersion、requestId；file为原File，markdownText为编辑后最终正文；新file换源。原decoded与最终正文分别校验，不要求字节相等。 |
| GET /api/me/resume/original | 仅owner current MD_FILE READY对象原字节下载；无current/无原件404。HEAD同样先鉴权。 |
| DELETE /api/me/resume | expectedVersion/requestId必传；清正文/source/pointer/hash并version+1；最小receipt确认结果，历史snapshot不动。 |

PUT/import成功返回最小已确认版本/当前metadata，GET为正文权威。重放receipt不能授予旧原件重新读取或使旧正文成为当前；UI读取真实current恢复。删除的参数按typed client采用一致明确编码，不能accept省略版本的旧式路径。

身份/CSRF在读取multipart前成立，controller使用lazy请求并先requireId，再读取parts；不借MultipartFile绑定声称owner鉴权先于解析。`.md`扩展白名单独立执行，不信MIME/Content-Length；stage原bytes≤1,048,576、SHA和strictUTF8；try-with-resources保证临时文件清理。解码只剥首BOM；不trim、不改换行，正文hash对精确UTF8。原件和最终正文各≤20,000码点，空MD与空正文有效，不添加最低长度。

1MiB byte gate等号在stage层验证；完整1MiB UTF8正文会因20k text gate拒绝，完整导入必须同时满足两个条件。前端Array.from(text).length/后端codePointCount与PG CHAR_LENGTH一致；非法surrogate拒绝而非编码替换。

所有响应与错误no-store, private。owner验证后才open/发filename、length等metadata；流式有界同步copy并close/abort。下载安全UTF8filename、Content-Disposition attachment、nosniff、Accept-Ranges:none，授权Range/conditional按现有原件合同返回完整200，不304/206，不sign/redirect。

401/403沿现有Security；格式/文本/缺字段400；真实bytes+1为413；版本/不同payload/无current EDIT_CURRENT为409；未知资源安全404。expectedVersion/currentVersion仅对owner暴露；IO失败用安全resume错误，不错当未配置，不泄漏SQL、凭据/endpoint或上游body。

## 4. 收据、预约与原子swap

canonical payload hash覆盖operation/mode/sourceKind、expectedVersion、精确最终正文hash；import还包括原file SHA/bytes。receipt检查先于过期版本：同owner/operation/requestId同hash返回原最小结果，不再put/version；不同hash409。正在UPLOADING的重放只返回持久状态，不派第二次put。失败后用户重新尝试导入用新requestId/objectId/key；不覆写原failed receipt。

PASTE/EDIT_CURRENT：shared text校验后短事务锁singleton、查receipt、CAS expectedVersion。EDIT_CURRENT保服务端source/pointer；PASTE切source并解除旧pointer。正文/version/hash+receipt同事务提交。任何失败不替换旧current。

import：先stage/解码/最终正文校验；短事务锁owner current并检查receipt/version，reserve immutable object/token/lease和receipt，旧current不变；事务外put；短事务匹配owner+UPLOADING+token+有效lease+expectedVersion，再原子置READY与current新正文/source/pointer/version/receipt。storage网络调用不持DB事务。确认事务回滚/CAS失败/put失败不能报告成功，新key进入耐久失败/待清理，旧current继续可用。

reservation使用现有storage实际connect/read/attempt/total deadline配置，必须reservation>总IO期限；有效token/lease持久化。恢复处理expired预约，不盲信仅进程内Future超时。迟到成功/失败提交全部检查token/version/lease，零行表示失权；不得UPSERT恢复current。

## 5. 清理与不确定put

替换、显式PASTE、删除只在事务提交后使旧对象失去current引用，业务读取立即关闭。清理短事务再次核验所有有效引用、预约和活动/未知完成状态，再claim DELETING/token；事务外delete，短事务同token写DELETED或DELETE_FAILED。失败不可读、不误报物理删除。

UPLOADING/有效预约、有current引用、仍运行的相关I/O或不能证明已结束的写入不得清理。过期/崩溃预约先撤销绑定权并记录FAILED/不确定写入；保存immutable key和tombstone，等相关处理退出及安全IO边界后执行补偿。已失权put若迟到完成只负责按原key再清理，不得finalize；若进程已丢失结果，持久恢复仍保key以复核/重复删除可能迟到写入。第一次delete成功不销毁唯一key/tombstone，避免迟到put留下无法定位的对象。必要后台补偿仅处理用户已授权替换/删除/失败产生的孤儿，不按天删除current或面试。

存储清理重试不是模型retry；每次结果分别记录，失败仍失败。恢复扫描持续检查过期预约/待清理，不能仅靠启动一次检查，使未过期中断预约永久活跃。实施须以同步点和实际JVM退出验证这一协议，不能只以mock最终状态宣称通过。

## 6. IAM、运输与未来云

RustFS私有bucket/app identity仅Put/Get/Delete，canonical资源并集为原 `community/attachments/*` + 新 `interview/resumes/*`，保Deny admin:*；不授全桶/ListBucket/ACL/管理，不旋转身份密码、不加anonymous/CORS浏览器直传。

initializer当前policy shape严格匹配。本需求增加显式canonical policy维护动作：输入明确local endpoint/bucket/app identity、核验原已知community最小policy或目标union，未知权限冲突fail closed；用明确管理身份更新所指named policy与该identity绑定，输出probe两prefix。原shape只是这次明确资源更新的前置核验，不保留常驻旧policy fallback。隔离真桶验证新旧prefix/外prefix/admin/匿名负例后才认定通过；不静默自动扩权。

`deploy/nginx.conf`仅 `/api/me/resume/import`设client_max_body_size 2m，保既有社区22m和其他API1m；multipart正文+file overhead必须实际经代理测试。Servlet当前21MiB/22MiB无需扩大，业务上限仍精确。

所有业务只用ObjectStorage逻辑key和bytes/size/SHA。阿里云adapter/config/IAM/资源/同key对象迁移与真实验收延期；未来核验length/SHA/DB引用全部就绪后一次配置切换，不双写/双读/自动迁移/供应商旧版回退。当前StorageProperties只接受rustfs/pathStyle=true，不能声称仅改endpoint已支持OSS；云账号未申请不禁用本地功能。

## 7. PG与对象联合备份/隔离恢复

现有backup.ps1只PG且固定source容器。新增明确source/target参数与小型对象bundle helper，或同等本需求专用工具；拟新增接口须在实现后按真实--help补到implement/validation，不把未存在命令当既有可执行入口。

1. bundle存忽略目录 `.local-backups/ai-interview/<run>/`，包含PG custom dump、dump SHA、Flyway版本、对象manifest及SHA、起止时间/完整性标志；无凭据或签名URL。
2. 暂停已核验的源app writers和cleanup，保PG/RustFS数据服务与卷。native按owned-process校验，Compose只stop所指backend；不down -v、不清表。确认无其他writer后协调快照。
3. 用同一dump在确认不存在的新 `d10_restore_*` DB读对象清单，不从变化live DB扫描。manifest包含所有current MD_FILE READY原件及社区仍保留有效引用的对象，精确key/type/size/SHA/引用；失败/删除tombstone不是必需可读原件。
4. 应用GetObject权限按DB精确key导出原bytes，不要求ListBucket；bundle filename采用keyhash映射防路径遍历。必要对象缺失/不可读/size或SHA不符均不标complete。状态ledger仍随PG恢复，但optional-present孤儿不得冒充必须业务原件。
5. 恢复拒绝既有DB和bucket，使用全新隔离project/端口/卷/私有桶/synthetic app identity；管理身份只创建明确目标，app同keyPUT并GET核SHA；新app指向新DB+桶，model key空。
6. 分别记录PG恢复与RustFS恢复，核current正文/version/原件下载、历史snapshot和community引用/Flyway。断流、错误SHA、missing必要对象、existing target、恢复中断不误报complete；源数据不覆盖。在线删除不等于历史dump/bundle同步删除。

## 8. 验证、风险和回退

真实验证入口和RES-AC见implement/prd；PG schema为全新 `d9_interview_tests_20261004`，必须同时符合TEST_DATABASE_URL.currentSchema与WORKBENCH_TEST_SCHEMA，不用public/日常卷。真实RustFS与NGINX验证不能用mock取代；合成环境无付费模型key。

核心风险分别以迟到put/JVM重启、原versionCAS、两个prefix真实负权限、完整proxy请求、协调bundle与新目标恢复证据关闭。引用/临时文件/原件敏感信息不进入日志/Git。未关闭项如实回流父issues。

只新增schema与必要代码/最小policy；非破坏回退保留新表、原件、最小receipt/tombstone与新prefix权限，不删源数据/不自动迁移/不新增兼容路径。清表、清桶、日常恢复、云操作和发布不由规划授权。审批前不start/implement；审批后按本任务implement→独立check/修复→接口交接engine。
