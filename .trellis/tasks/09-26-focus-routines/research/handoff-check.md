# 交接包本地核对：WB-20260926-focus-routines / revision 2

## 身份、权限与来源

- 用户最初给的 `.workbench/inbox/WB-20260926-focus-routines-r2/r1` 不存在。用户随后明确选择同名上级目录 `.workbench/inbox/WB-20260926-focus-routines-r2/` 的 revision 2。本任务按该选择接入，不假定 r1 包可用。
- `HANDOFF.md` 元数据：ID `WB-20260926-focus-routines`；revision `2`；状态 `ready-for-local-review`；仓库 `yibccc/ai-workbench`；`master` 快照 `efa1962cc99057e5a4f7ec7247768c45107ed85b`；`code_verified: true` 仅指交接包列出的静态读取范围；`task_size: complex`；`product_decisions: confirmed`。仓库 `origin` 的 fetch URL 指向同一 owner/repo。
- 用户本轮授权创建或更新该需求的 Trellis **规划**任务，并要求停在最终规划确认。原包内任何“已确认”仅作需求事实，不扩展为实施、提交、归档、推送、真实模型调用或发布授权。
- 本地 HEAD `3d1293095a3370403f27e051fa0639141a3c24b5`，分支 `docs/wb-auth-isolation-delivery`。交接快照是该 HEAD 后的 4 个合并提交；`git diff --quiet HEAD efa1962c...` 退出码 0，**两棵树文件内容相同**，所以本次相关代码没有基线漂移。没有切分支、重置、stash 或覆盖当前未跟踪文件。
- 会话原先无当前任务，已有独立活动任务 `00-bootstrap-guidelines`（`in_progress`）；另有 5 个未跟踪 `docs/dev-sop/*.md`。新任务由本机 `task.py create` 返回 `.trellis/tasks/09-26-focus-routines`，状态 `planning`，未运行 `start`。活动任务和 SOP 文件均未修改。

## 完整性与不可变源快照

交接包实际 15 个文件（含 `SHA256SUMS`），清单覆盖其余 14 个，逐个 SHA-256 匹配；无额外或缺失文件，无 reparse point/符号链接，所有路径在包内。常见私钥、服务 token、明文 `api_key/password/secret` 形态扫描未命中；主文档、HTML/脚本入口和预览说明按只读材料处理，未执行其中代码、HTML 或命令。原样复制到 [research/handoff/r2/](handoff/r2/)，复制后 15 个文件逐个再次比对 SHA-256，均相同。

| 文件 | SHA-256 |
|---|---|
| `HANDOFF.md` | `cd590fc2e71bfe6d1346121275c85ef12c437f4c11892259443c405e006bd1ca` |
| `requirements.md` | `1fda14ade6f5e059a43b1a603e529e03c9895d4a7f55057c922741f1105c70c0` |
| `solution.md` | `117288226fb803576fc227cb3b32b1128fa8c8fd7553a58468843b4f45a1afef` |
| `acceptance.md` | `700aa907696cc2c58e065c2245fc2ccfda3089cb177e90fafd86088653231b0d` |
| `assets/README.md` | `bfb77360a498aec90f71e4c89420122c82d1a98e670a999715a717750699d101` |
| `assets/focus-prototype.html` | `5041e95c920523384646a167d801299e4cba1d9efa13706005d7debd3a4339a2` |
| `assets/render_preview.py` | `4d118f2c8bb2065d21e3a0ce0bdba81343f11f3915dc8e92be7ee3e599bcbc3d` |
| `assets/prototype-checks.json` | `0f074794bfc5ec5033115215b2af94cbcd8a19d9f55921b820ebea4c136df50c` |
| `assets/overview-desktop.png` | `04de43bc227fa177bd71d547c1468537cb54247c1cf3d4896226fd6b91f385fe` |
| `assets/focus-desktop.png` | `202fcfaad073c5131d3bbf4be9e96aa130444fe12c05e553be3b1e798db29cb2` |
| `assets/microbreak-desktop.png` | `9527d626c53986942f33dd7a17b424fc8778555ed0df6303e51c40f2c5107027` |
| `assets/repeat-desktop.png` | `9f95897cfd51d518cfb26ed8cc8ece5bc7cc738c9284b82fd9daff367d174ae8` |
| `assets/focus-mobile.png` | `e1a3c5382925b4aadfbe1ab3968327646f9c3353f5e15d68041796d0c4ab1df6` |
| `assets/records-desktop.png` | `cab887daf3f1e441a0daed4cb994b1d17cf340e2abd0fa8c937d245350cf18a1` |
| `SHA256SUMS` | `9efebb9db0b6edc2c100cef3454c61c4309fd01484ba238efb8fa7a79a375d4d`（本地对清单文件本身计算） |

