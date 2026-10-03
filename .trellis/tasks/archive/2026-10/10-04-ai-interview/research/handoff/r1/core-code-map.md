# core-code-map — 已读代码与候选接入点

> 这是“从哪里核对”的地图，不是实现补丁。

## `ai-workbench@cf6ca832bdfffe13628489a99e80bbd3bbe9940a` 实际读取

| 路径 | 已核实用途 |
|---|---|
| `AGENTS.md` | Trellis 入口；兼容性变更需用户明确同意 |
| `.trellis/spec/backend/directory-structure.md` | Controller → Service → Impl → MyBatis Mapper |
| `.trellis/spec/backend/identity-isolation.md` | CurrentUser、owner 隔离、异步 owner |
| `.trellis/spec/backend/private-attachments.md` | RustFS 私有对象、MD 验证、恢复/清理 |
| `.trellis/spec/frontend/directory-structure.md` | RetainedView、account root、feature/api、focus 生命周期 |
| `backend/pom.xml` | Boot 3.5.16 / Java 17 / MyBatis / AgentScope |
| `backend/src/main/resources/application.yml` | 模型/Redis/storage/multipart/session 配置 |
| `backend/src/main/java/com/aiworkbench/ai/AgentScopeReportAiGateway.java` | AgentScope 模型调用与严格 JSON 参考 |
| `backend/src/main/java/com/aiworkbench/storage/ObjectStorage.java` | put/open/delete |
| `backend/src/main/java/com/aiworkbench/storage/RustFsObjectStorage.java` | RustFS adapter |
| `backend/src/main/java/com/aiworkbench/storage/AttachmentValidator.java` | 文件 staged 校验、MD 1 MiB |
| `backend/src/main/java/com/aiworkbench/storage/AttachmentValidationWorker.java` | UTF-8/控制字符验证 |
| `backend/src/main/java/com/aiworkbench/controller/AttachmentController.java` | 社区 post 绑定上传，不可直接当 resume API |
| `backend/src/main/java/com/aiworkbench/service/impl/InputServiceImpl.java` | TaskExecutor + 持久化状态参考 |
| `backend/src/main/java/com/aiworkbench/service/impl/ReportServiceImpl.java` | 后台报告处理参考 |
| `backend/src/main/java/com/aiworkbench/service/impl/ReportPersistenceServiceImpl.java` | token fencing 参考 |
| `backend/src/main/java/com/aiworkbench/config/InputRecoveryRunner.java` | 启动恢复 processing 参考 |
| `frontend/src/App.tsx` | authenticated account root / retained views |
| `frontend/src/components/layout/AppShell.tsx` | 侧栏/顶栏/focus compact |
| `frontend/src/components/layout/navigation.ts` | 当前 5 主工作区 |
| `frontend/src/components/layout/routes.ts` | hash 路由解析 |
| `frontend/src/features/auth/AccountMenu.tsx` | 当前无个人中心 |
| `frontend/src/styles.css` | 视觉系统 |
| `scripts/local/backup.ps1` | PostgreSQL 备份/隔离恢复；未覆盖 RustFS |

## `ai-interview@4341b0597466b2a9ce8a72967552c1a020878324` 实际读取

| 路径 | 可参考事实 | 限制 |
|---|---|---|
| `LICENSE` | GNU AGPL-3.0 | 许可未核对前不直接复制 |
| `.../InterviewController.java` | 会话/提交/报告 API 参考 | 无工作台 owner 合同 |
| `.../CreateInterviewRequest.java` | 3–20、简历/JD/方向字段参考 | 目标由服务端取 current resume |
| `.../InterviewSessionService.java` | 固定题单、恢复、提交、评估触发参考 | 锁定/owner/失败语义需重做 |
| `.../InterviewQuestionService.java` | 预生成追问、简历+方向出题 | 缺题成功/静默 fallback 不符合需求 |
| `.../AnswerEvaluationService.java` | 文本面试评估适配 | 目标四方向/三难度 |
| `.../UnifiedEvaluationService.java` | 主问+追问分组、批处理、汇总 | 系统失败 0 分不符合需求 |
| `.../InterviewEvaluationProperties.java` | 批次参数参考 | 目标不自动模型 retry |
| `.../InterviewSkillController.java` | JD text parse API 参考 | JD 只能 overlay 已选方向 |
| `.../InterviewSkillService.java` | Skill/JD 分类参考 | 目标栈不是 Spring AI |
| `.../interview-evaluation-system.st` | 原评分维度参考 | Java 角色写死 |
| `.../jd-parse-system.st` | JD 分类 prompt 参考 | 3–7 未冻结 |
| `.../skills/frontend/SKILL.md` | 前端方向参考 | React/Vue 混合 |
| `.../skills/ai-agent-dev/SKILL.md` | Agent 主题参考 | 动态追问假设需去掉 |

## 候选接入路径（未实现）

### 前端候选
- `frontend/src/features/interview/`
- `frontend/src/features/profile/`
- `frontend/src/api/interview.ts`
- `frontend/src/api/resume.ts`

P-01 未批准前，不固定 `navigation.ts` / `routes.ts` / 个人中心最终入口。

### 后端候选
按现有结构可考虑：
- `controller/*Interview*`, `controller/*Resume*`
- `service/*Interview*`, `service/impl/*Interview*`
- `service/*Resume*`, `service/impl/*Resume*`
- `mapper/*Interview*`, `mapper/*Resume*`
- `dto/interview/`, `dto/resume/`
- `entity/interview/`, `entity/resume/`
- `ai/*Interview*Gateway`
- `resources/mapper/*Interview*.xml`
- `resources/db/migration/V*__*.sql`

均为候选，不代表文件已存在或命名已批准。

## 原型核心代码

`prototype/ai-interview-prototype.html` 是本轮 UI 原型的完整 HTML/CSS/JS 核心代码，只用于预览，不是生产 React 实现。

静态图：
- `prototype/previews/history.png`
- `prototype/previews/create.png`
- `prototype/previews/answer.png`
- `prototype/previews/report.png`
- `prototype/previews/resume.png`
- `prototype/previews/delete.png`

## 开工前红线

- 未确认 P-01 前，不把原型导航结构当需求事实。
- 未解决 AGPL 许可前，不直接复制源仓库 Prompt/Skill/Java。
- 不覆盖 `AGENTS.md` 或现有 `.trellis/spec`。
- 不生成网页侧 task.json / implement.jsonl / check.jsonl。
- 不把 AC 当测试结果。
