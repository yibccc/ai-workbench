# HANDOFF — AI Workbench AI 面试整合

## 元数据

- `handoff_id`: `AIW-INTERVIEW-20261003-cf4cf8fc`
- `revision`: `1`
- `task_size`: `complex`
- 项目：`ai-workbench`
- 包状态：`draft`
- 交付状态：`ready-for-local-review`
- 冻结日期：`2026-10-03`（Asia/Taipei）
- 已开始正式实现：`false`
- 已运行测试：`false`
- 已生成 Trellis `task.json` / `implement.jsonl` / `check.jsonl`：`false`

> `draft` 的原因：R1 原型提出“AI 面试作为第 6 个主工作区、个人中心仅从账号菜单进入”，该信息架构尚未得到用户明确批准。  
> `ready-for-local-review` 只表示本交接包可以交给本地代理核对；**不表示批准编码、PR、迁移或发布**。

## 合同来源

仓库快照根目录未找到 `HANDOFF-CONTRACT.md`，因此本包遵守用户在本轮冻结请求中给出的交接合同。未虚构额外字段语义。

## 仓库快照

| 仓库 | 分支 | 实际读取的完整 commit | 用途 |
|---|---|---|---|
| `yibccc/ai-workbench` | `master` | `cf6ca832bdfffe13628489a99e80bbd3bbe9940a` | 目标项目、现有账号/隔离、RustFS、前后端结构、异步模式、备份与 UI 约束 |
| `yibccc/ai-interview` | `main` | `4341b0597466b2a9ce8a72967552c1a020878324` | 文字面试/JD/Skill/评估的参考实现 |

本包没有读取本地未提交修改，也没有声称上述 commit 与本地工作区当前 HEAD 一致。开始本地工作前必须重新核对。

## 用户已确认的范围与选择

以下均为本轮对话中用户明确确认的决定：

1. 首版只做**文字面试 + 预生成追问 + 评估 + 历史记录**；不做语音面试和知识库面试。
2. 每个用户只有一份**当前简历**，在个人中心维护。
3. 当前简历支持**导入 `.md` 文件 + 粘贴 Markdown**；简历不支持 PDF/Word 等其他格式。
4. 新建带简历面试只能使用个人中心的当前简历；仍允许**无简历通用面试**。
5. 创建面试时保存当时的简历**文本快照**；后续更新当前简历只影响新面试，旧面试（包括未完成、评估重试、历史报告）继续使用旧快照。
6. 作答采用**逐题提交，提交成功后不可回改**；提交前可修改/暂存。
7. 每道主问题固定 **1 道预生成追问**；不做回答驱动的动态追问。
8. 主问题默认 **5 道**，可选 **3–20 道**；总作答轮数固定为 `2N`。
9. 预设方向只保留：**Java 后端、React 前端、Agent 开发、全栈**。
10. 可选 JD 只支持**粘贴文本**；JD 只补充已选方向内的侧重点，不新增“任意岗位定制”第五方向。
11. 难度为**初级 / 中级 / 高级**，默认中级；出题和评分使用同一难度。
12. 提前交卷采用**整场计分**：未作答轮次按 0 计入总分，并明确标记“未作答”。
13. **模型评估失败不算 0 分**；必要评估结果不完整时不发布完整总分。
14. 首次正常交卷自动触发评估；JD 解析、出题、评估失败后**只允许用户手动重试**，不自动追加模型调用。
15. 允许**多场未完成面试**；“新建”和“继续”明确分开，各场独立保存快照、JD、题单、草稿与进度。
16. 当前简历、面试均由本人手动删除；**无回收站、无按天自动删除**。
17. 替换当前简历成功后，旧原文件在无有效引用时清理；历史面试的简历文本快照随对应面试保留。
18. 删除一场面试时删除该场题单、答案、JD 原文与解析结果、评估报告、简历文本快照；不影响当前简历或其他面试；后台迟到结果不得让它重新出现。
19. 输入上限：
   - 当前简历 Markdown 正文：`20,000` Unicode 码点；
   - JD：`10,000` Unicode 码点；
   - 每轮答案：`5,000` Unicode 码点；
   - 导入 `.md` 文件：`1 MiB`，且正文仍需满足 `20,000` 字符限制。
20. 超限不静默截断：保留输入、明确提示，不替换当前简历、不提交答案、不推进轮次。
21. 采用**工作台原生集成**基线：不把 `ai-interview` 作为独立后端服务接入。

## 尚未确认的产品决定

### P-01 — R1 信息架构（必须在实现对应 UI 前确认）

R1 原型建议：
- 在现有工作台主导航中把“AI 面试”作为第 6 个主工作区；
- “个人中心”只从账号菜单进入，不作为主导航项。

该决定**尚未得到用户明确批准**。原型保留用于评审，但不得在 requirements/solution 中写成已确认行为。

## 非产品但必须本地核对的事项

1. **许可**：`ai-interview` 的 `LICENSE` 为 GNU AGPL-3.0；`ai-workbench` 在上述快照根目录未找到 `LICENSE`。在直接复制 `ai-interview` 源码、Prompt、Skill 或 reference 资源前，必须先完成许可适用范围和发布义务核对。本交接包不构成法律意见。
2. **模型容量与质量**：20 主问题 / 40 轮、最大允许输入组合的上下文容量、延迟、成本和质量尚未验证。
3. **任务参数**：面试任务的 timeout、lease/处理权期限、`TaskExecutor` 并发度尚未确定。
4. **RustFS 权限**：当前应用身份是否允许新的简历对象前缀尚未核验；不得直接扩大为全桶/管理权限。
5. **备份**：现有本地 `backup.ps1` 只实际备份 PostgreSQL；未见 RustFS 对象备份。简历原文件恢复策略需本地补充核对。
6. **全栈方向资源**：参考仓库未见独立 full-stack Skill；需要本地设计，不得假装已有可直接迁移模板。
7. **React 方向资源**：参考 `frontend` Skill 同时覆盖 React/Vue；首版 React 方向必须收窄，不能原样复制后仍生成 Vue 题。
8. **JD 解析条目数量**：参考实现提示词使用 3–7 个考察方向，但用户没有把“3–7”确认成产品合同；本地可作为实现候选值，不得提升为用户需求。

