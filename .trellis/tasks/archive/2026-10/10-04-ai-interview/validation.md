# 最终整合验证

状态：工程验收完成，业务提交 `7e71e39636977b2b24eb8ca0e31150156db45162` 已生成；交付文档及原生归档/日志接续。用户已批准连续实现/审查/隔离验证/规范和本地提交归档；本记录只写实际结果，阿里云和真实付费模型测量延期。

## 后端完整门禁

- 最终离线 Linux Java17 `clean verify` 已实际 exit0/BUILD SUCCESS：**271 tests，0 failures，0 errors，0 skipped**；compile189主源/testCompile54测试源，生成并repackage `backend-0.0.1-SNAPSHOT.jar`。SDK System/User隔离最小修复及root受guard的E2E reset新表都在本次源码中。
- 环境：project wbinterview20261004，新PG25432/DBwbinterview_test；完整suite独立schema d9_interview_full_tests_20261004与live专用d9_live_acceptance；Redis36379、RustFS29000/bucketwbinterview-fixtures；modelkey/全局bootstrap为空，无RUSTFS管理凭据给Maven。
- 工具：缓存maven:3.9.9-eclipse-temurin-17、只读D:/APPS/apache-maven-3.9.9/repository与现settings；显式Mockito5.17 javaagent、boundedheap/CPU/SerialGC。
- 第一轮270 tests/3fail/7error如实FAIL；XML根因是测试进程误继承synthetic bootstrap创建额外ADMIN，以及live-acceptance未设其专用数据库URL指向container localhost。未修改原AccountService/管理员规则，窄化env+新schema后271全通过。
- 子针对证据：resume24初始+精确lease/scalar5定向（两个新增独立case）；engine25初始+report/receipt2+System/User SDK新增1；实际最终数量以本次Surefire271聚合为准，不能累加重复执行为额外用例。

## 前端当前门禁

独立UIcheck：最后27 fixture浏览器PASS，另外ACK→旧GET→权威GET1定向PASS，最终lint/strictType/Vitebuild PASS。四尺寸六屏24PNG已在UI artifacts/visual；fixture只证明UI状态/几何，不是后端事实或真实模型质量。最终JAR的独立E2E后端18080、新wbinterview_e2e/schema d9_e2e实际V1→V20/healthPASS；Vite/Chromium控制配置限定非业务secret环境，真实链/旧回归117用例全部通过，进程已停止。

## 对象存储及ops

已真实验证隔离RustFS双prefixPut/Get/Delete、anon/outside/admin/ACL/ListBucket拒绝；App专有policy显式维护已再实测exit0、密码不旋转/共享policy不改。最终33轻量ops tests与真实自有短进程native capture通过；native capture不可证实时只记录safeError，普通app启动继续，backup拒不明身份。

新恢复目标wbinterviewrestoreproof20261004已通过prepare-volumes在创建前生成freshproof，随后PG25434/Redis36381/RustFS29004健康；旧stoppedrestore目标没有事后补造proof。真实defaultnative source/API数据与联合backup/restore/恢复HTTP已全部通过，结果如下。

## 最终整合实际结果

- 完整真实浏览器117PASS、exit0、0failed/flaky/retry：13community/14focus-alarm/28interview/62workbench。实链原件A字节/SHA、编辑正文A/B快照、多场draft/submit/刷新/重登/current删除/第三通用、1答5未答early→13.35/UNANSWERED0、GET稳定、单场删除均通过。组编号显示修正为真实mainIndex/2+1；旧撤销夹具时序修复保实际401和完整5秒Toast。见browser-real-validation。
- Ops默认native启动/env与caller不变、Vite无secret；2对象PG+original bundle/verify→prepared fresh-volume record的新DB桶恢复，31张表/Flyway/SHA相同；恢复HTTP CurrentB/version2/fileA SHA/historyA/JD/社区原件、GET无新增AIjob、源beforeafterdigest均PASS，6完整性/既有目标负例PASS。两个实际工具bug已修，33ops tests PASS，final-ops-review无新增缺陷。
- 真Nginx→nonroot packagedJVM→RustFS：29 HTTP+4现物anonymousGET/HEAD PASS，2m/1MiB/20k运输与业务边界/普通API1m/社区22m/SHA/owner等全部正确，0AIjobs。proxy自己的两个容器已精确清理，未动数据服务。
- 正常本地.env所指RustFS现bucket/app身份已严格knownshape维护为专用canonical双prefix并读回验证，现密码/共享policy/普通.env SHA不变，日常DB/应用进程未触。
- 26INT-AC工程映射在research/final-acceptance.md全部PASS或明确PASS_ENGINEERING，实付模型质量/性能及Aliyun仍DEFERRED。

## 本地收尾

业务提交7e71e39已完成，仅本任务delivery文档提交、4任务archive/session接续；原用户6项改动排除，bootstrap任务不归档。原始network trace可能含运行Cookie，仅本地保留并由.gitignore精准忽略；已提交合成PNG/脱敏Markdown及SHA摘要。

自有测试应用/代理/临时WSL保活已按PID、创建时间、命令、工作目录核验后停止；隔离数据卷保留。根清理脚本首次在pwsh7中因JSON时间自动类型化拒绝身份比较，改用脚本既定Windows PowerShell 5.1入口通过身份核验完成清理，未绕过guard或停止未知进程。

运行问题保留在research/issues.md/各validation，不把未执行门禁当PASS，不停止日常服务/删除源或旧用户数据，不push/PR/外部发布。
