# 最终 INT-AC 工程证据映射

原正式条件唯一正文在父prd，原acceptance不改。本表只登记实际证据；PASS_ENGINEERING明确不包含真实付费模型质量/性能/供应商容量测量。假/确定性AI与真实数据库/HTTP/S3/browser分层明确，阿里云接入延期。

| AC | 判据 | 实际证据 | 状态 |
|---|---|---|---|
| INT-AC-001 | 账号隔离 | Resume/Interview真实HTTP A/B/ADMIN、CSRF与same-owner FK；Proxy匿名/foreign HEAD | PASS |
| INT-AC-002 | 精确2N题单 | PG N3/5/20与坏题单zero子行，SDK/domain严格数量/parent/type | PASS |
| INT-AC-003 | 提交不可回改 | 真实PG submit并发/receipt不同payload409/旧版本；真实浏览器两场submit | PASS |
| INT-AC-004 | 草稿/丢ACK恢复 | 真实浏览器刷新/重登两场draft；PG receipts；UI未知ACK/旧GET定向 | PASS |
| INT-AC-005 | 提前交卷 | 真实UI 1已答5未答confirm冻结；PG submit/freeze竞争 | PASS |
| INT-AC-006 | 报告一致 | 真实report13.35=80.125/6、5UNANSWERED0、重复GET一致；PG53.42/80.13 | PASS |
| INT-AC-007 | 系统失败不0 | SDK503/429/timeout/badJSON与PG mixed pending/failed：submittednull/unanswered0/totalnull | PASS |
| INT-AC-008 | 原工作区回归 | UI retained/focus27fixture；最终原workbench/focus/community+interview117套件全通过，无failed/flaky/retry | PASS |
| INT-AC-009 | 简历快照 | PG Mandatory snapshot/version；真实UI A/B不同配置、current删除留A；restoreHTTPsnapshotA | PASS |
| INT-AC-010 | Markdown安全 | 共享strictUTF8/BOM/control与no remote img；SDKsystem/user完整JSON/no tools真实计数 | PASS |
| INT-AC-011 | 个人中心输入 | 真实File先editor后保存编辑文本，FAIL/limits preserve；没有创建临时上传入口 | PASS |
| INT-AC-012 | RustFS私有原件 | 真实default/proxy/restore bytes SHA；anon403/owner404/head/cache/no provider URLs | PASS |
| INT-AC-013 | 唯一current | 真实PG firstwrite/importCAS/rollback/readonlycurrent/单调version | PASS |
| INT-AC-014 | server当前简历 | snapshotForInterview owner/version严格、HTTP伪造/strictDTO；真实A/B来源 | PASS |
| INT-AC-015 | 方向难度 | 四enum/三difficulty frozen、SDK prompt/PG矩阵、真实Java/MID与React/SENIOR | PASS_ENGINEERING |
| INT-AC-016 | GET不调模型 | 真实SDK一次count、PG GET计数、restoredGET jobcount不增、UI纯read/hiddenpoll | PASS |
| INT-AC-017 | 四方向 | strict enum/numeric400、rubric React非Vue、全栈跨层；真实Agent通用第三场 | PASS_ENGINEERING |
| INT-AC-018 | JD快照/失效 | 真实JDparse、direction变移除；PG owner/rawhash/reuse/删除JD后create重放；restoreJD | PASS |
| INT-AC-019 | JD失败保文 | normalnoKey真实FAILED保raw；UIfixture失败manualidretry/mismatch不静默ignore | PASS |
| INT-AC-020 | current替换/删除 | 真实UI currentdelete保history；PG modeEDIT/PASTE失败旧current、restoreB/fileA | PASS |
| INT-AC-021 | 场次删除 | PG各阶段旧token/receipt不复活；真实deletefirst404/second200 | PASS |
| INT-AC-022 | 原件清理/备份 | 真实PG lateput/uncertain/delete重试；实际2objectbundle/freshrestore/31table SHA/negativeguards | PASS |
| INT-AC-023 | 输入上限 | HTTP/PG codepointBMP/emoji精确/+1和malformed；proxy1MiB transport+text composite门禁 | PASS |
| INT-AC-024 | 中断/fencing | 实际JVM kill/newJVM0calls/只manual；queue reject/expire/clocktimestamp最后CAS trigger0子行 | PASS |
| INT-AC-025 | 最大组合 | 真实PG20main40turn+20kresume10kJD每答5kemoji完成80.13；SDK最大完整wire capture | PASS_ENGINEERING |
| INT-AC-026 | 多场未完成 | 真实UI两配置draft/submit/刷新重登+第三通用独立；PG replay/delete不影响他场 | PASS |

详细可复查记录：resume/engine/UI各validation与check-report，父research/browser-real-validation、proxy-validation、ops-real-validation、final-ops-review及271test Surefire。最终完整117浏览器套件已exit0（13community/14focus/28interview/62workbench），另一次明确“已完成”报告真实链1PASS；所有26项工程验收均已取得相应层级真实证据。

原输入与正式prd52FR/AC无损继承；1MiB字节边界在validator/transport层验证，完整API还受20k文本上限，不能声称1MiB UTF8原文满足20k。实model质量/latency/context/cost DEFERRED；本地业务无默认禁用gate/compatlayer。
