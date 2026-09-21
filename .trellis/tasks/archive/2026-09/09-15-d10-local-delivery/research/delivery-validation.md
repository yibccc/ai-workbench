# D10 本机交付验证

日期：2026-09-21，Asia/Shanghai。基线为 D9 全部提交及用户明确“d9验收通过”；本轮未产生真实模型调用，未替用户填写主观验收。

## 环境与命令

- Windows 11，Oracle Java 17（17+35-LTS-2724），Maven 3.9.9，Node 25.2.1，npm 11.6.2。
- Docker 位于 WSL Ubuntu，使用 `wsl.exe -d Ubuntu -- docker ...`。Compose postgres:17.6-alpine / redis:7.4.2-alpine；两容器 healthy，5432/6379 都只映射127.0.0.1。
- 应用为打包 JAR + 本机 Vite dev；8080/5173 只监听127.0.0.1。未改业务实现、未创建演示业务数据。

| 检查 | 实际结果 |
|---|---|
| `mvn -q clean verify`，19:32—19:33 | PASS：83 tests，0 failures，正常打包，无 JAR 锁 |
| `npm ci` | PASS：172 packages，审计0 vulnerabilities |
| `npm run lint` / `npm run build` | PASS：ESLint、TypeScript、Vite构建 |
| `docker compose config --quiet` | PASS；两容器 healthy |
| `check-safety.ps1` | PASS：所有脚本语法、匹配身份、启动时间/command/root不匹配拒绝 |
| 启动重复执行 | PASS：复用自身PID；未托管端口8080明确拒绝 |
| 停止重复执行 | PASS：只停止登记的前后端；无登记进程时只提示，数据库保留 |
| 新 JVM 重启，19:36 | PASS：40260→32776；20个项目ID相同；health/db/redis UP |
| `JAVA_HOME` 未设置 | PASS：通过实际java.home解析JVM并启动，避免Oracle javapath双PID |
| 完整浏览器首次 `npm run e2e -- --retries=0` | 15/16；报告迟到保存用例等待确认弹窗超时，定向复现1/1失败；见下述分析 |
| 完整浏览器第二轮 | 15/16；保存迟到已通过，跨窗口删除首次WS就绪竞态失败；其余通过 |
| 两受影响用例修正后各重复3次 | PASS：6/6，21.3s，retries=0；没有声称某次全套16/16 |
| 前端测试修正后 `npm run lint` | PASS |
| 独立终审完整 `npm run e2e -- --retries=0` | PASS：16/16，55.9s；包含两项修正后的用例，未重试 |
| 独立终审 `npm run lint` / `npm run build` | PASS：ESLint、TypeScript、Vite |

手工关闭的旧服务只涉及经端口及命令核对的本项目 Java 6196、Vite 45144。其后均使用新增脚本管理，启动日志和PID/创建时间/命令/目录位于忽略目录 `.local-runtime`。脚本不停止其他Java/Node，失败时保留日志与已启动服务的登记，用户用stop后重试；不设计Windows服务或跨主机部署。

## 备份恢复演练

只读预检确认非模板数据库仅 `postgres`、`ai_workbench`；后者含 public、d9_backend_tests、d9_e2e、d9_live_acceptance schemas。备份涵盖整库，不把测试schema计数与业务public混淆。

1. `backup.ps1 -RestoreTo d10_restore_20260921_validation` 调用 pg_dump custom archive，在新库执行 pg_restore；源库不覆盖。
2. `backup.ps1 -RestoreTo d10_restore_20260921_existing -ArchivePath ...` 使用同一已有归档再次恢复到另一个新库，验证实际恢复路径。
3. 恢复到已存在名字被拒绝，exit 1；没有覆盖或DROP现有库。
4. 校验后只用精确库名 dropdb 删除本轮创建的两个验证库，最终库列表仍为 postgres、ai_workbench；不删除源库测试schemas或业务数据。

归档：`.local-backups/d10/ai_workbench-91313278bc56479cb5b6da778d582211.dump`。
SHA256：`7E88F5A1473FB7E6C996E219A7F61E63047059B067E1E8BBD26B0CD3DD77A63B`。
备份包含业务文本，不提交Git；应另行复制到安全存储。容器内本轮随机 `/tmp/workbench-*.dump` 逐一在finally按精确文件名清理。

原库与首次恢复库如下count及按主键排序整行JSON哈希完全相同；后两行同时核对第二次已有文件恢复库。整行包含内容、版本、时间、来源JSON，不仅比较数量。

