# AI 面试：当前简历与私有原件

## 目标与任务边界

为账号维护唯一当前 Markdown 简历，让文件导入、编辑、粘贴替换、并发保存、删除与响应丢失都保持确定的生效结果。原件直接复用本仓库的 ObjectStorage、RustFS、AttachmentValidator；面试创建只复制服务端当前正文与版本，不把简历挂在社区 post 上。

父合同为 `../10-04-ai-interview/{prd,design,implement}.md`，源包为父任务 `research/handoff/r1/`。本任务是先行的后端/存储独立交付；engine 在本任务当前简历读取与版本合同验收后启动，profile UI 在 ui 子任务实现。本文件继承下列父 FR、INT-AC 的完整语义；仅分配验证职责，不删减父要求。

状态为 planning。创建本任务和规划不代表产品实施批准；唯一待批准产品选择 P-01 留在父最终规划确认，本任务不伪造批准。

## 正式需求

- **FR-001**：每个用户至多一份生效的当前简历，绑定稳定 userId；不提供多简历选择、版本库或回收站。
- **FR-002**：个人中心支持 UTF-8 `.md` 导入和 Markdown 粘贴/编辑。只有保存成功才替换；校验、存储或确认失败时旧当前简历继续生效，前端编辑内容由 ui 保留。
- **FR-003**：导入通过现有 RustFS/ObjectStorage 保存私有原字节；粘贴不制造文件对象，简历不依附社区 post。已有 current 文本编辑保留原件来源；显式粘贴替换解除原件引用；编辑后保存文本可与原文件不同，二者摘要与用途分开。
- **FR-004**：替换成功只有新正文生效。删除当前简历仍允许无简历面试；已创建历史面试的文本快照不变。仅无有效引用且没有相关活动上传/替换的旧原件可清理。
- **FR-008 / INT-AC-014 对接约束**：engine 只能用 owner 范围内的服务端当前简历、用户确认的版本读取并复制正文/version/hash；不接受客户端指定旧/他人 resumeId 或替代正文。后续保存/删除不改已有快照。
- **FR-021**：无引用原件清理使用耐久记录和执行权校验。失败不得宣称物理删除成功；保留不可业务读取的最小 key/token/state/错误记录供重试，未知活动写入不能被误删，迟到 put/finalize 不能恢复生效正文。
- **FR-022**：所有查询、版本、对象、receipt 与异步清理绑定 owner。ADMIN 无私有读取/修改绕过。后台从持久记录取得 userId。
- **FR-023（本任务部分）**：原件实际大小不超过 1,048,576 bytes；原件解码正文和最终编辑正文分别不超过 20,000 Unicode 码点。严格 UTF-8、控制字符与不成对 surrogate 校验，不静默替换、截断或规范化正文；头 BOM 仅在正文解码时剥除，原件 bytes/SHA 不变。空 MD 和空字符串不新增最低长度限制，null/exists 明确表示无当前简历。
- **FR-024（本任务部分）**：Markdown、HTML、脚本、代码块、链接与伪指令都只是资料文本；保存/读取不执行、不抓取，也不触发模型。模型提示词和浏览器显示由 engine/ui 继续遵守。
- **FR-025 / FR-026（存储执行权部分）**：上传预约、current swap、清理的 token/lease 与单调版本可核验。进程重启、超时和确认失败不使预约永远活跃，不允许旧 token 提交或 ABA；恢复保持旧 current 和可追踪清理状态。

## 已核实约束与复用

Java 17 / Boot 3.5 / MyBatis / Flyway；当前最新迁移 V18，实施前重查并追加实际下一版。沿用 Controller → Service interface → ServiceImpl → Mapper XML、CurrentUser.requireId、Session/CSRF、ProblemDetail、UUID handler 与短事务模式。

`AttachmentValidator.stage` 已与 post 无关，提供流式真实字节/SHA、严格 MD worker 和临时文件自动关闭；service 先执行 `.md` 白名单，再复用/提取现有小型 Markdown 文本 helper。保留社区 PDF/图片/空 MD validator 合同，不复制另一套文本安全规则。

当前只有 RustFS adapter；“无缝切阿里云”指未来不改业务代码、逻辑 key、DB/HTTP 和浏览器路径。所有业务只依赖 put/open/delete 和原 bytes/size/SHA，不存桶、endpoint、供应商 URL。阿里云申请、adapter、真实云调用、对象搬迁与切换验收延期；不因此禁用当前 RustFS 功能。

