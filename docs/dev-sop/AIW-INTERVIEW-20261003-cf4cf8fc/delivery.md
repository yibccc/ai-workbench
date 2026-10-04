# 交付回流

handoff_id / revision：`AIW-INTERVIEW-20261003-cf4cf8fc` / `r1`

状态：**已推送，PR已创建，待审核/合并**。用户已批准 P-01 及连续实施、审查修复、隔离验证、规范更新、本地提交和归档，并在2026-10-04明确要求推送、创建MR及Trellis收尾。远端为GitHub，对应[PR #14](https://github.com/yibccc/ai-workbench/pull/14)。

业务 commit：`7e71e39636977b2b24eb8ca0e31150156db45162`，分支 `codex/ai-interview`。

review base：`0090d0127b0693c780aa3e587e20a6e6bae969fc`；reviewed HEAD：上述业务 commit。限定95个产品源码/配置/规范文件的提交前 SHA-256 指纹为 `c0ca0bf6a0f1605c5363673cbed46df209f22b0c39d3d999fed7d7a45292e2ec`，汇总值记录于父任务 `research/final-fingerprint.json`，不是整个 Git tree 指纹。交接14文件的原始字节及 SHA 已在 Git index 核验；`.gitattributes` 保留不可变输入字节。

Trellis 原实际任务：`.trellis/tasks/10-04-ai-interview`、`10-04-interview-resume`、`10-04-interview-engine`、`10-04-interview-ui`。四任务均已原生归档至 `.trellis/tasks/archive/2026-10/` 下同名目录，实际返回位置和完成状态已核验。下列链接指向归档位置：

- [父任务验收记录](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/validation.md)、[26项验收映射](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/research/final-acceptance.md)。
- [简历独立审查](../../../.trellis/tasks/archive/2026-10/10-04-interview-resume/check-report.md)、[引擎独立审查](../../../.trellis/tasks/archive/2026-10/10-04-interview-engine/check-report.md)、[前端独立审查](../../../.trellis/tasks/archive/2026-10/10-04-interview-ui/check-report.md)。
- [真实浏览器](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/research/browser-real-validation.md)、[真实备份恢复](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/research/ops-real-validation.md)、[真实代理边界](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/research/proxy-validation.md)、[最终运维审查](../../../.trellis/tasks/archive/2026-10/10-04-ai-interview/research/final-ops-review.md)。

归档/日志 commit：原交付四任务归档为`1435a2e`、`6978e56`、`3990b94`、`84d2f37`，日志`022dafc`；后续真实模型验证工作提交`b9de640`、归档`630d680`、日志`652d05e`。本轮远端交付文档及原生收尾日志另在同分支接续；日志只引用工作提交，不引用归档提交。

## 实际交付

已有原型 HTML 内容与 CSS 直接迁入现有 React 工作台，新增第六个保留工作区“AI面试”；私有个人中心从账号菜单进入，继续共用现有登录、Dialog、Markdown及导航基础。四尺寸六屏24张合成截图与真实流程截图已留档。

个人中心维护唯一当前 Markdown 简历，支持粘贴、`.md` 原件及独立编辑正文，原件字节和编辑文本分别保留；20,000 Unicode codepoint 与1MiB字节门禁独立验证。创建面试原子冻结当前正文/版本/SHA，替换或删除当前简历保留历史文本快照。私有读取、删除、失败清理、迟到上传、幂等回执及单调版本均实现。

面试支持四方向、三难度、3–20个主问题（默认5），每主问题预生成一个追问，固定2N轮。实现 JD 显式解析/失败保文/成功复用、逐轮草稿/锁定提交、提前结束、报告、历史及单场删除。失败评估为 null/NOT_EVALUATED；未答轮为0；全轮等权且 HALF_UP 两位小数。持久任务、排队期限、领取后lease、最后wall-clock fencing、重启只标失败及手动重试完成，GET不调用模型。

AgentScope 网关采用一次模型尝试，可信规则放 SystemMessage，完整非可信 JSON 放 UserMessage，不开启工具或隐式修复。真实浏览器链使用确定性测试网关和真实数据库/HTTP/对象存储；工程验收不等同真实模型能力验收。

本地 RustFS 应用专用策略已支持 `community/attachments/*` 与 `interview/resumes/*` 的 Put/Get/Delete，拒绝匿名、全桶列举、越界及管理权限。现有 ObjectStorage、对象key/字节/大小/SHA及HTTP合同保持供应商边界，数据库不保存供应商地址或签名。阿里云适配器、资源申请和真实迁移尚未实现。

新增 V19/V20，未修改历史迁移。联合备份包含数据库和被引用原件的manifest/SHA，完成标识最后落盘；恢复仅接受事先证明的新卷、新数据库和新桶。Nginx简历导入2m运输额度与后台1MiB/20k门禁同时成立，社区22m及其他API1m边界保留。

## 与原方案的偏差

原正式26 FR与26 INT-AC正文无损继承。P-01 已由用户明确批准。外部 `ai-interview` 项目的 AGPL Java、Prompt、Skill未直接复制；复用本交接原型及本仓库代码，独立实现已核实合同。网页目标commit在本地对象库不可用，已明确记录逐文件/符号核对，未声称完成不存在的 Git diff。

本轮未添加旧行为兼容层、自动云回退、双供应商门禁或默认禁用功能。真实付费模型及阿里云验证按最终批准延期；除此之外无未批准产品范围变化。

## AC 验收

结果 PASS 指工程证据。标注“真实模型 NOT_RUN”的项仍需真实供应商质量、容量和性能测量。人工验收未另行进行；自动浏览器、HTTP和真实隔离资源验证如下。

| AC | 结果 | 实际证据 | 人工验收 |
|---|---|---|---|
| INT-AC-001 | PASS | A/B/ADMIN私有隔离、CSRF、匿名/foreign HEAD | 未进行 |
| INT-AC-002 | PASS | N3/5/20严格2N题单与坏输出无子行 | 未进行 |
| INT-AC-003 | PASS | 真实PG提交并发/版本/receipt与浏览器两场提交 | 未进行 |
| INT-AC-004 | PASS | 两场草稿刷新/重登、未知ACK与旧GET屏障 | 未进行 |
| INT-AC-005 | PASS | 真实UI提前结束与PG提交/冻结竞争 | 未进行 |
| INT-AC-006 | PASS | 真实13.35报告、5未答0、稳定GET与PG舍入 | 未进行 |
| INT-AC-007 | PASS | SDK失败及mixed状态为null，不伪造0分 | 未进行 |
| INT-AC-008 | PASS | 原工作台/专注/社区加面试117完整浏览器通过 | 未进行 |
| INT-AC-009 | PASS | Mandatory快照、A/B正文、current删除保A、恢复HTTP | 未进行 |
| INT-AC-010 | PASS | 严格UTF8/BOM/control、无远程图片、System/User隔离 | 未进行 |
| INT-AC-011 | PASS | 真实File→编辑→保存及失败保持当前状态 | 未进行 |
| INT-AC-012 | PASS | 私有原件default/proxy/restore bytes与SHA | 未进行 |
| INT-AC-013 | PASS | 真实PG singleton、并发首写、CAS及单调版本 | 未进行 |
| INT-AC-014 | PASS | owner/version服务端简历快照、伪造DTO拒绝 | 未进行 |
| INT-AC-015 | PASS；真实模型 NOT_RUN | 四方向/三难度冻结、prompt/PG矩阵与真实UI配置 | 未进行 |
| INT-AC-016 | PASS | SDK单次计数、PG/恢复GET不增加job | 未进行 |
| INT-AC-017 | PASS；真实模型 NOT_RUN | 严格enum、方向规则及Agent通用第三场 | 未进行 |
| INT-AC-018 | PASS | JD owner/rawhash/成功复用/配置失效与恢复 | 未进行 |
| INT-AC-019 | PASS | 无key真实FAILED保原文，UI手动重试 | 未进行 |
| INT-AC-020 | PASS | current替换失败保旧、删除保history与恢复B/fileA | 未进行 |
| INT-AC-021 | PASS | 各阶段删除/token不复活、真实first404/second200 | 未进行 |
| INT-AC-022 | PASS | 迟到put/cleanup、2对象新目标恢复与完整性负例 | 未进行 |
| INT-AC-023 | PASS | BMP/emoji精确边界、1MiB/+1及Nginx复合门禁 | 未进行 |
| INT-AC-024 | PASS | 实际JVM中断/重启0calls、queue/lease最后CAS | 未进行 |
| INT-AC-025 | PASS；真实模型 NOT_RUN | PG20main/40turn+最大文本、SDK最大完整wire | 未进行 |
| INT-AC-026 | PASS | 多场不同配置draft/submit/刷新重登及独立删除 | 未进行 |

最终完整门禁：后端离线 Java17 `clean verify` **271 tests，0 failures/errors/skipped**；前端 lint、严格类型检查、Vite build PASS；浏览器 **117 PASS，0 failed/flaky/retry**（13社区、14专注、28面试、62工作台）；ops **33 PASS**；真实代理 **29 HTTP +4匿名GET/HEAD PASS**；联合备份恢复 **2对象、31表/Flyway/SHA一致**，6个完整性/既有目标负例通过。重复定向测试不累加为新的总用例。

## Review 问题与处理

已修复并复审：lease最后提交越界、版本ABA/删除重放、迟到上传与清理、SDK隐式重试和消息隔离、UI旧GET覆盖ACK/身份失效残留、真实报告主问题编号、备份WinPS环境和多行JSON解析。完整门禁首轮的账号bootstrap/live URL污染已通过窄化测试环境及独立schema解决，未改账号规则；旧撤销夹具时序已保留真实401与完整Toast。最终只读提交审查未发现秘密、备份或原始trace混入。曾在验证摘要前段残留的进行中表述已更新为最终事实。

自有临时进程清理首次在pwsh7中因时间类型转换被guard拒绝，改用脚本既定Windows PowerShell 5.1入口严格核验PID/创建时间/命令完成；未绕过身份guard。详细台账见父任务 `research/issues.md`，无开放产品阻塞。

## 规范沉淀

新增 backend `private-resumes.md`、`private-interviews.md` 与 frontend `private-interviews.md`，更新两层index。补充 private-attachments、local-delivery、linux-deployment 的对象key合同、联合恢复、身份证明与代理额度；更新 frontend directory-structure、identity-session、viewport-layout、dialogs 的六工作区、身份清理、版本ACK与导航规则。

## 残余风险与下一轮

- 阿里云API申请、适配器、真实访问及同key对象字节/SHA迁移：**NOT_RUN/延期**。供应商边界已准备，尚不能声称配置即可直接启用阿里云。
- 真实模型基础出题/评分链路及本次样例耗时已按后续授权完成，见下节；最大上下文容量、全方向/长期质量、供应商usage/费用仍**NOT_MEASURED/延期**。
- Vite主产物约563KB提示：构建通过，非阻塞，后续可按实际性能需求拆包。
- 外部AGPL代码复制与许可义务不属于本交付；如后续需要，应另核具体范围。

用户此前已批准延期边界，随后明确要求配置的DeepSeek Flash并提供简历，批准真实验证任务；本轮没有新增产品选择或兼容方案。

## 后续真实 DeepSeek Flash 验收（2026-10-04）

原交付的工程验收与模型延期为当时事实。本次用户明确授权配置的`deepseek-flash`，提供两页PDF简历并同意验证任务；原产品代码未改变。题单与完整评分已实际通过，其中包含一次明确业务手动重试，不能声称首轮零失败。

[真实验收记录](../../../.trellis/tasks/archive/2026-10/10-04-interview-live/validation.md)与[独立复核](../../../.trellis/tasks/archive/2026-10/10-04-interview-live/check-report.md)已原生归档，包含初次失败与恢复证据。输入去姓名/联系信息后3002 codepoints，仅本地忽略目录；未新增PDF导入行为。default真实AgentScope网关、模型snapshot deepseek-flash、独立schema/Redisnamespace/合成账号已核验，Vite实际进程秘密计数0，普通.env字节SHA不变。

- 真实生成约**10.328秒**：AGENT_DEVELOPMENT/MID、5主问/5预生成追问、0..9连续和父索引全合法，紧贴Agent工程项目深度。
- 初次五组评分：三组成功，一组MODEL_OUTPUT_INVALID，一组因验收脚本过早收尾被中断；已提交的失败轮与总分正确为null。**28.547秒是首次可读失败，不是五组完成耗时。**
- 已修正本地验收脚本等待所有job终态；重启只按原生租约恢复中断组为FAILED/EXECUTION_EXPIRED，无新增模型job。随后一次显式业务evaluation/retry只重试必要组6/8，原成功组0/2/4结果与job完全复用。
- 手动重试约**21.032秒**后五组均SUCCEEDED，十轮均SCORED，有有限0..100分及具体反馈；按全十轮等权/HALF_UP核得**67.60**。回答均为明确验收示例，其中一轮刻意薄弱为16分，**此分数不是用户本人能力评价**。
- 持久job从6到8，仅新增两个评分job；多次GET前后数据库快照相同，所有job终态后按身份清理应用与保活。job数量不等于外部HTTP抓包计数。

遗留边界：初次非法模型输出的vendor原始文本/细分原因未捕获，不据安全码认定产品根因；供应商token/账单和wire计数NOT_MEASURED；JD、最大40轮/20k简历/10kJD及长期/全方向质量未测。没有自动付费重试、输出修复、fixture兜底、prompt或校验放宽。验收脚本提前清理问题已修复并复核，原始失败证据保留；无新增产品阻塞。

## 发布与恢复

2026-10-04已推送`origin/codex/ai-interview`并创建[PR #14](https://github.com/yibccc/ai-workbench/pull/14)，目标`master`，非draft、open、未合并；创建后GitHub确认mergeable=true。首次远端head为`652d05ec90114584f844c166e1ee74d079ca56a1`，base为`cf6ca832bdfffe13628489a99e80bbd3bbe9940a`；本轮交付状态和日志接续推送到同一PR分支。

获取远端后核实其新增两个合并提交的代码树与原已验证基线`0090d0127b0693c780aa3e587e20a6e6bae969fc`完全相同，未进行不必要的rebase或改树。PR范围独立核证仅为本需求，原六项工作区改动和`.local-runtime`未包含。未合并或部署。所有自有临时测试应用、代理和保活进程已按身份核验停止，隔离测试数据卷保留。日常 `.env` SHA、共享RustFS策略及密码不变；日常数据库/应用进程未触。原有六项用户改动与`00-bootstrap-guidelines`任务保留。

原始trace与含凭据运行文件仅本地忽略，未入库。恢复必须先停已证明属于源的写入者并验证完整bundle，再使用prepare-volumes事先证明的新目标，核对引用对象字节/SHA和数据库；保留失败现场，不覆盖旧卷或用户数据。V19/V20撤回不能仅靠回退JAR，应依据已验证数据库+对象联合备份制定恢复操作。
