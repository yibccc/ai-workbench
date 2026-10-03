# AI 面试 UI 执行计划

## 执行前置与顺序

本文件是正式执行计划，当前任务保持 planning。P-01、父完整规划与 resume → engine → ui 连续执行需最终真实批准；未批准不实施、不运行原型、不调用模型、不提交/推送/发布。全部 FR-001–026/INT-AC-001–026 继承父合同；一般问题记录父 `research/issues.md` 并在交付末尾汇总，涉及新增范围/具体兼容处理/外部不可逆行动遵守父授权边界。

先读取适用 `.trellis/spec/frontend` active规范及父 design，运行阶段上下文/检查流程。子任务默认由 Trellis implement/check 代理实施和独立复核，主代理协调合同、规范、批准、整合和收尾。产品文件与检查文件分配明确，所有代理保留其他人改动。

本子任务依赖简历子任务 current/version/multipart/original稳定且独立验收，再依赖 engine子任务 JD/场次/轮次/报告/删除/retry合同稳定且独立验收。批准后可以并行做原型JSX/CSS骨架和共享组件接入；真API联调按 resume → engine → ui推进，不把临时fixture当作后端已通过。

## 计划步骤与交付检查点

### 1. 原型内容迁移与原生入口

- 从父不可变 R1副本迁入内容HTML为JSX/feature CSS；使用研究中行号映射保留原型结构、色值、间距/断点，删除shell/全局reset/demo tabs/hash render/onclick/样例账号计时数据。
- P-01批准后更新 navigation、strict routes/title、AccountMenu、AppShell透传和 App retained树；interview第六主导航，profile仅账号菜单辅助入口，不增旧原型route alias。
- 保留唯一focuscontroller/account-key/五原页；共享guard类型/注册仅调度当前活动编辑上下文，复用Dialog、Toast、Pagination、request、安全Markdown和beforeunload。
- Scoped `.interview-workspace`/`.profile-workspace` CSS补齐原生高度链，删除1180cap，固定页头、有界正文/rows/timeline/textarea、pager正常footer；六项窄屏导航主体仍正高度。

检查点：生产引用不依赖handoff/task路径；无未批准兼容分支/featureflag；原生shell及安全路由真正工作，骨架数据不得包含公开fixture或假业务完成状态。对应INT-AC-008/010及几何基础。

### 2. 私有当前简历界面

- `api/resume.ts` 采用后端正式DTO/共享request；current生效baseline和local text/File/revision分离。
- 只在Profile导入UTF-8.md；校验扩展、1MiB字节、fataldecode、20k码点；File先入编辑器，可改文本后显式 `POST multipart {file,markdownText,expectedVersion,requestId}`，原件不重新编码成编辑文本。
- PUT明确必填mode=EDIT_CURRENT|PASTE，无旧版默认/兼容。普通编辑当前用EDIT_CURRENT与expectedVersion保既有来源/原件；用户显式粘贴替换才PASTE，提示解除旧原件关联。新File走import，后续编辑用EDIT_CURRENT不重复上传；PASTE不合成对象。成功按sourceKind刷新来源/下载入口，current GET/ACK不覆盖新版编辑；保存未知保留tuple/requestId按合同恢复，409显式读取/用户重试。
- 实现放弃/删除Dialog、dirty导航、失败输入保留、无current通用面试、owner原件Blob下载生命周期；不宣称物理清理或备份已删。

检查点：INT-AC-001/009/010/011/012/013/014/020/022/023 的Browser分工；真实HTTP/DB/RustFS链与存储后端证据分别记录。

### 3. 创建与 JD

- 四方向/三档难度、默认中级、完整3–20默认5、2N动态文案、current确认版本/none；创建无临时上传/粘贴简历。
- JD仅文本+显式parse202；实现owner GET状态、explicit retry(requestId/expectedVersion)及DELETE分析；任何字符/方向变更立即使旧分析不可创建并DELETE原分析，迟到旧结果丢弃；取消创建也清原分析。失败不静默降级，明确移除才none。
- 固定create tuple/requestId同步防重；创建copy快照后保留当前form配置与可用analysis，同owner+direction+rawhash可重复建多场，无consume或强制重解析。任一字符/方向修改、明确移除/确认取消才DELETE分析，不影响已建各场copy。同ID重放优先receipt；未知响应先同POST重放及owner GET，不猜恢复GET或新requestId盲建；显示generation/answer/evaluation正式分轴状态与手动retry/完整2N成功状态。

检查点：INT-AC-002/009/014/015/016/017/018/019/023/024/025/026对应UI与countingfakegateway证据；所有DTO来自已验收engine合同。

### 4. 多场作答、交卷和恢复

- 单场选中/轮次请求ownership，至少两场独立草稿；渲染2N及MAIN/FOLLOW_UP/parent和40轮timeline。
- PUT draft仅更新savedbaseline、不推进不锁定，空草稿合法；保存并离开失败保留上下文。submit固定turn/version/text/requestId，不加非空/trim门禁，空字符串成功也按SUBMITTED锁定/推进；成功GET权威后更新。丢ACK按submitted状态读确认/旧tuple重放，双页409不覆盖已锁定答案。
- beforeunload/活动guard保护dirty；提前交卷Dialog按submitted状态显示已答/未答/未答0（空提交计已答，有未提交草稿仍未答），取消零写；最后轮/提前交卷成功冻结，首次评估仅后端触发。
- 多场继续/刷新/重登恢复纯GET，不自动选旧场新建；从任何阶段owner删除并清投影，不让迟到ACK复活；成功写后刷新失败单独反馈。

