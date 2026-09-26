# 交付回流：专注与每日重复任务

handoff_id / revision：`WB-20260926-focus-routines` / `2`
Trellis 任务：`.trellis/tasks/09-26-focus-routines`（`in_progress`）
状态：本地实现与自动化验收完成，人工设备验收待确认；未推送
业务 commit：`dc56cf07589212ff0b4acfe6acec0a49c6c851be`
review base / reviewed HEAD / 工作区指纹：`3d1293095a3370403f27e051fa0639141a3c24b5` / `dc56cf07589212ff0b4acfe6acec0a49c6c851be` / Git tree `c94f8c2077f74b83efaed300b94f6a98ac642604`
验证记录：`.trellis/tasks/09-26-focus-routines/research/acceptance-audit.md`、`rollout-drill-result.json`
审查记录：`.trellis/tasks/09-26-focus-routines/research/quality-check.md`
后续归档/日志 commit：未生成；任务仍需人工设备验收后收尾。

## 实际交付

- V15 新增重复规则、软删后也不补造的每日实例唯一约束、同 owner 会话与区间账本、同会话同日专注记录。待办带入上下文，明确点击才开始；结束不自动完成待办。
- 独立“专注”页、五项导航、离页紧凑控制、15 秒可见微休息与声音失败反馈；失联缺口需确认，未确认时间不入账。记录页只显示已结算来源。
- 真实净投入按业务日切分并进入日/周报告冻结来源。纯专注来源的报告进展由冻结事实确定性渲染，模型不能把它伪写成完成成果。
- `FOCUS_WRITE_ENABLED` 默认关闭新规则/实例/会话写入；已有会话仍可收尾。README、Compose、环境模板及 Trellis 代码规范已同步。

## 与原方案的偏差

- 迟到检查点跨过提醒阈值时，把响应前已可靠经过时间计为专注，从用户实际看到提示时才开始 15 秒微休息；不追溯生成用户未见的休息。精确 25:30 场景以按时触发的可控时钟验证。该语义不改变产品目标，但前台提示 ≤3 秒仍缺实际设备与网络测量。
- 发布开关关闭的是新规则、今日补齐和新会话；已有会话的检查点、恢复、结束和进展仍可写，以免回退时困住未结束会话。兼容读取版本使用本次认识 `FOCUS_SESSION` 的二进制。

## AC 验收

| AC | 结果 PASS/FAIL/NOT_RUN | 实际证据 | 人工验收 |
|---|---|---|---|
| AC-001 | PASS | 真实 PostgreSQL 唯一键、并发 HTTP 补齐、软删不补造、星期/历史快照测试 | 无设备依赖 |
| AC-002 | PASS | 可控时钟净 1500 秒、休息 30 秒、经过 1530 秒；任务关联/临时开始与暂停测试 | 无设备依赖 |
| AC-003 | NOT_RUN | 自动化已覆盖引导、跳过/关闭、音频 API 拒绝和不补播；实际可听及 ≤3 秒仍待测 | Chrome/Edge 前台声音与入口待验 |
| AC-004 | PASS | 并发/重试单日结算、跨日分片、独立事务故障回滚、日周冻结来源及恶意模型文字测试 | 真实模型未调用，按规划无需付费试验 |
| AC-005 | NOT_RUN | 自动化已覆盖同会话恢复、60 秒阈值、时钟跳变、被动检查点不续期；真实睡眠未测 | 设备睡眠/锁屏恢复待验 |
| AC-006 | PASS | A/B/ADMIN 所有权与 CSRF、上海跨日、任务完成循环、记录/报告回归、NY 浏览器时区 | 外部 HTTP/IP/HTTPS 入口未在本机配置完成 |
| AC-007 | PASS | Chromium 五入口、默认记录页、独立专注页、320/390/760/1440 像素、草稿/历史/紧凑入口 | 实机焦点与页面观感可补验 |

## Review 问题与处理

- 独立检查发现规则更新缺版本原返回 409，已改 400 并补 409/ADMIN 跨 owner 测试。
- 报告模型可在“进展”凭纯专注来源谎称完成，已改为冻结来源确定性文字，并以日/周恶意输出测试复核。
- 旧迁移测试借用 Hikari 连接后未恢复 `search_path`，已在 `finally` 恢复；完整隔离门禁复跑通过。登录失效 Toast 的切页竞态、开始与结束响应丢失后的恢复亦已修复并经浏览器复跑。
- `research/quality-check.md` 记录仍需人工证明的声音、设备睡眠与入口矩阵；这些项目未被自动化结果代替。

## 规范沉淀

- 新增 `.trellis/spec/backend/focus-routines.md`、`.trellis/spec/frontend/focus-page.md`；更新数据库来源枚举、连接池测试隔离、账号级 Toast 生命周期、五页导航与布局规范。

## 残余风险与下一轮

- 本地无音频感知与物理睡眠证据。需要在实际 Windows Chrome/Edge 前台分别验证开始/结束声音、拒绝时的可见降级、双标签只响一次、≤3 秒提示和真实睡眠恢复；记录浏览器版本、入口 URL、设备/声音状态及结果。未测前 AC-003/005 不判 PASS。
- 现有本机仅验证 loopback HTTP。若交付环境提供直接 IP HTTP 与 HTTPS 入口，应分别跑相同的登录、声音/CSRF/WebSocket 与回退检查；未配置入口不宣称通过。
- 隔离测试环境保留在独立 Compose 项目 `focus-routines-20260926`，未使用或改动日常 `ai-workbench` 卷。结束人工验收后可按明确项目名停止该测试环境。

## 发布与恢复

- 本地分支 `feat/focus-routines-r2`，基于 `docs/wb-auth-isolation-delivery` 的 `3d1293095a3370403f27e051fa0639141a3c24b5`；未推送、未建 PR、未合并、未部署。
- 隔离 JAR 对 `focus_routines_test.d9_focus_rollout_20260926` 已演练默认关写→启写→再关写：新会话 409 / 可写并形成单条记录 / 关写后原记录仍可读。V14→V15 合成旧数据升级与 V1～V14 checksum 由独立迁移测试验证。写入新来源后不能直接回退到只识别旧来源的二进制；用兼容读取版本关闭新写，保留新表和历史记录。
