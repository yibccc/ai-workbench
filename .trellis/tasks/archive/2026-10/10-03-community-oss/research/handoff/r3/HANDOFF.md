---
handoff_id: WB-20261003-community-oss-3f9aaa
revision: 3
status: ready-for-local-review
repository: yibccc/ai-workbench
snapshot_branch: master
snapshot_commit: f27eda5f50cf298c5e864acc4961f5838efd4ac6
code_verified: true
task_size: complex
product_decisions: confirmed
---

# 个人工作台 · 站内广场与 OSS 附件交接

冻结日期：2026-10-03。采用仓库 [docs/dev-sop/HANDOFF-CONTRACT.md v2.0](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/docs/dev-sop/HANDOFF-CONTRACT.md) 的字段与职责。本包 revision 3 接续交接包 revision 2；原型仍是 UI R2，二者版本号相互独立。

## 1. 交付结论与授权边界

**本轮产品决定已全部收敛，状态更新为 `ready-for-local-review`。** 用户在批准 R2 全部页面布局后进一步确认：“保存方式、历史版本保留、管理员下架权限和附件清理策略等其他的全部同意”。本包据此将 revision 2 中 DEC-11～DEC-13 的剩余产品建议转为 selected，并关闭 OQ-01～OQ-05 的产品未决项。

`product_decisions: confirmed` 只表示本交接中列出的产品行为已获用户确认；不表示 OSS 地域/Bucket/Endpoint、SDK 精确版本、代码路径、数据库字段、路由库、缓存头、并发实现或兼容方案已经验证或批准。`code_verified: true` 仍仅表示 solution 证据表内列出的仓库代码/文档曾静态核对，不表示全仓阅读、编译、测试或 OSS 验证。

`ready-for-local-review` 仅表示可交给本地代理核对快照、适配 Trellis 正式 PRD/design/implement 规划；**不表示批准编码、创建/启动 Trellis 任务、执行脚本、数据库迁移、云资源操作、commit、push、PR 或部署。** 若本地发现实现必须引入旧 hash 兼容、旧版回退、功能开关或默认禁用门禁，仍须按 AGENTS.md 先取得用户对具体兼容方案的明确同意。

### 本次修订：revision 2 → revision 3

本轮没有新增功能，只把 revision 2 已列出的其余产品建议转为已确认：今日素材选择与独立发布快照；作者公开资料边界与图片操作；手动保存私有草稿、未保存离开确认、显式发布更新且编辑不重置首次发布时间；保留发布历史版本；管理员可下架并记录理由/审计，作者不能绕过下架自行再发布；附件按不同附件计数，历史保留版本继续占用其引用，只有不再被当前发布、有效草稿或保留历史版本引用的对象才可清理。首版不增加管理员“恢复”流程、每用户存储配额或自动定时清理天数。

原型 R2 的 30 个文件继续逐字节保留，不重绘 PNG、不修改 HTML/CSS/JS、不增加页面或功能。revision 1/2 原包保持不变；本包是同一 handoff 的新 revision。仓库快照与静态代码证据继承 revision 1，本次未重新拉取远端 HEAD、运行工程或执行 OSS 验证。

## 2. 用户已确认范围（准确摘要）

已有工作台用户可以分享今天做了什么、发动态和写博客，其他有效登录用户可浏览；匿名公开留作后续扩展；不做点赞和评论。附件接入阿里云 OSS，图片仅 JPG/JPEG、PNG、WebP（5 MiB），PDF（20 MiB）、MD（1 MiB），每篇最多 10 个/50 MiB；PDF/MD 仅下载，不做站内预览，不自动转换图片或导入 MD 正文。

私有 OSS 桶，后端中转上传并校验，后端按会话、稿件归属和当前可见发布引用读取；不做浏览器直传，不向读者提供 OSS 直连/预签名 URL。草稿附件仅作者可读，撤回或下架后拒绝新的读者读取请求。必要时允许前后端做有边界的重构，不授权顺手重写和兼容层。