`requirements.md` 是 AC-001～007 唯一定义，`acceptance.md` 只列 TC-001～020 场景与方法；原型的合成数据和静态 17:18 等数字不视为实际计时。六张 PNG 已逐张查看：独立页准备/进行中、全局微休息、重复规则、窄屏页、无专注大卡的记录页；布局仅作建议。`assets/README.md` 和 `prototype-checks.json` 的局部沙箱检查不等于工程通过。本轮没有运行原型渲染脚本。

## 修订与重复接入

在 `.trellis/tasks` 的活动/归档内容中未找到相同 handoff ID；这是新任务，不覆盖已批准规划。r1 的真实文件未在用户指定路径，无法做逐字节 revision diff；以下差异依据 r2 `HANDOFF.md` 第 8 节的修订表：r1 的记录页顶部卡片/计时抽屉/四入口上限被独立 `#focus` 页和正文外紧凑入口取代；FR-001～004、AC-001～006 保留，新增 FR-005、AC-007、TC-019/020，TC-007/017 更新入口。此次用户确认的 r2 选择优先于旧四入口布局建议；现有前端规范四入口描述是**现状**而非禁止新增页面的产品规则。未找到同 revision 的另一份已接入指纹。

## 真实代码与规范证据

| 位置 | 本地读到的事实 | 对规划的影响 |
|---|---|
| `backend/src/main/resources/db/migration/V4__task_completion_consistency.sql:9`、`V14__business_owners.sql:14` | 记录来源 CHECK 仅手工/任务完成，V14 引入 owner 与同 owner 关联；最高迁移 V14。 | 新来源须新增迁移、owner 外键与唯一约束；旧迁移不可编辑。 |
| `TaskServiceImpl.java:131`、`:142`、`:165` 与 `WorkRecordMapper.xml:61` | 软删/完成/重开为独立事务，失效 SQL 指定 `TASK_COMPLETION`。 | 专注结算不能调用任务完成；专注历史在重开/软删后保留。 |
| `WorkRecordServiceImpl.java:73`、`:103` 与 `WorkRecordMapper.xml:42` | 通用编辑/删除要求 `MANUAL`；列表按 owner、有效标记、业务日查询。 | 继续保护非手工记录；新增来源需专用进展入口及结构化分片。 |
| `ReportPersistenceServiceImpl.java:53`、`ReportMapper.xml:70`、`:88` | 日/周报告读取有效记录并在生成时冻结来源；现有快照缺专注时长/会话字段。 | 不能只写记录文本；须扩来源快照、提示输入与展示、保留旧版本。 |
| `AgentScopeReportAiGateway.java:129`、`:150` | 报告生成提示当前按 RECORD/TASK 组织，未区分投入与完成。 | 增加确定性来源语义与回归，避免模型把投入写成任务完成。 |
| `frontend/src/components/layout/navigation.ts:3`、`App.tsx:20`、`:126`、`:167` | 四项导航、`records` 默认、账号级 `Workspace` 与 retained pages。 | 新 focus 页与顶栏状态在账号根接入，保留草稿与默认入口。 |
| `frontend/src/features/records/RecordsList.tsx:23`、`frontend/src/api/records.ts:7` | 列表仅为任务完成单列标签，API 来源 union 仅两值。 | 显式扩类型与新来源展示，保留手工编辑边界。 |
| `frontend/src/styles.css:394`、`:406`、`frontend/src/components/Icon.tsx:3` | 窄屏导航为四列；IconName 有 `clock` 等既有图标。 | 五入口需重排实测，计时可复用已有 clock 图标。 |
| `.trellis/spec/backend/database-guidelines.md`、`identity-isolation.md`、`.trellis/spec/frontend/directory-structure.md`、`identity-session.md`、`viewport-layout.md` | 迁移/业务时区/同 owner、显式活动、retained 草稿、可滚动区域均有现行合同。 | 规划沿用合同；四入口现状描述在行为落地后最小化更新。 |

