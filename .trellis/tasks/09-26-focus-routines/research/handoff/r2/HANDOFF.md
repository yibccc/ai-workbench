---
handoff_id: WB-20260926-focus-routines
revision: 2
status: ready-for-local-review
repository: yibccc/ai-workbench
snapshot_branch: master
snapshot_commit: efa1962cc99057e5a4f7ec7247768c45107ed85b
code_verified: true
task_size: complex
product_decisions: confirmed
---

# 专注与每日重复任务 · 本地交接入口

编制日期：2026-09-26。遵循本仓库 `docs/dev-sop/HANDOFF-CONTRACT.md` 2.0（已在上述commit读取）。

**状态仅表示可以交给本地核对。** 本包不是任务创建、开工、提交、归档、推送或发布授权；没有生成最终Trellis元数据/JSONL，没有修改目标仓库。`code_verified: true` 只表示下表范围的代码事实经过静态阅读，不是全仓库核查、编译、测试或运行验收。

## 1. 用户已确认的内容

原始需求准确摘要：在工作台增加记录专注的功能，能指定每日重复任务、临时自定义专注时间，按间隔响铃提醒闭眼15秒，相关投入记录到“今天干了什么”。

r1确认依据：用户在首版完整方案说明后回复“确认”。r2变更依据：用户明确要求 **“专注新开一页吧，不和今日工作挤”**，随后回复 **“继续”**。

【用户已决定】专注作为独立业务页面；原先在工作记录页顶部放专注卡片和用抽屉承载计时的方式取消。计时、重复规则和今日专注汇总归专注页；工作记录页仍展示结束后的投入，不与计时控制混排。此前已确认的业务语义不变。

具体页内排版、颜色、图标和辅助层仍为 proposed；本地工程选择、阈值、迁移号、浏览器矩阵尚需核实。产品确认不表示任务创建或实现授权。

本包是r2完整替代包，不是只含差异的补丁。r1附件及ZIP保持原样；本地如已接入r1，先比较本包修订摘要，不直接覆盖已批准的活动任务规划。

## 2. 最小交付闭环与范围

每天/自选星期模板 → 当日首次进入生成独立待办 → 从待办转入独立专注页或临时目标开始 → 25分钟快捷值/自定义净时长 → 可调整间隔的15秒微休息 → 结束自动记录真实投入 → 当日统计和既有日报/周报来源。

必须分开“专注投入”和“任务完成”；任务重开不能擦掉计时历史。微休息是可选引导，不是闭眼检测或效率承诺。没有网页后台准点、离线原生闹钟、完整多轮25/5番茄流程、评分/排行/摄像头功能。

## 3. 实际文件清单

| 文件 | 内容和使用方式 |
|---|---|
| `HANDOFF.md` | 本入口、基线、确认依据、读取范围、移交门禁。 |
| `requirements.md` | 目标/范围、FR-001～005、AC-001～007唯一需求定义；r2仅新增独立页约束。 |
| `solution.md` | selected/proposed/delegated决定、数据/状态/API/接入建议、风险/回退、本地未知及外部证据。 |
| `acceptance.md` | TC-001～020引用AC列前置、操作、预期和层级；没有工程测试结果。 |
| `assets/focus-prototype.html` | 自包含HTML/CSS/JS界面参考；合成数据、静态计时，默认只读，不自动执行或覆盖源码。 |
| `assets/overview-desktop.png` | 独立专注页的准备开始、汇总和提醒设置。 |
| `assets/focus-desktop.png` | 桌面独立专注页；不是工作记录上的抽屉。 |
| `assets/microbreak-desktop.png` | 15秒微休息引导、跳过和关闭。 |
| `assets/repeat-desktop.png` | 自选星期/默认时长的重复任务抽屉。 |
| `assets/focus-mobile.png` | 手机独立专注页、导航与完整计时控制；下方次要内容可滚动。 |
| `assets/records-desktop.png` | 工作记录页无专注大卡，原列表保留投入记录；顶栏仅有紧凑入口。 |
| `assets/prototype-checks.json` | r2独立HTML渲染/交互日志；不是工程验收或Trellis上下文。 |
| `assets/README.md` | 原型类型/基线/局限、图片尺寸、真实沙箱检查范围及复核方式。 |
| `assets/render_preview.py` | 本包PNG渲染和原型局部冒烟的参考脚本；只作用于本包，按授权可复现，不是仓库测试。 |
| `SHA256SUMS` | 上述全部文件的SHA-256；自身不纳入自引用摘要，zip摘要可在传递时另外计算。 |

所有附件均属于此revision。不能只读四个主文件就宣称继承了UI设计；任何来源代码都不可自动应用。包中没有字体文件、密钥、真实数据库或个人工作记录。

