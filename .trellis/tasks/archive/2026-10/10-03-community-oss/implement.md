# 执行计划与验收门禁

执行批准已取得：用户在最新最终规划摘要后明确“开工/批准并连续执行”，实际publishing/UI子任务已启动，storage依核心交付门禁后接入。原最终确认门禁已满足，不再重复询问。工程AC仅在真实验证后记录结果，当前起始值为NOT_RUN。

## 顺序与分工

| 顺序 | 任务 | 交付/所有权 | 依赖 |
|---|---|---|---|
| 0 | `10-03-community-oss` | 源快照、统一PRD/design、接口/AC合同、整合审查和回流 | 规划批准 |
| 1 | `10-03-community-publishing` | V17、新发布/作者/今日素材Controller-Service-Mapper、核心权限与版本测试 | 父正式合同 |
| 2 | `10-03-community-storage` | V18、ObjectStorage/RustFS、上传/读/清理、compose/local/nginx配置；把附件校验/引用接进发布事务 | publishing表与事务完成 |
| 3 | `10-03-community-ui` | R2 React/CSS复用、hash/登录返回、API/Blob、全部界面/响应式与E2E | API合同可先做骨架；最终联调/验收须1+2通过 |
| 4 | 父整合 | 全AC/22场景矩阵、完整回归、spec沉淀、delivery.md、末尾问题清单 | 三交付通过 |

各任务都明确列出依赖；父子关系不是依赖系统。主会话逐个启动实际执行子任务，dispatch提示第一行必须 `Active task: <task.py current真实路径>`；优先trellis-implement/check子代理与native上下文注入，未注入时子侧按jsonl→PRD→design→implement读取。Agent不可重置他人改动。有明确不同文件所有权才并行写代码。

上下文容量已知：database-guidelines为38,126字节、backend研究为32,884字节，均超过单文件32,768注入上限。validate允许且告警已记录；不修改全局限制或删事实规避。实际dispatch必须明确：出现truncated/index notice时子代理从原路径完整读取相关段（backend研究须完整），随后完整读取父`prd/design/implement`并以父design已选合同覆盖research历史候选/review旧行号。不能把已截断内容当完整规范。

## 0：开工准备

- [x] 用户在展示本版本后明确批准执行；依据为后续“开工”及确认问题答复“批准并连续执行”，记录在planning-review及task.meta。
- [x] 重新核对git status/HEAD/session/current，保留已有5个用户SOP文件；从真实HEAD建 `codex/community-oss`分支，不stash/reset。
- [x] 4个任务规划三文档与implement/check manifests均validate成功；不使用allow-empty-context。
- [x] 按storage研究核实Java17/Maven、Node/npm/Chromium、WSL/Docker、9000/9001端口；任何测试schema和bucket均专用隔离，禁止public/日常业务表reset。
- [x] 新依赖与RustFS固定版本在线获取并保存实际版本/命令；不执行交接包build.py/smoke.py/HTML作为代码验证。

## 1：发布核心

- [x] V17追加表、索引、同owner FK与状态约束，保留V1～V16摘要；空schema迁移验证。
- [x] 明确三类型、owner私有draft/immutable revisions、MEMBERS范围、author独立public profile；writer DTO与reader DTO分离。
- [x] PageQueries列表/详情/作者、本人稿件、profile、保存/当前预览payload发布/撤回/ADMIN hide审计与ProblemDetail；publish幂等固定result_version+fingerprint、版本竞争/first_published_at、仅保存type/date/summary不提前公开的快照测试。
- [x] 今日素材复用当前已合并的WorkRecord展示投影，source IDs本人/日期校验、公开字段白名单、includeFocus单独汇总；同日多篇。
- [x] 真实A/B/ADMIN/匿名HTTP+PostgreSQL/Redis测试，不把直接service调用当HTTP鉴权已测；回归原private SQL、任务/focus完成、报告来源。
- [x] storage阶段接入附件前核心只交付无附件业务；不以此宣称父需求完整，也不增加默认禁用发布功能。