R2 全部页面布局已经用户明确批准；原型与截图为布局基线，详细范围与验收见 DEC-16、requirements 的 AC-22。

## 3. 决定登记

| 编号 | 类别 / 状态 | 决定或候选内容 | 用户依据 / 处理边界 |
|---|---|---|---|
| DEC-01 | product / selected | 首版所有有效登录用户可见；不按作者在线状态限制。匿名公开为后续独立扩展，不自动公开既有站内内容。 | 用户：“先做登录可见，但把匿名公开作为明确的后续扩展边界。”后续多次确认范围汇总。 |
| DEC-02 | product / selected | 今日分享、动态、博客的发布与浏览；不含点赞、评论。 | 初始明确要求分享今天、博客、动态；用户：“首版按『发布与浏览，不含点赞和评论』收敛”。 |
| DEC-03 | technical / selected | 阿里云 OSS 私有桶；后端中转上传，后端鉴权后返回文件，不采用浏览器直传，不向读者返回或重定向至 OSS 直连/预签名下载地址。 | 用户“附件接入阿里云oss”，并对完整链路提问回答“确认”。 |
| DEC-04 | product / selected | 仅 JPG/JPEG、PNG、WebP、PDF、MD；不支持 GIF、SVG、HEIC 等，也不增加自动格式转换。 | 用户“首版按 图片 + PDF + md”，并确认图片白名单。 |
| DEC-05 | product / selected | 图片 5 MiB、PDF 20 MiB、MD 1 MiB；每篇最多 10 个、合计 50 MiB。 | 用户给出上述数值，并再次回答“按这组上限收敛”。MiB 单位沿用前文已明确的字节计量。 |
| DEC-06 | product / selected | PDF 和 MD 仅下载，不做站内预览；MD 附件不自动导入为正文。 | 用户明确：“PDF 和 MD 首版仅提供下载、不做站内预览”，后续确认汇总。 |
| DEC-07 | product+technical / selected | 草稿附件仅作者可读；当前可见发布版本的附件可由有效登录成员读取；撤回/下架后拒绝新的读者请求，不能收回已下载副本。 | 完整附件链路及范围汇总得到“确认”；下架管理的具体操作设计未因此获批。 |
| DEC-08 | technical / selected | 必要时允许一起调整前后端；该许可不是重写整个工程或新增基础设施的批准。 | 用户初始：“必要时允许一起重构前后端”。兼容方案仍受 AGENTS.md 的单独确认约束。 |
| DEC-09 | product / selected | 冻结已明确范围，交付四份文档及刚生成的 R2 原型，不新增功能；未决产品点标 draft。 | 本次用户交付指令。要求“包含原型”不等于“批准原型全部行为”。 |
| DEC-10 | technical / proposed | 同一应用新增独立发布模块，保留现有技术栈；整理前端路由/账号根/页面外壳，不放开旧业务查询的 owner 条件。 | 既有方案建议；组织方式由本地评估，不把具体库、表、端点当成已存在。 |
| DEC-11 | product / selected | 今日分享先选指定日期素材，默认不选；跨页保留选择，只带入用户选择公开的字段；专注合计额外勾选且不计作完成次数。生成稿可编辑，原记录后续变更/删除不自动改写已发布快照。同一日期允许多篇，不设“一天一篇”唯一约束。 | 用户本轮“其他的全部同意”，承接 R2 review-notes 与既有 DEC-11。 |
| DEC-12 | product / selected | 作者主页只展示公开昵称/简介和已发布内容，不展示登录用户名、私有日历/任务数/在线状态；图片可在正文显示并点击放大。编辑器采用手动保存私有草稿、未保存离开确认；保存不公开，显式“发布更新”才切换读者版本；编辑不重置首次发布时间、不重新置顶。发布历史版本保留，当前读者只读当前发布版本。登录后返回原受支持站内目标。 | 用户本轮“其他的全部同意”，承接 R2 review-notes/原型既有行为。演示身份、状态菜单和静态专注芯片仍不进入生产。 |
| DEC-13 | product / selected | 现有 ADMIN 可对站内发布执行下架；下架记录理由与审计信息，作者不能自行绕过下架重新发布。首版不增加恢复流程。附件额度按不同附件 ID 计数，正文重复引用同一附件不重复计数；保留历史版本引用的附件继续保留，移除编辑器引用不立即删除；仅无当前发布、有效草稿或保留历史版本引用的孤立对象可清理，失败可重试。首版不设自动清理天数或每用户总存储配额。 | 用户本轮“保存方式、历史版本保留、管理员下架权限和附件清理策略等其他的全部同意”；不新增举报、通知或独立管理平台。 |
| DEC-14 | technical / proposed | 旧 hash 链接兼容、生产路由变化、特定回退方式、功能开关。 | 早期建议没有获得具体批准；AGENTS.md 禁止自行加入。不得以“安全上线”为由默认关闭已要求功能。 |
| DEC-15 | technical / delegated | 本地核对部署配置、OSS 凭证注入与最小权限、SDK 锁定、解析器、流式 I/O、事务、状态实现、缓存/错误合同及测试命令。 | 按项目职责边界留给本地细化；凡影响产品行为、兼容或重大风险者须回到用户，不能借 delegated 代选。 |
| DEC-16 | product / selected | R2 全部页面布局获批：桌面/移动导航、广场、素材选择、编辑与附件区、发布预览、详情、我的发布、撤回确认、作者主页、登录及异常/空状态等既有画面的区域布局与视觉呈现。 | 用户本轮原话：“R2 的页面布局全部批准”。覆盖整个 R2，不限于 17 张 PNG；原型的演示工具/虚构数据不是生产功能，业务默认值、权限、保存/清理/兼容规则仍按各自 DEC/OQ 状态处理。 |
| DEC-17 | product / selected | revision 2 中剩余产品建议全部批准；本包产品决定可进入本地核对，不再以 OQ-01～OQ-05 阻塞。 | 用户本轮原话：“保存方式、历史版本保留、管理员下架权限和附件清理策略等其他的全部同意”。 |