## 4. 决定与未知

产品selected决定见 `solution.md` DEC-001～006；新UI视觉建议见DEC-010。候选表/命令设计为proposed，准确接口、迁移、恢复阈值、主控策略和兼容矩阵由本地核实（DEC-007～009）。

已知工程未知 U-001～007：本地工作区/HEAD；通用自动记录保护与完整报告链；区间恢复/主控/阈值；HTTP/HTTPS与浏览器能力；归档/删除关联；新来源部署/回退版本；独立页导航、图标与窄屏接入。它们必须在本地正式规划中闭合；若发现改变已确认范围、行为、公共合同、兼容或风险的冲突，返回用户而非自行重选产品。

复杂度来自重复实例与现有任务结合、持久化计时/异常恢复、跨日来源和已启用用户隔离，不代表允许额外架构抽象。本包只交必要主文档与UI附件。

## 5. 仓库快照与实际读取范围

r1于2026-09-26两次通过GitHub读取master分支；r2本轮再次读取，仍指向 `efa1962cc99057e5a4f7ec7247768c45107ed85b`。文件用固定commit ref读取。分支元数据来源：<https://api.github.com/repos/yibccc/ai-workbench/branches/master>；该URL会变，包中完整commit是本次基线。

S-001～S-018继承本需求探索/确认轮的已记录静态阅读；r2重新读取S-008并补充S-019～S-021。未读内容不推断，不把目录定位当成文件审查。路径均相对仓库根，单文件链接固定到commit。