## 2：RustFS/附件

- [x] V18追加attachment/draft ref/revision ref；ObjectStorage与S3 RustFS adapter、统一safe storage异常；不实现Aliyun adapter。
- [x] 为WSL Docker添加固定RustFS镜像/持久volume/私有bucket配置，native与compose端点各自正确；新增凭据进入前端子进程剥离清单，无值输出或VITE secret。
- [x] 单文件暂存、精确计字节、真实结构/UTF8校验、safe filename；Nginx上传专路由22m/后端file21MiB/request22MiB允许20MiB+multipart开销，其他既有入口限制保持。
- [x] owner优先、post锁/version、先识别upload requestId再校验旧version、并发计额/预留、外部put无长DB事务、finalize持久token/state fencing；崩溃逾期显式recover、失败新ID/new key保留旧补偿行；输入/其他可用附件不丢。
- [x] 修改publisher保存/发布事务同锁验证READY/owner/额度并写不可变refs，F1→草稿F2→发布F2实际散列/权限测试。
- [x] 作者与读者stream、HEAD、Range不支持时full200路径、no-store/nosniff/download headers；所有路径先鉴权且不云重定向，不同账号取消流不污染UI。
- [x] 显式cleanup同post锁DELETING fencing，查全部draft/revisions引用，删除失败可重试；不设置自动清理日数或用户配额。
- [x] 真RustFS专用bucket无签名GET被拒、后端write/read/delete验证，非mock证据；超限/边界/伪装/并发/存储失败注入与代理链路20MiB样例；延迟put+另tab移除/cleanup与JVM崩溃recover不复活不永久占额。

## 3：R2正式界面

- [x] 从不可变R2原源码提取既有CSS/区域/组件，不重绘新风格；source→target复用表落盘。
- [x] 同一Workspace账号根新增社区space，单一strict hash parser/允许目标登录返回；受保护加载门禁，保留5工作区/ADMIN导航/FocusController/声音与草稿筛选。
- [x] 共享HTTP身份/CSRF管线新增Blob解码、上传/下载与objectURL清理，真实401与403/404/network独立。
- [x] react-markdown安全渲染，attachment UUID许可集图片、禁外链图片/HTML/危险协议；只PDF/MD下载，大图可键盘关/focus还原。
- [x] 广场/作者/我的发布、今日跨页选择、编辑/附件/预览、发布更新/撤回/ADMIN hide理由、profile维护；手动save、dirty guard与409输入保持。
- [x] 按R2全部页面/状态与17图桌面/移动对照，补源码已有但无PNG状态；制作正式UI截图/geometry/reachability证据。异常通过标明测试夹具呈现不能冒充业务验收。
- [x] 真浏览器A发布/B读/更新/下载/撤回/管理员下架、匿名深链登录返回、跨页/跨日素材、旧账号晚到upload/Blob隔离、工作区往返专注/草稿回归。

## 实际验证命令与隔离

先按 `research/storage.md` 的本机工具位置与测试数据库键设置当前进程；不读取或打印 `.env`秘密。后端必须使用真实PostgreSQL/Redis以及新的专用schema，`TEST_DATABASE_URL`的currentSchema与`WORKBENCH_TEST_SCHEMA`同指`d9_community_tests_20261003`（已核实允许命名），测试模型使用现有deterministic配置，不调用付费模型。具体URL/密码仅经env注入，验证记录只记schema脱敏标识。Playwright沿现行`d9_e2e`/15432/16379隔离基础设施要求，并额外传专用RustFS bucket/endpoint键；不能让e2e用日常public/schema/bucket。

现有仓库命令（从repo root，PowerShell）：

```powershell
git status --short
git rev-parse HEAD
python ./.trellis/scripts/task.py validate .trellis/tasks/10-03-community-oss
wsl -d Ubuntu -- docker compose -f /mnt/e/projects/workbench/compose.yaml config --quiet
wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- python3 scripts/local/verify-compose.py
& 'D:\APPS\apache-maven-3.9.9\bin\mvn.cmd' -s ./backend/maven-settings-aliyun.xml -f ./backend/pom.xml clean verify
npm --prefix ./frontend run lint
npm --prefix ./frontend run build
npm --prefix ./frontend run e2e
```

