# R3 本地接入核对

## 输入、授权与真实基线

- handoff_id：`WB-20261003-community-oss-3f9aaa`，revision：3。
- 输入：`.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3/`；不可变副本：本目录 `handoff/r3/`。
- 源元数据：repository `yibccc/ai-workbench`，snapshot branch `master`，commit `f27eda5f50cf298c5e864acc4961f5838efd4ac6`。
- 本地 remote：`git@github.com:yibccc/ai-workbench.git`；HEAD：`1a7928c2b2fa15ce0d3148af187770d85aca96f7`，branch：`codex/login-focus-record-merge`。
- 会话原生身份：`CODEX_THREAD_ID=01a0fdc4-f6c4-7363-b684-2da854f093fa`；task create 返回并激活 planning pointer：`.trellis/tasks/10-03-community-oss`。没有运行 task start。
- 用户本轮明确授权创建/更新 Trellis 规划，要求真实代码核对、原型直接复用、本地 WSL Docker RustFS，阿里云 API 尚未申请所以本轮不做；最终规划批准后持续执行，非阻塞问题最后汇总。
- `wb-handoff` 的“本轮绝不实现”与用户希望最终批准后持续执行的流程有差异：遵守用户指令，在最终门禁取得后续明确批准后可进入原生实现；不能把初始指令当尚未呈现最终规划的批准。

## 文件完整性与重复接入

本会话新增 `verify_handoff.py` 并执行退出码 0；没有执行输入包 HTML/Python/JS。完整 36 文件指纹/大小见 `package-verification.json`（包含 SHA256SUMS 自身），30 个原型文件均保留。根清单 35 条及原型清单 29 条全匹配；HANDOFF 文件清单全覆盖，无链接/越界/禁止生命周期输入，有限格式扫描无秘密命中。复制后再次逐文件比较 SHA-256，全匹配。

在 `.trellis/tasks/`（含 archive）检索相同 ID/slug，创建前无同 handoff 记录；inbox 无 R1/R2，不能宣称本地比较了不存在的历史包。R2→R3 内容变化依据源 HANDOFF：其余产品决定变为 selected，OQ-01～05关闭，布局仍 R2，不新增功能。

以下源正文残留仅记录不篡改输入：requirements、acceptance 顶部 `revision:2/draft`；PACKAGE-CHECKS 的执行路径/报告大部仍是 R2及产品待定文字；solution 有 R2版本文字与历史 OQ引用。R3 HANDOFF frontmatter、DEC-17/R3专项及各文件后续已确认 AC 更具体且一致，因此本地正式 PRD 统一为 R3。原型原有 59 条 PASS 完全不算本轮结果。

## 本地差异及并行状态

Git diff `f27eda5..HEAD`：21文件、538插入、10删除，包含登录密码眼睛、焦点/完成记录合并、相关规格/测试及归档/日志。任务不修改这批已完成语义；对今日素材直接使用当前合并展示投影，保留原始ledger给统计/报告。

初始工作区5个未跟踪用户文档：`docs/dev-sop/{REVIEW-SKILLS,SOP,SOURCES,WEB-PROMPTS,WORKBENCH-CHECKLIST}.md`。保留且不加入提交。已有 bootstrap-guidelines为别的 in_progress任务，没有切换或修改它。实际会话无 current task 才创建本需求父任务；子任务使用 `--no-start`保持父 planning pointer。

`.trellis/config.yaml` 无生效生命周期 hooks，task create只写规划/空context与session pointer，无git commit。archive/add_session有默认自动提交副作用，本轮不运行。Codex dispatch使用auto/native injection，research子代理仅写其研究文件；无需为一次静态研究额外创建channel。

## 继承与适配决定

| 来源决定 | 处理 | 本地理由 |
|---|---|---|
| DEC-01/02/04～09/11～13/16/17 | 原样采用 | R3已确认，FR/AC不重新决策 |
| DEC-03，FR-06/AC-21 | 按本轮用户指令适配 | 本轮 RustFS 私有存储和后端中转；阿里云 adapter/凭据/资源/API/实测延期 |
| DEC-10/15 | 本地技术细化 | 保持Java/MyBatis/PostgreSQL/React，发布独立领域，原私有SQL不放开 |
| DEC-14/OQ-06 | 不触发兼容处理 | 保留现行工作区hash作为现行合同，扩展同一parser的新社区地址；无旧地址映射/fallback/flag |
| OQ-07 | 局部核实，云资源延期 | 真实RustFS环境、依赖、代理/SDK/合同见storage.md；不把凭据未申请阻塞本地功能 |
| 原型CSS/页面骨架 | 直接复用后接真实数据 | 源码和17图一起作为布局基线，fixtures/演示鉴权/简易解析不进入生产 |

## FR/AC → 代码/正式规划映射

| FR | AC | 所属子任务 | 真实证据 |
|---|---|---|---|
| FR-01/02/04/07 | 01/02/04/05/06/09/11 | community-publishing | backend.md：账号、owner、PageQueries、事务与新publishing位置 |
| FR-03 | 03/04 | community-publishing + community-ui | backend.md：当前WorkRecord展示合并、时间区间；frontend.md：跨页source state |
| FR-05/06/07 | 13～21 | community-storage | storage.md：WSL/Docker、代理/multipart、存储/校验/引用授权 |
| FR-08 | 12 | community-publishing + community-ui | 无新互动领域；frontend.md界面/入口 |
| FR-09 | 07/08/09/10 | publishing + storage + ui | identity-isolation、identity-session；backend/frontend研究现行代码锚点 |
| FR-10 | 22 | community-ui | frontend.md：R2源码、17图映射、账号根和CSS复用 |

`acceptance.md` 的 TC-01～21完整覆盖 AC-01～22，所有本轮工程TC目前 NOT_RUN；TC-19 本轮只可记RustFS真实私有测试结果，不记阿里云结果。源所有边界由父PRD完整继承（AC21供应商适配除外），子任务精确分担，不重新定义另一套AC。

## 核对覆盖与限制

当前结论是包核验和真实源码/本地环境研究；研究中的文件行号与技术选择见 `backend.md`、`frontend.md`、`storage.md`。尚未执行新增迁移、安装依赖、启动RustFS、产品修改、工程测试、云操作、commit/push/部署。最终规划与context validate结果单独记录在 `planning-review.md`。