## 文件清单

- `HANDOFF.md`
- `requirements.md`
- `solution.md`
- `acceptance.md`
- `core-code-map.md`
- `MANIFEST.txt`
- `prototype/ai-interview-prototype.html`
- `prototype/design-notes.md`
- `prototype/previews/history.png`
- `prototype/previews/create.png`
- `prototype/previews/answer.png`
- `prototype/previews/report.png`
- `prototype/previews/resume.png`
- `prototype/previews/delete.png`

## 事实来源（实际读取）

### `ai-workbench@cf6ca832bdfffe13628489a99e80bbd3bbe9940a`

- `README.md`
- `AGENTS.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/identity-isolation.md`
- `.trellis/spec/backend/private-attachments.md`
- `.trellis/spec/backend/community-publication.md`
- `.trellis/spec/backend/local-delivery.md`
- `.trellis/spec/frontend/directory-structure.md`
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/aiworkbench/ai/AgentScopeReportAiGateway.java`
- `backend/src/main/java/com/aiworkbench/storage/ObjectStorage.java`
- `backend/src/main/java/com/aiworkbench/storage/RustFsObjectStorage.java`
- `backend/src/main/java/com/aiworkbench/storage/AttachmentValidator.java`
- `backend/src/main/java/com/aiworkbench/storage/AttachmentValidationWorker.java`
- `backend/src/main/java/com/aiworkbench/controller/AttachmentController.java`
- `backend/src/main/java/com/aiworkbench/service/impl/InputServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/ReportServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/ReportPersistenceServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/config/InputRecoveryRunner.java`
- `frontend/src/App.tsx`
- `frontend/src/components/layout/AppShell.tsx`
- `frontend/src/components/layout/navigation.ts`
- `frontend/src/components/layout/routes.ts`
- `frontend/src/features/auth/AccountMenu.tsx`
- `frontend/src/styles.css`
- `frontend/package.json`
- `scripts/local/backup.ps1`

### `ai-interview@4341b0597466b2a9ce8a72967552c1a020878324`

- `README.md`
- `AGENTS.md`
- `LICENSE`
- `app/build.gradle`
- `gradle/libs.versions.toml`
- `app/src/main/java/interview/guide/modules/interview/InterviewController.java`
- `app/src/main/java/interview/guide/modules/interview/model/CreateInterviewRequest.java`
- `app/src/main/java/interview/guide/modules/interview/repository/InterviewSessionRepository.java`
- `app/src/main/java/interview/guide/modules/interview/service/InterviewSessionService.java`
- `app/src/main/java/interview/guide/modules/interview/service/InterviewQuestionService.java`
- `app/src/main/java/interview/guide/modules/interview/service/InterviewQuestionProperties.java`
- `app/src/main/java/interview/guide/modules/interview/service/AnswerEvaluationService.java`
- `app/src/main/java/interview/guide/common/evaluation/UnifiedEvaluationService.java`
- `app/src/main/java/interview/guide/common/evaluation/InterviewEvaluationProperties.java`
- `app/src/main/java/interview/guide/modules/interview/skill/InterviewSkillController.java`
- `app/src/main/java/interview/guide/modules/interview/skill/InterviewSkillService.java`
- `app/src/main/resources/prompts/interview-evaluation-system.st`
- `app/src/main/resources/prompts/jd-parse-system.st`
- `app/src/main/resources/skills/frontend/SKILL.md`
- `app/src/main/resources/skills/ai-agent-dev/SKILL.md`
- `app/src/main/resources/skills/algorithm/SKILL.md`
- `app/src/main/resources/skills/ali-backend/SKILL.md`
- `app/src/main/resources/skills/`

## 交接步骤

1. **先核对本地仓库**：记录本地 branch + full commit；若与本快照不同，先比较影响范围，不得把网页结论当作当前代码事实。
2. 阅读本地 `AGENTS.md`、`.trellis/workflow.md`、相关 `.trellis/spec/`；遵守“兼容性变更须用户明确同意”。
3. 在任何直接复制 `ai-interview` 源码/Prompt/Skill 前先解决 AGPL 许可问题；如未解决，按已确认行为独立实现，不直接复制受影响资源。
4. 与用户确认 P-01 信息架构后，才固定导航和个人中心入口。
5. 根据 `solution.md` 核对现有模型网关、异步处理、RustFS 权限、Flyway/MyBatis 结构，建立正式本地设计。
6. 本地 Trellis 再创建正式 PRD、设计和执行计划；**不要从本包生成或覆盖 `AGENTS.md`**。
7. 按 `acceptance.md` 建测试计划。测试计划不是测试结果，实际结果只能由本地执行后记录。
8. 用户批准本地方案并明确开工后，方可实现。

## 自检结论

- 未确认的产品决定：**有 1 项（P-01）**，已标记为 draft。
- AC：均写成可观察、可判定通过/不通过的行为。
- 文件一致性：本包将相同的题量、方向、简历/JD、评分、重试、删除和长度规则作为唯一合同。
- 路径/commit：只引用实际读取过的路径或在 `solution.md` 中明确标记的候选路径。
- 测试：**没有声称执行任何测试**。
- 实现：**没有声称创建代码补丁、commit、PR 或正式 Trellis 任务**。