检查点：INT-AC-003/004/005/008/009/016/021/023/024/025/026；真实API+DB恢复/并发共证与可控lostACKfixture清楚分开。

### 5. 真实报告、失败恢复与身份清理

- 显示完整逐轮题/答/有效分数/反馈、可选参考点与综合反馈；未提交/未答0明确标记，已提交空内容仍需有效模型评估（可真实0+具体反馈），系统未评估无分，必要结果不完整无总分，总分只后端。
- 显示评估等待/处理中/失败/成功/部分有效分组；只有explicit retry发模型操作，历史GET/轮询无新调用，冻结答卷不重开。
- A→B/真实401结束File/localdraft/pendingtuple/轮询/迟到响应，403/404/网络/409区别；sharedToast visible root与5s行为不变；后台GET不signalActivity。

检查点：INT-AC-001/006/007/010/012/015/016/021/024/025；countingfakegateway证明纯读取不调用模型，后端验证总分/映射/fencing。

### 6. 独立检查、浏览器验收与规范回流

- 由独立check代理按父FR/INT-AC检查scopeCSS、状态/请求ownership、File与保存文本绑定、全码点/不截断、pending/未知ACK/版本冲突/owner切换；自修复lint/strictType/build失败并记录实际结果。
- 新增 `frontend/e2e/interview.spec.ts` 的真实业务、受控错误及视觉/几何测试；保留原workbench/focus-alarm/community套件，已受影响导航五项断言在P-01批准后改六项，同时保留五页原行为/专注几何断言。
- 获批实现后更新适用directory/identity/viewport/community的五项表述及新增sharedguard合同；规范可按现有英文要求写，任务规划为中文，批准前不把P-01写成已实现。
- 回填每项INT-AC证据与层级、截图/运行命令/实际数量；未跑/失败/延期明确列出，问题统一送父 `research/issues.md`；常规工程验收不混入付费真实模型或阿里云验证。

## 验证命令与环境安全边界

以下是真实已有入口，执行后记录实际输出；本规划阶段尚未运行。

```powershell
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix frontend run e2e -- e2e/interview.spec.ts
npm --prefix frontend run e2e -- e2e/workbench.spec.ts e2e/focus-alarm.spec.ts e2e/community.spec.ts
```

build包含 `tsc -b && vite build`，不添加空typecheck脚本或通过放松TS/ESLint满足检查。E2E只用已验证隔离环境：显式loopback `E2E_DATABASE_URL`，非5432端口，数据库非ai_workbench且currentSchema=d9_e2e；Redis非6379；必须配置隔离PG的POSTGRES_USER/PASSWORD并使用合成账号，文档/证据不写密钥。普通后端隔离测试schema仍遵守 `d9_*_tests_YYYYMMDD` guard，不能修改或绕过现有reset保护。Vite子进程不注入DB/model/storage/root/AWS凭据。测试级retry不授权产品自动模型retry。

既有local-acceptance只读smoke与browser-env-check不能替代面试业务验收。真实模型最大输入质量、费用/延迟、OSS适配/云账号/API/真实迁移验收延期，由父任务统一列出；隔离工程验收使用deterministic/controlled gateway且计数验证读操作零新增调用。

## 证据要求与完成判定

1. 真实业务Browser至少完成“Profile File导入后修改再保存→当前A建场1→改B建场2→两场独立暂存/提交→刷新/重登恢复→current删除保留旧快照→任一阶段场次删除不影响其他”链，记录HTTP/DB/RustFS共证与无公网对象URL。
2. 可控错误覆盖JD失败/不匹配/迟到/任一字符与方向失效，create/submit/save未知ACK，双标签页409，owner迟到/401/404，提前交卷取消/成功，评估缺结果无总分，任务中断/explicit retry/删除迟到。每个fixture标明只证明UI行为，后台所有权/耐久/fencing需相应服务端证据。
3. 输入边界逐项20k/+1、10k/+1、5k/+1，含emoji码点；空简历正文、空草稿保存和空文本submit无额外非空门禁，空submit需有效评估而非UNANSWERED。导入原件正文和最终正文各20k校验，file validator的1MiB/+1字节门禁与完整API正文门禁分开验证，不声称1MiB合法UTF-8正文可同时满足20k。超限不截断/覆盖/推进；20主问题完整40轮并最大允许输入完整可达。
4. 原型六屏及遗漏状态在320×520、390×520、760×700、1440×900截图/几何：document不纵横溢出，scrollowner正高度，长内容与末尾按钮/pager可达，六导航/账号/专注/旧五页状态保留；不降低原几何标准。
5. lint/build、独立面试E2E、受影响旧回归都真实通过；INT-AC-001–026对应前端职责与后端共证无空缺，延期不写PASS。完成检查后主代理处理父整合/批准边界/规范/收尾，不自行提交、推送、发布或更新lifecycle。