本任务包含必要的本地最小 IAM 前缀更新、import 专用 nginx 2m 运输限制，以及 PG+对象备份/全新隔离目标恢复合同。现有 backup.ps1 仅 PG；在线删除不代表旧备份也删除。

## 独立可观察验收

以下为待运行目标，规划完成不等于 PASS。

| 子验收 | 对应父 INT-AC | 操作与可观察结果 |
|---|---|---|
| RES-AC-01 owner 与私有响应 | 001 / 012 | A、B、ADMIN 登录真实 HTTP；本人 GET/HEAD 可读，交叉请求无内容/元数据/写入；匿名401、无CSRF写入403。所有 JSON/原件 no-store, private；无 key/公开或预签名 URL/redirect。 |
| RES-AC-02 导入、编辑和粘贴 | 010 / 011 / 012 / 023 | 合法 `.md` 通过 stage，原 bytes/size/SHA 与下载一致；最终编辑文本独立保存。EDIT_CURRENT保留服务端来源/原件；显式PASTE不put并解除旧原件引用；缺mode拒绝。链接/脚本不执行不抓取。非md/无效UTF8/control/不成对surrogate失败时current/version不变。 |
| RES-AC-03 双上限 | 023 | stage 单测1,048,576 bytes通过字节门禁、+1拒绝；完整import仍独立拒绝超20k解码正文。解码与最终正文各测20,000/20,001码点，含emoji/非BMP；等号允许、+1拒绝，无截断/覆盖。完整API成功必须同时符合两上限，空MD/空正文不新增最低长度。 |
| RES-AC-04 current 并发和 receipt | 013 / 020 | 同owner同expectedVersion两个独立事务保存不同内容，至多一个生效，另一409；同requestId同payload重放不多put/不多version，不同payload409。响应丢失后原receipt确认结果，GET恢复真实current。 |
| RES-AC-05 删除与 ABA | 013 / 020 | 保存A→删除→保存B单调version，删除不重置singleton。迟到A写入不能越过版本/token；无current仍返回exists=false与版本。失败替换保持A，成功仅B；删除解除读取且历史A不受影响。 |
| RES-AC-06 上传失败与清理 | 020 / 022 / 024 | 真实PG同步点注入put失败、DB确认失败、CAS竞争、JVM退出和迟到put；旧current不变，候选key耐久可追踪。活动预约或current引用对象不删；无引用清理失败业务不可读、状态非DELETED且可重试；旧finalize不能重绑。 |
| RES-AC-07 存储与代理 | 012 / 022 / 023 | 新隔离RustFS桶真实验证community/attachments/*与interview/resumes/* Put/Get/Delete，外前缀/桶管理/admin/匿名已有对象GET+HEAD拒绝。nginx只import允许2m运输，业务仍1MiB/20k；原社区入口回归。 |
| RES-AC-08 联合恢复 | 022 | 停止明确源writers后导出PG和必要对象manifest，在全新DB+桶+卷恢复，同key逐对象size/SHA一致，current正文/version/原件与社区引用可读取。缺必要对象/错SHA/既有目标/中断不标complete；源库/桶未被覆盖。 |
| RES-AC-09 对接快照 | 009 / 014 / 020 / 025 | 当前读取合同测试证明owner+expectedVersion与正文摘要一致，旧/他人版本不能代用；engine整合时用A建1→改B→建2，历史1始终A，无current通用面试成功。最大输入组合由engine接续。 |

RES-AC-09 的最终面试创建由 engine 执行；个人中心入口、失败保留 File/编辑内容由 ui 执行。各子任务独立结果与父整合结果分别记录，不把接口测试当浏览器通过。

## 非目标、风险与授权

不引入云adapter/账号/API申请、双读双写/自动迁移/供应商回退、未经批准兼容层、feature flag、旧简历选择、PDF/Word、按天业务删除、全桶IAM或浏览器直传。对象清理补偿与模型手动retry是不同机制。

核心风险是迟到/不确定put、数据库与对象快照一致性、严格policy shape更新和原件隐私。design给出耐久处理合同；验证使用合成身份及隔离库/桶，不读写日常数据。非破坏回退保留新增表、原件、receipt和最小权限，不清表/清桶、不提供旧行为路径。一般问题回流父research/issues.md于最终交付集中确认；新增范围、具体兼容、不可逆或真实付费动作需要相应明确授权。
