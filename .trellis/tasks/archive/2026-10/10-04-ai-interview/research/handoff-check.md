# 交接包本地核对

## 身份、版本与输入核验

- ID：AIW-INTERVIEW-20261003-cf4cf8fc；revision 1；source draft，ready-for-local-review是交付状态，二者不等价于批准。
- 目标origin：git@github.com:yibccc/ai-workbench.git；本地branch codex/community-oss；HEAD 0090d0127b0693c780aa3e587e20a6e6bae969fc。
- source目标snapshot：master@cf6ca832bdfffe13628489a99e80bbd3bbe9940a；本地非shallow，但对象不可用，git diff退出128/bad object。没有fetch、没有伪造完整diff；使用源core-code-map与当前真实文件逐项核对影响。
- 参考源本机 E:/projects/interview-guide：origin https://github.com/yibccc/ai-interview.git，HEAD 4341b0597466b2a9ce8a72967552c1a020878324，与交接一致；有未跟踪.workbuddy/，只读未改。
- MANIFEST与实际14文件完全匹配；所有路径留在指定源目录；未见ReparsePoint/符号链接；按已执行secret regex范围没有匹配，非全能秘密认证。HTML无远程脚本引用/链接执行；未运行任何包中HTML/脚本或安装依赖。
- 已逐文件复制到research/handoff/r1；源/副本SHA-256均一致；明细handoff-fingerprints.json。
- 包指纹算法：按file排序拼接“file 空格 sha256 换行”的UTF-8再SHA-256；结果 a80366e11865c2243d0a63dfc610bab20411457ffc26d1a179052e05176eac46。
- 同ID/revision在active/archive的task.json/HANDOFF/handoff-check无匹配；本轮由task.py create实际返回父与三子路径，不重建归档任务。
- 本机身份kira；当前chat真实ID 01a10293-1da5-7121-80ed-85fc1c66014d由get_goal确认；用TRELLIS_CONTEXT_ID=codex:<实际ID>恢复会话，create自动设置planning pointer，没有task.py start。
- 独立既有00-bootstrap-guidelines in_progress不变；最初git status六项：删除.cursor/rules/no-unsolicited-compatibility.mdc与五份docs/dev-sop未跟踪文件，本轮不接管/提交。

## 原生流程与副作用

已读AGENTS、workflow、wb-handoff、trellis-start、trellis-brainstorm、HANDOFF-CONTRACT、相关平台config/agent与规范。create非空title/description/无日期slug，children--no-start保父pointer。config只有注释hook示例，没有启用after_create外部/提交动作；create代码检查后执行。archive/add_session默认可auto-commit，本轮不调用。Trellis-channel索引已查看，但本工作是静态研究，使用原生子代理，不启动channel或自日志替代工具。

源包未使用当前合同标准元数据标量，而以仓库快照表给目标/参考repository/commit；本地明确规范化记录，不篡改源。源draft仅因P-01；所有已确认21项决定原样继承。当前规范不等同于source网页未找到合同的旧快照，以本地为权威。

## 代码事实与差异影响

| 网页判断 | 当前真实核验与处理 |
|---|---|
| 五工作区、账号菜单无个人中心 | 当前仍五retained主工作区，但已有community公开作者主页/昵称设置；个人中心应私有独立profile，不能复用公开profile DTO。App.tsx/routes/navigation/AccountMenu证据见frontend-reuse |
| 源原型是可运行界面 | HTML只有静态screen与hash切屏，业务不是生产实现；直接复用content/CSS，不复制全局shell/mock脚本。HTML/PNG不全一致，源码优先；记录差异 |
| RustFS/ObjectStorage可复用 | ObjectStorage.java:8、RustFsObjectStorage唯一adapter；StorageProperties.java:12/15强制rustfs/pathStyle。归档community-oss并非实际阿里云适配；原来主评论过早表述已公开更正 |
| Markdown验证已有 | AttachmentValidator.java:17 stage独立于post；worker markdown私有严UTF-8/control；resume只收.md并补20k码点，PASTE提共享helper，不搬社区配额/业务接口 |
| 输入/报告异步可参考 | InputPersistenceServiceImpl/Mapper只有部分token/lease；InputRecoveryRunner启动一次，Report无完整lease；面试专用expiry与有效lease提交，不宣称原流程已满足FR-025/026 |
| 默认模型失败仅手动重试 | AgentScope2.0.3 JAR实际MODEL_DEFAULTS maxAttempts=3；专用gateway必须显式1并计数fake HTTP，见backend-contracts |
| 当前Flyway候选版本待核 | 已有V1–V18，resume预定V19/engine V20，实施前重查；原迁移不改 |
| RustFS新前缀未验权限 | initialize-storage.py只community/attachments/*且policy shape guard；新interview/resumes/*最小显式canonical policy维护，不放宽全桶/admin |
| backup仅PG | 当前backup.ps1还固定ai-workbench-postgres-1，隔离项目不能原样套；补显式目标/联合object helper，新DB/桶恢复并核hash |
| 代理传输未知 | nginx默认1m只社区upload22m；resume import需专有2m运输限额，保业务1MiB |
| 默认test schema可任意 | IsolatedProfileSchemaGuard要求d9_*_tests_YYYYMMDD，E2E固定d9_e2e；命令写实际约束 |
| 缓存保护已有 | CommunityCacheControlFilter仅社区，新resume/interview/JD必须显式no-store/private |
| 参考源码可能缺 | 本机实际存在且commit相同；AGPL文本已读，本轮不复制，prototype自有交付与本仓库代码复用不混为同一许可对象 |

完整真实路径/符号/行号：research/frontend-reuse.md、backend-contracts.md、storage-baseline.md；被上述结论影响的源建议均已局部适配design/implement，不原样宣称已验证。

## FR/AC与产品/技术选择

26个FR和26个INT-AC正文无损继承prd，场景源acceptance不可变；分工及额外验证在acceptance-map。不把验证计划写PASS。若源AC-023的byte等号与正文复合限额不能同时构造，分别验证门禁并明确完整API双条件，不降低产品上限。

- 已确认产品：source21项、本轮RustFS先用/阿里云暂不做、直接复用原型、一般问题末尾汇总。
- **P-01仍待批准**：第六retainedAI面试工作区，个人中心仅账号菜单；最终一次确认包含该明确选择与连续执行。
- 本地技术：同单体/owner/3层状态、0-based turn、payload receipts、单调resume版本、JD成功分析同owner/direction/hash多场copy，明确改文/移除/取消才清理、统一jobs/分组lease/禁SDK retry、后端等权两位HALF_UP、私有key、最小policy、联合隔离备份、fakeHTTP+PG/RustFS验收。
- 未添加兼容层/回退/双读双写/功能flag；回退仅指工作成果/保数据的运维边界，不提供旧版业务路径。
- 未来事项：OSS适配/配置/对象迁移/云验证；真实模型最大输入质量/费用/延迟（需真实调用授权）；任何外部AGPL Java/Prompt/Skill复制前许可确认。均不禁用本地已要求功能。
- 批准后连续执行仅同范围实现/检查/修复/隔离验证；commit/push/PR/发布/不可逆生产数据/付费调用未被当前创建规划授权自动覆盖。

## 规划门禁状态

技术证据与正式文档已补齐；上下文/静态一致性由主代理校验，实际结果追加planning-review。唯一产品阻塞是P-01/最终规划批准，保持planning，不运行start/implement。所有产品AC目前NOT_RUN；真实实现后必须按本地delivery模板回流。