Maven执行使用已核实Maven路径及`C:\Program Files\Java\jdk-17`，不能默认PATH上的新Java；所有新文件UTF-8，必要时为当前子进程设置file.encoding。`e2e`脚本实际存在（不是test:e2e），现行playwright启动隔离backend18080/Vite15173；实际schema在启动前和reset guard都检查，不把日常运行实例当测试服务。`verify-compose.py --help`已在WSL Python3退出0确认，该脚本无必需位置参数；正式运行在WSL，因为Windows没有docker CLI。它会创建/重启并清理自己固定隔离项目，执行前按已读脚本归属检查确认项目无冲突，新RustFS纳入隔离资源，不动日常volume。

新增验证（实现后存在才执行）：选定publishing/storage Java测试类、`frontend/e2e/community.spec.ts`、RustFS live integration/代理附件验收入口。新测试路径是拟新增，不冒充当前可调用命令。所有真实结果写 `validation.md`，包含被测HEAD+worktree范围、命令、exit code、schema/bucket、HTTP/SQL/文件sha与覆盖限制。

## AC与TC映射 / review

源TC-01～21全部继承；源码实际用例覆盖每个AC，父交付矩阵明确PASS/FAIL/NOT_RUN与证据。publishing负责01～06/08～12，storage负责13～21（含05撤回联动），UI负责02/03/07～10/12/16/20/22与跨层全流程；AC21只验RustFS，Aliyun标DEFERRED而不是PASS。

- [x] 每子任务实现后trellis-check自修复：spec、lint/type/test/事务/权限/复用/原型布局；发现真实冲突更新研究，物质改变产品/范围/布局/兼容必须回最终规划确认。
- [x] 最后一次check覆盖所有业务diff及V1～V16未改，测试/场景覆盖未漏，不止最后子任务。
- [x] 主会话按trellis-update-spec沉淀新的publication/attachment/space合同；既有private规范仅补明确边界。
- [ ] 本轮初始授权不含commit/push/PR/部署。执行批准只含实现与验证，尚未获得这些独立动作授权；不得触发archive/add_session自动commit。完成后具体可审阅结果如需提交，统一在最后列出；不要把仓库3.4常规阶段当用户已批准git写入。
- [x] `docs/dev-sop/WB-20261003-community-oss-3f9aaa/delivery.md`按模板回流，真实状态/代码变更/源方案差异/所有22AC与review/残余问题；业务commit没生成写“尚未提交”，不能预填SHA。
- [ ] 最后向用户汇总延期阿里云、任何未执行检查/人工声音或截图待验、必要兼容/发布选择；常规已经修复的问题只简要记录，不重复中断执行询问。

## 失败与恢复

维持当前实现前进修复；保留draft、historical refs与业务volume。禁止无授权delete volume/schema/bucket、降迁移、旧版应用fallback或功能flag。新功能真实不可用是待修问题，不以隐藏功能规避AC。环境不可得时先尝试授权范围内修复，仍不可验证准确报告NOT_RUN，不把Mock/静态阅读当真实验证。

## 最终执行记录（不改已批准合同）

实际测试改用全新 `d9_community_storage_tests_20261003`，旧早期V18测试schema保留不repair/drop；E2E仍d9_e2e，Native另用新schema/namespace。最新真实门禁为后端223全量、18原定向、新AC19单方法1PASS；社区12完整、原76回归（历史2FLAKY）、最新12+13禁重试25PASS及新增R2视觉单case1PASS。Docker/原生/环境/清理/正式视觉均完成，完整22AC在validation.md关闭。

后续只待Phase3.4具体本地业务提交分组批准，再原生archive本需求四task与journal；当前未commit/push/PR/远端部署。所有普通修复/文件验证范围及人工未验收已在delivery汇总，最终确认仍在用户端。