## 4. 已关闭的产品未决项与仍需本地核实项

OQ-01～OQ-05 已由 DEC-11～DEC-13、DEC-16、DEC-17 关闭，不再作为产品阻塞项。其最终行为以本包 requirements 的 AC 为准；原型历史文字中的“待审阅/建议”不再覆盖本轮用户确认。

| 编号 | 状态 | 结论 / 本地责任 |
|---|---|---|
| OQ-01 | closed | 作者公开资料与图片操作按 DEC-12；具体数据库字段/编辑入口实现由本地适配，不得暴露登录用户名或私有指标。 |
| OQ-02 | closed | 今日素材默认不选、跨页保留、公开字段控制、专注额外勾选；同日允许多篇；来源后续变更不自动改写发布快照。 |
| OQ-03 | closed | 手动保存私有草稿、未保存离开确认、显式发布更新、首次发布时间不因编辑重置；发布历史版本保留。冲突检测的技术实现由本地固定。 |
| OQ-04 | closed | ADMIN 可下架并记录理由/审计；作者不可绕过下架再发布；首版不提供恢复流程。具体管理端点/审计字段由本地设计。 |
| OQ-05 | closed | 单篇额度按不同附件 ID 计数；同 ID 重复正文引用不重复；保留历史版本引用继续占用；只有无当前发布/有效草稿/保留历史引用的孤立对象可清理。首版不设自动清理天数或用户总配额。 |
| OQ-06 | local-review / compatibility | 新路由与旧 hash 的兼容、明确回退方案仍须本地先定位影响；若确需兼容层/回退/功能开关，按 AGENTS.md 回到用户确认具体方案，不能由本包自动授权。 |
| OQ-07 | local-review / technical | OSS Bucket、地域、Endpoint、部署身份、最小权限、SDK 精确版本、上传/读取超时、代理/缓存及实现细节由本地核实；聊天不接收密钥。 |