| ID | 文件/范围 | 已核实用途与限制 |
|---|---|---|
| S-001 | [README.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/README.md) | 已读功能、架构、部署/测试约束；README记载不等于本轮跑过验证。 |
| S-002 | [AGENTS.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/AGENTS.md) | 已读Trellis入口；未运行本地Trellis。 |
| S-003 | [.trellis/workflow.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/workflow.md) 1–200行 | 已读计划/创建与启动权限分离；后续阶段未完整读取。 |
| S-004 | [HANDOFF-CONTRACT.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/docs/dev-sop/HANDOFF-CONTRACT.md) | 已读2.0全文；采用其字段、代码材料和接入合同。 |
| S-005 | [backend/directory-structure.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/spec/backend/directory-structure.md) | 已读分层/事务/迁移规则。 |
| S-006 | [backend/database-guidelines.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/spec/backend/database-guidelines.md) | 已读记录与日期、任务CRUD、完成一致性章节；未覆盖全文。 |
| S-007 | [backend/identity-isolation.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/spec/backend/identity-isolation.md) | 已读owner、CSRF、显式活动、实时事件和测试合同。 |
| S-008 | [frontend/directory-structure.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/spec/frontend/directory-structure.md) | 已读账号内保留页面、Toast/抽屉、分页/请求归属规则。 |
| S-009 | [WorkRecordSource.java](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/backend/src/main/java/com/aiworkbench/enums/WorkRecordSource.java) | 已读两个现有枚举值。 |
| S-010 | [TaskServiceImpl.java](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/backend/src/main/java/com/aiworkbench/service/impl/TaskServiceImpl.java) | 已读create/list/page/get/update/delete/complete/reopen/updateCompletionResult/events等可见方法；不声称审计整个服务体系。 |
| S-011 | [WorkRecordMapper.xml](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/backend/src/main/resources/mapper/WorkRecordMapper.xml) | 已读记录SQL、owner/时间/活动过滤、任务完成失效和结果更新。 |
| S-012 | [frontend/src/api/records.ts](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/api/records.ts) | 已读WorkRecord字段/来源union和现有请求函数。 |
| S-013 | [frontend/src/App.tsx](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/App.tsx) 1–170行 | 已读Workspace账号根、交互续期、跨页组合和刷新；其余行未完整核查。 |
| S-014 | [frontend/src/styles.css](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/styles.css) 请求1–180行 | 仅以实际可见的root色彩、按钮、侧栏和主要布局为依据；返回展示截断，不声称整个CSS已读。 |
| S-015 | [ReportMapper.xml](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/backend/src/main/resources/mapper/ReportMapper.xml) 1–100行 | 已读日/周候选记录与来源快照表达；未读完Mapper或报告编排/模型逻辑。 |
| S-016 | [frontend/package.json](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/package.json) | 已读声明版本与scripts；未查lockfile或安装验证。 |
| S-017 | [完成一致性prd.md](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/.trellis/tasks/archive/2026-09/09-15-d4-completion-consistency/prd.md) | 已读历史需求合同；不把勾选框或任务归档当作本轮测试结果。 |
| S-018 | `.trellis/tasks/`、`archive/2026-09/`、`backend/src/main/resources/mapper/` 目录元数据 | 仅定位相关文件；未读取个人工作日志；本地活动任务/未推送分支未知。 |
| S-019 | [frontend/src/components/layout/navigation.ts](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/components/layout/navigation.ts)，r2全文 | 当前PageId/导航四项；新增focus为本包建议修改，不是已实现事实。 |
| S-020 | [frontend/src/components/layout/AppShell.tsx](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/components/layout/AppShell.tsx)，r2全文 | 统一导航、顶栏、面包屑和正文组织；候选接入紧凑会话入口。 |
| S-021 | [frontend/src/features/records/RecordsPage.tsx](https://github.com/yibccc/ai-workbench/blob/efa1962cc99057e5a4f7ec7247768c45107ed85b/frontend/src/features/records/RecordsPage.tsx)，r2全文 | 自持date/mode/page、快速记录、RecordsList及onSaved；不将计时控制加入原记录正文。 |

**未做**：clone后检出/执行目标工程、读取本地磁盘工作区、扫描全仓库、运行数据库/Redis/模型、运行编译/E2E、修改GitHub文件/分支/PR、生成最终Trellis任务或上下文JSONL。

## 6. 事实来源与证据分类

- 用户需求/确认：当前会话原始请求、“确认”、独立页要求和“继续”，归类【用户已决定】。
- 代码/合同：上表固定快照，归类【已核实事实】，范围仅限可见内容。
- 技术建议/新UI：solution的proposed/delegated项，不是现状。
- 外部研究/浏览器文档：solution E-001～004包含作者/官方URL、日期和适用范围；r2于2026-09-26重新打开E-001/003/004；E-002访问受验证码/403限制，保留r1背景索引并标注未重新核实。本包引用的研究不支持把15秒闭眼及10分钟间隔表述为最优配方。
- 验证证据：只有 `assets/README.md` 与 `assets/prototype-checks.json` 记载的r2独立原型沙箱检查；acceptance是未来工程验证计划，不含伪造结果。

## 7. 移交步骤与停止点

1. 用户将本包放进本地收件目录；目录可采用 `.workbench/inbox/WB-20260926-focus-routines/r2/`，这是建议位置，不是本次已创建的仓库路径。保留revision，不覆盖旧包。
2. 本地代理先只读：当前AGENTS、workflow、spec、合同、工作区/HEAD、活动任务；读取本包全部文件并校验清单/摘要；代码和HTML不自动执行。
3. 对照快照列出“原样采用/局部适配/需重新决策”；不要无理由从零重做已确认方案。确认没有同ID/revision内容冲突。
4. 在用户授权创建/更新正式规划后，按本仓库原生Trellis流程建立PRD/design/implement和所需上下文；由本地生成，不拷贝网页伪元数据。保留脱敏源包到合同规定位置并记录映射。
5. 工程未知与范围冲突解决后，向用户呈现本地最终规划并等待批准；不得因本包ready或产品confirmed自动启动实施、提交、归档、推送。

## 8. 修订和摘要

| 项目 | r1 | r2（本包） |
|---|---|---|
| 页面归属 | 工作记录顶部专注卡片，完整计时用抽屉；四业务入口 | 新增独立“专注”页；原四入口保留，默认仍工作记录 |
| 工作记录正文 | 含专注汇总/启动卡片及相关入口 | 移除完整专注控件，只在原列表保留结束后投入 |
| 专注控制 | 抽屉 + 底部全局条 | 独立计时页面 + 正文外紧凑返回入口（原型放顶栏） |
| 重复规则与统计 | 分散在记录/待办入口 | 从专注页可达；原型建议页内标签，待办保留跳转入口 |
| 决策编号 | DEC-006为混排方案 | 同一DEC-006修订内容并说明替代关系；DEC-010更新视觉建议 |
| FR/AC/TC | FR-001～004、AC-001～006、TC-001～018 | 保留原编号；新增FR-005、AC-007、TC-019/020；TC-007/017同步入口描述 |
| 代码/预览 | r1 HTML与5张PNG | r2独立页HTML与6张新PNG，另有本轮局部检查日志 |
| 不变 | 重复实例、净计时、15秒引导、结算、报告、隔离、异常恢复 | 全部保留；未新增领域业务能力或工程依赖 |

现有导航与前端规范描述四页，本次用户明确允许新增专注页。本地获准实施后同步必要规范/测试，不覆盖整份现有规则，也不能继续将r1“不能增加第五页”当成约束。详见solution第8节。

`SHA256SUMS`覆盖全部主文档、代码、PNG、说明、渲染脚本及局部检查日志，除校验文件自身；ZIP摘要可另算。本包不修改r1，也没有改变本地任务状态。同ID同revision同内容复用，同revision不同内容报告冲突。r2须先比较差异，不直接替换已批准规划。
