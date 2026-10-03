# AI 面试执行计划

状态：规划；用户尚未在本轮最终规划摘要之后批准。不得运行task.py start/派发implement/改产品。P-01与持续执行授权在planning-review登记，批准后按以下有序交付。

## 1. 批准后启动前门禁

- [ ] 保存真实批准消息：正式规划版本、P-01“第六AI面试工作区+账号菜单个人中心”、连续实现/检查/修复/隔离验证/规范沉淀及本地提交/归档授权；一般问题末尾汇总。
- [ ] 重查git status/HEAD、源14文件SHA与已有6项工作区改动；保留用户改动，不reset/stash。
- [ ] 用当前真实chat上下文ID恢复Trellis pointer；重查当前任务与并行任务，不能接管00-bootstrap-guidelines。
- [ ] 验证父/三子prd/design/implement + implement/check jsonl；载入Phase1.4详情；没有产品阻塞才start实际子任务。
- [ ] 用户批准代码分支选择后（或其执行授权足够时）按项目默认建立codex/ai-interview分支；不把既有社区历史回滚。
- [ ] 创建全新隔离PG/Redis/RustFS测试项目，独立非默认端口、数据库/schema/桶/应用身份；无真实模型key，环境值只存忽略路径。
- [ ] 实施前重查Flyway最新编号与SDK API；新scope/private HTTP合同统一。

## 2. 子任务顺序与责任

1. **10-04-interview-resume**：V19（或实际下一版）、singleton version、共享Markdown检查、PASTE/import/download/delete、receipt、预约/迟到put/fencing/耐久清理；最小IAM前缀与nginx专用上传位置；对象备份/新目标恢复。owner/并发/大小/失败核心验收通过。
2. **10-04-interview-engine**：依赖已验证resume读取/version；V20（或实际下一版）、JD持久来源/改文失效/显式retry与同来源多场复用、immutable会话/2N题单、draft/submit/complete/receipts、job分组lease/恢复、AI禁SDKretry、评分/历史/删除；假HTTP与真实PG并发验收通过。
3. **10-04-interview-ui**：依赖P-01批准和稳定API；原型内容/CSS直接迁入、retained六导航/profile菜单、typed API、全状态交互、码点计数/保File、Dialog/身份清理/恢复；desktop/narrow与原工作区回归。
4. **父整合**：全26 INT-AC、源HTML对照、全量后端前端、隔离恢复；独立跨层审查、规范沉淀、剩余风险回流。

UI骨架在批准后可与后端非冲突工作并行；实际API联调与验收必须按依赖。主会话协调，默认用trellis-implement/trellis-check子代理；每dispatch首行Active task真实目标，原生注入优先，缺注入按context jsonl+prd/design/implement子侧加载。不派implement去规划父任务。

## 3. 实际可用验证入口

以下是已定位工程命令与规划模板，不是已运行结果；新增测试文件名以实际实现为准，不写不存在的测试结果。

### Trellis规划门禁（本轮可执行）

```powershell
python ./.trellis/scripts/task.py validate .trellis/tasks/10-04-ai-interview
python ./.trellis/scripts/task.py validate .trellis/tasks/10-04-interview-resume
python ./.trellis/scripts/task.py validate .trellis/tasks/10-04-interview-engine
python ./.trellis/scripts/task.py validate .trellis/tasks/10-04-interview-ui
```

### 后端（批准后）

先设置并核验专用 POSTGRES_USER/POSTGRES_PASSWORD（秘密仅忽略环境文件，无默认生产凭据），再核验TEST_DATABASE_URL的currentSchema与WORKBENCH_TEST_SCHEMA同为全新 `d9_interview_tests_20261004`（IsolatedProfileSchemaGuard允许d9_*_tests_YYYYMMDD），指定隔离PG用户/非默认端口和独立Redis；绝不使用public/日常数据库/schema。以下env在已验证隔离目标上设置，密码不写规划。

```powershell
$env:WORKBENCH_TEST_SCHEMA = 'd9_interview_tests_20261004'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/wbinterview_test?currentSchema=d9_interview_tests_20261004'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '16379'
$env:DEEPSEEK_API_KEY = ''
mvn -f backend/pom.xml clean verify
```

连接示例端口并非已创建服务；冲突时用实际新隔离端口并记录。保持现有test profile，不全局覆盖spring.flyway.schemas污染live-acceptance。迁移测试独立connection恢复search_path，最新/空库与所有既有migration回归。

针对新增测试先跑实际类（读取后再写-Dtest参数），通过后完整clean verify一次；SDK重试以local fake HTTP端到端发送计数，覆盖5xx/断流/timeout/坏JSON与仅显式retry第二次请求，不用只mockGateway冒称通过。