没有新增图片处理、通知、举报、点赞评论、作者在线状态、开放注册、病毒扫描云服务、用户存储配额或管理员恢复流程。常规内部实现细节按 DEC-15 留给本地；影响外部行为、兼容或重大风险的新增选择仍需用户确认。

## 5. 仓库快照与事实来源

revision 1 冻结时通过 GitHub 读取 `master`，得到完整 commit `f27eda5f50cf298c5e864acc4961f5838efd4ac6`；相关固定文件来自该 ref，不拿文件 blob SHA 代替 commit。本地仓库路径、分支、未提交改动与运行/部署状态为未知，未运行 git status 或工程构建。

revision 1 冻结时新读：交接合同 v2.0、AGENTS 全文、README 第 1–28 行、`.trellis/tasks/` 顶层及 2026-10 归档目录、关联专注进展任务 PRD。前序同快照实际读到的 App/API/Mapper/认证代码、依赖声明和 spec 清单/范围完整列于 [solution.md](solution.md) 第 2 节 S-01～S-20。不可据此称阅读了所有历史任务或完成全仓安全审计。

分支来源：https://api.github.com/repos/yibccc/ai-workbench/branches/master 。冻结源树：https://github.com/yibccc/ai-workbench/tree/f27eda5f50cf298c5e864acc4961f5838efd4ac6 。兼容限制：[AGENTS.md](https://github.com/yibccc/ai-workbench/blob/f27eda5f50cf298c5e864acc4961f5838efd4ac6/AGENTS.md)。外部 OSS/OWASP 一手来源、访问日期和适用版本在 solution 第 11 节；不把外部文档当作实际资源配置证明。

## 6. 原型来源、包含范围与真实性

原型直接来自本对话已交付 `workbench-community-r2.zip`，原始 ZIP SHA-256：

`20668065708ff241b4e26f0f9c3ea2076b4b114f0fed9f1af80a6c4b9540769c`

30 个原文件逐字节保留在 `assets/prototype-r2/`：单文件 HTML、可编辑源码、17 张桌面/手机 PNG、1 张手机总览、原型说明和原有检查脚本/记录。没有重写原型或伪造新截图，不包含字体文件。原 zip 本身不重复套入本包。

原型“演示身份”“状态演示”和静态专注芯片只是审阅工具，不是产品新功能；PDF/MD 按钮不实际传输文件。R2 的 README 写“不是冻结交接包”是对该独立原型身份的说明，不与本包归档矛盾。原型仍为只读参考代码，布局状态已由本次 DEC-16 更新为 selected；原型 README/review-notes 中的“布局待审阅”是历史文本，不再作为本地重复征求布局批准的依据。

revision 2 本轮未重新执行原型/浏览器、后端、数据库或 OSS 测试，也未重新读取远端仓库。保留的 `checks/results.json` 含 59 条 PASS，是原附带记录，不转化为本交接测试结论。本次文件级核对见 PACKAGE-CHECKS。所有示例是原型中的虚构数据，不包含真实数据库或个人工作记录。

## 7. 文件清单与摘要

共 36 个文件：四份主文档、PACKAGE-CHECKS、根 SHA256SUMS，以及 30 个原型文件。根摘要清单覆盖除自身外的全部 35 个文件，包含原型自己的摘要文件；zip 的旁置 `.sha256` 覆盖整个归档。以下每个路径都是本次实际交付的相对路径，不是用户本地工作区路径。