| public 表 | 行数 | 整行集合MD5 |
|---|---:|---|
| projects | 20 | f6f68332b8bd3c60b73ed3948826dc76 |
| todo_items | 53 | dd9ea34183b0305bbdfd357c87c9d96f |
| work_records | 91 | f60904bd749dc2a59fe7dd25bd4b99ca |
| capture_inputs | 139 | a2f6514aa2bef3b2a11bebbd49004e5a |
| reports | 5 | 92b7743e6faf416463dc15ee923ac29b |
| report_sources | 241 | b5a265ae6541566776e9edb38da8fdb6 |
| task_events | 2 | 1a360936e8c143e920f130ed6fa6d4db |
| capture_generated_items | 141 | ffda66c64005e35668878f1d38d289f9 |

public Flyway成功迁移12条、最新V12；两恢复库report_sources孤立report引用为0。软删除、归档数据包含在这些原始行数中，不能与界面活动条数直接比较。

## 浏览器失败分析

旧“报告保存迟到不覆盖继续输入或切换后的版本”测试只挂起PATCH响应，实际服务器已提交；WebSocket事件GET可提前使selected.content同步已保存正文。第二次保存后没有新编辑时dirty=false，因此无需出现“放弃修改”。等待不存在的弹窗导致30秒超时，完整运行及定向都复现。

测试修正为 second.wait 后继续输入新的未保存内容再切历史，验证确认弹窗、迟到PATCH响应和新草稿保留行为。首次失败及修正后的测试结果分别记录。

第二轮全套出现另一用例：页面首次显示正文后立即发跨窗口删除，5秒未收到推送；原用例未等待WS握手，而断线GET兜底是15秒。现在通过Chromium CDP显式等待101成功握手后才执行删除，继续保留5秒即时消失断言，不靠延长全局timeout掩盖问题。该用例与迟到保存分别独立重复三次共6/6通过。原失败说明仍保留，浏览器临时trace会被后次运行重建。

新增脚本的本轮修正：WSL路径先转换正斜杠后交给wslpath；PowerShell 5将Java正常stderr视为NativeCommandError，探测段暂用Continue并检查退出码；启动等待同时验证登记PID和实际端口归属；前端子进程显式移除继承的后端连接/模型变量，启动后还原父进程环境。已有备份可在源库缺失时恢复到新验证库。19:42最终安全检查、停止、启动再次通过；当前体验服务后端PID42064、前端PID35828，均监听127.0.0.1。

## 最终验收状态（按真实证据）

| 条目 | 状态 | 依据 / 仍需证据 |
|---|---|---|
| A13 服务重启持久化 | PASS（技术） | D9重启/恢复测试 + D10新JVM读取原库及完整备份恢复；用户独立按README重启另待确认 |
| A14 连续真实使用及周报提交 | PENDING | 需G01—G03真实使用证据 |
| G01 连续5工作日 | PENDING | 没有连续5工作日真实记录证据，不能按日历推定 |
| G02 真实周报编辑复制提交 | PENDING | D9功能用户验收通过，真实周报完整提交流程尚无单独明确反馈；替身浏览器流程不能替代 |
| G03 人工与工作台耗时对比 | PENDING | 用户尚未提供手工基线与工作台实际修改耗时 |
| G04 基础数据可靠 | PASS | D9最终用户验收、83项后端、事务/重试/撤销/重开验证；本轮public恢复数据一致 |
| G05 可解释与复现 | PASS（交付物） | README、architecture.md、demo.md和验证命令可用 |
| 用户按README独立重启/录入/报告 | PENDING | 工程侧已演练，仍需用户本人独立执行反馈 |
| 143来源真实模型PT6M边界 | PENDING | 离线143来源通过；D8 PT4M超时、D9 0/3账本保持；本轮0次调用 |

D9最终用户验收见归档acceptance-log.md末尾2026-09-21结论。连续使用天数和耗时对比的当前状态见上表。

## 独立终审补充

- 修复 `backup.ps1` 清理异常覆盖原始备份/恢复失败的问题：清理失败输出带精确临时文件路径的警告，保留原异常。内存替身模拟 pg_dump 与 rm 双失败，确认原始错误保留；未执行 Docker 写入。
- README 手工前端启动移除 Windows PowerShell 5.1 不支持的 `&&`，改为按次序执行命令。
- `check-safety.ps1` 再次通过；WSL `wslpath` 带空格参数得到完整路径；已存归档 SHA256 与上述记录相同；README 本地文档链接均存在。
- 终审仅在隔离 e2e schema 运行浏览器回归，未产生模型费用，未停止体验服务。复核8080/5173分别仍为PID42064/35828，均绑定127.0.0.1；health、PostgreSQL、Redis正常。非模板数据库仅ai_workbench、postgres，恢复临时库已不存在。
- 全套浏览器运行中出现一次 Vite WebSocket ECONNRESET 日志，各行为断言及断线恢复用例仍通过，记录该日志但不把它当作业务失败或隐藏它。