### 前端（批准后）

```powershell
npm --prefix frontend run lint
npm --prefix frontend run build
$env:E2E_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/wbinterview_e2e?currentSchema=d9_e2e'
$env:REDIS_PORT = '16379'
$env:DEEPSEEK_API_KEY = ''
npm --prefix frontend run e2e
```

playwright.config.ts强制E2E_DATABASE_URL、POSTGRES_USER、POSTGRES_PASSWORD、REDIS_PORT；运行前必须均已设置为隔离值，文档不写密码。已有安全检查和d9_e2e reset守卫；E2E库与后端tests另库，不能仅换schema绕过库隔离。实际Playwright dev backend18080、Vite15173，运行前核验端口归属。新增面试fixture只能test profile/假gateway，不能让生产新增绕过。

### RustFS / ops（批准后）

定位既有脚本 `scripts/local/initialize-storage.py`、`storage-smoke.py`、`backup.ps1`、`verify-compose.py`；实施前分别读--help/代码，使用它们真实参数，不猜命令。存储对象测试专用桶/前缀和应用身份，A/B鉴权经后端，匿名直读失败；同时验证原community前缀操作不被破坏。

新备份工具定稿后在implement/validation补真实命令：PG dump +对象manifest导出→新隔离库/桶恢复→逐key bytes/SHA/文本/current pointer/历史snapshot比对；精确清理自己的测试资源，不删除日常卷。现有backup.ps1仅PG已核验，原件恢复不能靠该命令声称成功。Linux nginx新增路径2m需真实代理合成上限/正文上限和社区原入口回归。

## 4. 必须实际覆盖的故障与并发

- owner A/B/ADMIN、匿名/CSRF、404不漏状态、no-store、HEAD/Range/下载鉴权。
- PASTE及MD严格UTF-8/control/BOM/byte等号与+1，20k/10k/5k码点等号与+1，emoji/非BMP，输入不截断、File/draft保持。
- current并发CAS、删后单调version/ABA、创建锁定版本、旧snapshot留存、延迟put/CAS失败/cleanup失败/替换竞争。
- create同ID同payload一次/不同payload409/删除后重放不复活；两场+第三场独立恢复。
- N3/5/20与所有整数、少/多/空/错误parent；strict整体失败不补假题。
- submit独立事务并发、旧标签页、response丢ACK、已锁不可覆/只推进一次；draft不推进；complete与submit竞争只冻一次。
- JD改一字/方向失效、owner/hash不匹配、失败不静默无JD、显式移除、分析明确删除后旧token无权、同hash成功分析可多场copy。
- 评分分组固定input hash；未答0/失败未评估/总分null，平均/round规则；成功组复用，GET无新增调用。
- 排队lease从start算、queue拒绝/expiry、有效lease不恢复、token过期/新token/删除迟到成功失败、restart不自动再调模型。
- 20主问/40轮/最大输入组合假gateway与fake HTTP；真实供应商质量/费用延期，不混写工程PASS。
- 原records/tasks/focus/reports/projects retained草稿/筛选、唯一focus、导航返回/账号切换/失效/后台activity不续期；六导航320×520及桌面原型对照。

## 5. 检查门禁、规范与回流

每子任务完成后trellis-check独立审查并修复，记录文件/commit基线、命令/exit/schema/桶/假模型边界；全量验收只来自实际运行，planning validate≠AC PASS。最后读trellis-update-spec，沉淀新增模块与SDK无重试/码点合同，修改仍写五区的相应spec；保现有API/owner约束。

非阻塞问题写父research/issues.md，交付时集中确认；真正需要范围/兼容/风险选择时只停依赖动作，继续无关授权工作。本轮创建规划尚未授权提交或归档；最终“批准并连续执行”如明确涵盖本地提交/归档收尾，则按已批准范围和Phase3继续，不重复请求同一授权，提交前核对只含本任务改动。push/PR/外部发布仍须独立明确授权。脚本auto-commit/归档须在该真实本地收尾授权后执行。

最终回流按docs/dev-sop/templates/delivery.md在 `docs/dev-sop/AIW-INTERVIEW-20261003-cf4cf8fc/delivery.md` 生成，已完成行为/所有INT-AC实际结果/偏差/残余/发布事实如实写；本轮不伪造业务commit或交付完成。approved continuous执行后用trellis-continue恢复并按当前next子任务推进。

## 6. 回退边界

失败先修复实现/状态协议，不加功能flag隐藏已要求功能；未批准兼容层不得补。只回退本任务已授权代码；新表/原件/收据保留，不破坏原业务数据。清表/清桶/恢复日常数据是不可逆范围，须单独明确批准并有备份。