| 文件（相对本包根） | 用途 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | 合同元数据、决定、未知、来源、接入与全清单 |
| [requirements.md](requirements.md) | FR 与唯一 AC 定义，区分基线和 proposed |
| [solution.md](solution.md) | 已选/候选设计、静态证据、状态/接口/兼容与风险 |
| [acceptance.md](acceptance.md) | 引用 AC 的 TC 场景，无执行结果 |
| [PACKAGE-CHECKS.md](PACKAGE-CHECKS.md) | 本次交接文件级检查范围与结果 |
| [SHA256SUMS.txt](SHA256SUMS.txt) | 全部其他实际文件的 SHA-256；清单不自哈希 |
| [assets/prototype-r2/CHECKS.md](assets/prototype-r2/CHECKS.md) | 上轮原型局部检查记录；本次未复跑 |
| [assets/prototype-r2/README.md](assets/prototype-r2/README.md) | 原型使用/范围/非生产限制 |
| [assets/prototype-r2/SHA256SUMS.txt](assets/prototype-r2/SHA256SUMS.txt) | 原型原始摘要清单，原样保留 |
| [assets/prototype-r2/build.py](assets/prototype-r2/build.py) | 原型内联 HTML 构建参考脚本，不自动执行 |
| [assets/prototype-r2/checks/results.json](assets/prototype-r2/checks/results.json) | 上轮原型结果数据；不是 Trellis 上下文 |
| [assets/prototype-r2/checks/smoke.py](assets/prototype-r2/checks/smoke.py) | 原型浏览器检查参考脚本，不自动执行 |
| [assets/prototype-r2/index.html](assets/prototype-r2/index.html) | 单文件交互原型（建议实现） |
| [assets/prototype-r2/mobile-overview.png](assets/prototype-r2/mobile-overview.png) | 三张手机视图总览拼版 |
| [assets/prototype-r2/preview-gallery.html](assets/prototype-r2/preview-gallery.html) | 17 张页面截图索引 |
| [assets/prototype-r2/previews/01-community-desktop.png](assets/prototype-r2/previews/01-community-desktop.png) | 原型渲染 PNG：01-community-desktop |
| [assets/prototype-r2/previews/02-source-selection-desktop.png](assets/prototype-r2/previews/02-source-selection-desktop.png) | 原型渲染 PNG：02-source-selection-desktop |
| [assets/prototype-r2/previews/03-editor-desktop.png](assets/prototype-r2/previews/03-editor-desktop.png) | 原型渲染 PNG：03-editor-desktop |
| [assets/prototype-r2/previews/04-publish-preview-desktop.png](assets/prototype-r2/previews/04-publish-preview-desktop.png) | 原型渲染 PNG：04-publish-preview-desktop |
| [assets/prototype-r2/previews/05-article-desktop.png](assets/prototype-r2/previews/05-article-desktop.png) | 原型渲染 PNG：05-article-desktop |
| [assets/prototype-r2/previews/06-my-posts-desktop.png](assets/prototype-r2/previews/06-my-posts-desktop.png) | 原型渲染 PNG：06-my-posts-desktop |
| [assets/prototype-r2/previews/07-withdraw-confirm-desktop.png](assets/prototype-r2/previews/07-withdraw-confirm-desktop.png) | 原型渲染 PNG：07-withdraw-confirm-desktop |
| [assets/prototype-r2/previews/08-author-profile-desktop.png](assets/prototype-r2/previews/08-author-profile-desktop.png) | 原型渲染 PNG：08-author-profile-desktop |
| [assets/prototype-r2/previews/09-login-gate-desktop.png](assets/prototype-r2/previews/09-login-gate-desktop.png) | 原型渲染 PNG：09-login-gate-desktop |
| [assets/prototype-r2/previews/10-upload-error-desktop.png](assets/prototype-r2/previews/10-upload-error-desktop.png) | 原型渲染 PNG：10-upload-error-desktop |
| [assets/prototype-r2/previews/11-community-mobile.png](assets/prototype-r2/previews/11-community-mobile.png) | 原型渲染 PNG：11-community-mobile |
| [assets/prototype-r2/previews/12-source-selection-mobile.png](assets/prototype-r2/previews/12-source-selection-mobile.png) | 原型渲染 PNG：12-source-selection-mobile |
| [assets/prototype-r2/previews/13-editor-mobile.png](assets/prototype-r2/previews/13-editor-mobile.png) | 原型渲染 PNG：13-editor-mobile |
| [assets/prototype-r2/previews/14-attachments-mobile.png](assets/prototype-r2/previews/14-attachments-mobile.png) | 原型渲染 PNG：14-attachments-mobile |
| [assets/prototype-r2/previews/15-article-mobile.png](assets/prototype-r2/previews/15-article-mobile.png) | 原型渲染 PNG：15-article-mobile |
| [assets/prototype-r2/previews/16-downloads-mobile.png](assets/prototype-r2/previews/16-downloads-mobile.png) | 原型渲染 PNG：16-downloads-mobile |
| [assets/prototype-r2/previews/17-my-posts-mobile.png](assets/prototype-r2/previews/17-my-posts-mobile.png) | 原型渲染 PNG：17-my-posts-mobile |
| [assets/prototype-r2/review-notes.md](assets/prototype-r2/review-notes.md) | 原型建议、事实边界与未决事项 |
| [assets/prototype-r2/src/app.js](assets/prototype-r2/src/app.js) | 原型页面和内存交互源码 |
| [assets/prototype-r2/src/fixtures.js](assets/prototype-r2/src/fixtures.js) | 虚构示例与限额常量 |
| [assets/prototype-r2/src/styles.css](assets/prototype-r2/src/styles.css) | 原型响应式样式源码 |