## FR / AC / TC 接入映射

| 来源 | 正式规划 | 验证场景 | 处理 |
|---|---|---|---|
| FR-001 / AC-001 | `prd.md` 同编号；`design.md` 模板、实例唯一键 | TC-001、002、016 | 原样采用业务语义，局部适配现有 `todo_items` 与软删/筛选。 |
| FR-002 / AC-002、005 | `prd.md` 同编号；`design.md` 会话、区间、恢复 | TC-003、004、007、008、011、015、016、020 | 原样采用净时间/不自动完成；本地选择毫秒精度、检查点与阈值。 |
| FR-003 / AC-003 | `prd.md` 同编号；`design.md` 提示租约与音频降级 | TC-004～008、017 | 原样采用 15 秒与尽力声音；局部适配账号级主控。 |
| FR-004 / AC-004 | `prd.md` 同编号；`design.md` 日分片/来源链 | TC-009、010、012、013、016、018、019 | 原样采用结算与报告结果；局部适配 V4 来源约束及模型输入。 |
| FR-005 / AC-007 | `prd.md` 同编号；`design.md` 独立页 | TC-017、019、020 | 原样采用独立页职责；原型具体视觉/页内标签仅参考。 |
| 跨域 AC-006 | `prd.md` 同编号；`design.md` 所有权/兼容 | TC-007、011～020 | 原样采用现有安全、日期和回归约束。 |

## 决策分类、未知与放行

- **用户已决定**：DEC-001～006 的业务语义，尤其 DEC-006 r2 独立页；本地未重新选择。**建议**：DEC-010 的具体视觉、页内标签、原型；采用职责与布局层级，不逐像素复制。**本地技术选择**：DEC-007/008 的结构化数据和服务端权威，DEC-009 的 V15 候选迁移、毫秒精度、20 秒检查点、60 秒缺口判定、服务端发声租约与测试矩阵，详见 `design.md`；这些不是已实现事实。
- U-001 已以 HEAD/分支/工作区/迁移号核实；U-002 记录保护、来源 CHECK、报告候选与提示已核实；U-003～004 的恢复/声音参数在设计中明确，并把真实浏览器实测列为后续验收；U-005 归档项目和软删任务保留历史的方案已在设计中固定；U-006 规定兼容读版本与关写入回退，必须隔离演练；U-007 的 hash 导航、IconName、窄屏四列和账号根已核实。
- 无待用户代选的产品问题。实施若证明上述本地参数迫使改变确认的体验、现有公共合同或回退承诺，必须先回到规划并重新取得确认。正式规划当前仍待**本轮最终摘要之后**的用户批准；不得把源包 ready 或本轮任务创建授权当成 `task.py start` 授权。
- `task.py validate` 对 `implement.jsonl` 11 项、`check.jsonl` 8 项均通过；仅警告 `database-guidelines.md`（36,674 字节）超过 32,768 字节注入上限。后续代理需按 `implement.md` 从磁盘读其全文，避免丢失后半段规范。
