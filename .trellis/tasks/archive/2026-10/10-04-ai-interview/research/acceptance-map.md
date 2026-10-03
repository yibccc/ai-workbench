# FR/AC本地验收分工

正式条件唯一正文在父prd，原场景在research/handoff/r1/acceptance.md；此表补本地方法/所有权，不重定义AC。规划期全部NOT_RUN。真实模型质量/费用与OSS延期须独立记为DEFERRED，不计为已通过；AC-025工程合同使用可控假模型覆盖。

| AC | 负责子任务 | 本地方法与关键证据 | 当前状态 |
|---|---|---|---|
| INT-AC-001 | resume+engine+ui | 真实HTTP+PG/Redis：A/B/ADMIN越权、owner SQL/响应元数据/CSRF | NOT_RUN |
| INT-AC-002 | engine | fake gateway N3/5/20及少/多/空/错parent，整体2N入库 | NOT_RUN |
| INT-AC-003 | engine+ui | 独立PG事务并发、旧版本/轮次、submit receipt/只进一次 | NOT_RUN |
| INT-AC-004 | engine+ui | 浏览器草稿恢复/丢ACK原requestId、刷新/重登真实进度 | NOT_RUN |
| INT-AC-005 | engine+ui | 提前交卷已答未答确认/冻结、submit竞争/未答0 | NOT_RUN |
| INT-AC-006 | engine | 固定scores后端2N平均、HALF_UP/持久report纯读 | NOT_RUN |
| INT-AC-007 | engine+ui | fakeHTTP超时/坏JSON/错ID/缺分：总分null/失败不0 | NOT_RUN |
| INT-AC-008 | ui+parent | 五区retain草稿/筛选、唯一focus、六导航切换E2E | NOT_RUN |
| INT-AC-009 | resume+engine | A建1后current B、继续/retry1仍A，新建2为B | NOT_RUN |
| INT-AC-010 | resume+engine+ui | Markdown/script/URL/伪指令仅数据；无执行/抓取/提权 | NOT_RUN |
| INT-AC-011 | resume+ui | import先editor/File保留，失败/超限保旧current和输入 | NOT_RUN |
| INT-AC-012 | resume | 真实HTTP→RustFS：owner原件、匿名/foreign/HEAD无字节、无云URL | NOT_RUN |
| INT-AC-013 | resume | current singleton/version CAS真实并发和删后ABA | NOT_RUN |
| INT-AC-014 | resume+engine | create只认server current/version或NONE，伪正文/foreign无效 | NOT_RUN |
| INT-AC-015 | engine | 四方向/三难度固定fixture+prompt contract一致 | NOT_RUN |
| INT-AC-016 | engine+ui | SDK fakeHTTP计数+GET计数：仅初次动作/显式retry调用 | NOT_RUN |
| INT-AC-017 | engine+ui | 四enum非法拒绝；React无默认Vue/全栈跨层样例 | NOT_RUN |
| INT-AC-018 | engine+ui | JD精确UTF8hash+owner+direction，改1字旧analysis失败 | NOT_RUN |
| INT-AC-019 | engine+ui | JD失败保文/不换向/不静默ignore，显式移除 | NOT_RUN |
| INT-AC-020 | resume+engine+ui | 替换失败旧current、成功唯一、delete不删历史快照 | NOT_RUN |
| INT-AC-021 | engine+ui | 各阶段delete，迟到token/receipt重放不复活，不影响别场 | NOT_RUN |
| INT-AC-022 | resume+parent | 当前/活动引用保护、cleanup失败/恢复、新DB+桶hash恢复 | NOT_RUN |
| INT-AC-023 | resume+engine+ui | Unicode码点等号/+1/非BMP；MDbyte gate等号/+1与API复合上限 | NOT_RUN |
| INT-AC-024 | engine+parent | 真实restart/expiry，lease开始领取、旧token终态失败、无自动调用 | NOT_RUN |
| INT-AC-025 | engine+parent | 20main/40turn与最大输入fakegateway+fakeHTTP：完整成功或明确失败 | NOT_RUN |
| INT-AC-026 | engine+ui | 两场+第三场真实恢复/独立草稿/删除隔离/create receipt | NOT_RUN |

## FR责任与跨层验收

- resume：FR-001～004/021/023；涉及FR-008快照读取、FR-022 owner、FR-024数据安全和FR-025/026存储迟到/中断处理。
- engine：FR-005～020/022～026；依赖resume FR-001～004版本与快照API。JD/generation/evaluation失败仅手动模型重试，draft/submit/complete读写在owner短事务。
- ui：FR-001～026的可见入口/状态；不提供额外格式/第五方向/动态追问；直接源码复用，源HTML/PNG矛盾有记录。
- parent：全26INT-AC、原五工作区/auth/community/no-idle-refresh回归、真实恢复与文档一致性；不能只用各子报告替代全量整合。

INT-AC-023：1MiB等号仅byte gate可PASS；完整导入仍需decoded正文<=20k。分别记录file stage、复合API、前端input保留证据，不声称构造了同时20k码点的1MiBUTF-8正文。
INT-AC-016/024：需要真实AgentScopeSDK+本地计数HTTP，mockInterviewGateway测试只能证明业务调度，不能证明SDK内部无retry。