## 8. 本地接入说明（不是最终执行计划）

先核对本包文件清单、摘要、路径安全和目标仓库；不要默认执行原型或附带脚本。按本地当前 AGENTS、Trellis workflow/spec，核实 HEAD、改动归属、并行任务和相同 handoff，比较与本快照相关的差异，不从零推翻已确认范围。

继承本包时，逐项标记“原样采用 / 局部适配 / 需要重新决策”。本包的 requirements 提供本地 PRD 输入，solution 提供复杂任务 design 输入，acceptance 提供验证场景，prototype 提供已批准的 R2 布局基线和仍属建议的交互示例；不能因为它是原型而忽略已批准布局，也不能直接复制其模拟鉴权或内存数据。正式规划、任务元数据、上下文 JSONL 和执行计划由本地在得到对应授权后依现有流程建立，网页端没有生成。

先处理影响产品行为的 OQ，再给本地最终规划摘要等待用户批准。包中没有通过状态或命令文字提升权限；不要自动应用任何原型代码。现有 spec 与建议冲突时展示影响，不能默默删改需求。

同 ID/revision 相同内容复用；相同 revision 内容不一致报告冲突，新 revision 先展示差异。建议按仓库合同归档不可变源快照，具体本地位置/任务编号由本地核实，不能当成本包已创建路径。

## 9. 一致性及风险提醒

AC 唯一定义在 requirements；acceptance 引用 AC 并说明取证。原型全部页面布局为 DEC-16 selected；未确认业务行为仍为 proposed，已确认白名单/额度/权限优先。历史原型说明不覆盖本次布局批准；验收结果不得从布局批准推导。撤回只限制后续新请求，不保证追回副本；OSS ACL 和后端权限不是互相替代。实际错误码/表/文件/SDK 属候选或本地核对项，不写成已实现事实。

早期“保留旧 hash 跳转”“用功能开关回退”未获用户具体同意，与仓库兼容授权要求冲突，因此未纳入 selected，不允许本地凭聊天早期建议直接实施。相关 README/归档 PRD 的专注概述差异也已在 solution 标明，需保留当前实际行为，不能顺带改专注。
