# 人工设备验收入口（2026-09-26）

本轮已在 Windows 本机启动独立测试实例：`http://127.0.0.1:15174`。后端 `127.0.0.1:18083`、PostgreSQL `focus_routines_test.d9_focus_manual_20260926`、Redis `127.0.0.1:26379`，与日常 `ai-workbench` 数据卷隔离。启动检查结果：前端 HTTP 200、后端 health UP、合成管理员登录成功，`/api/focus/capabilities.writeEnabled=true`。运行 PID 和路径只保存在本机 `%TEMP%/focus-manual-runtime.json`，仓库未保存密码。启动脚本为 [start-manual-acceptance.ps1](start-manual-acceptance.ps1)；验收后可由 [stop-manual-acceptance.ps1](stop-manual-acceptance.ps1) 按 PID 与命令行双重核对后关闭这两个专属进程。

## 待人工记录

| 场景 | 操作与应记事实 | 当前结果 |
|---|---|---|
| Chrome/Edge 前台声音 | 各记浏览器/版本、声音设备与入口；启用并试听。以目标 2 分钟、提醒间隔 1 分钟开始，观察约 1 分钟时可见微休息和开始提示音，15 秒后结束提示音；记录提醒阈值到可见/可听的实际延迟是否 ≤3 秒 | NOT_RUN |
| 音频拒绝与双标签 | 在一个标签禁用声音或用浏览器权限拒绝，确认可见降级；同账号第二标签打开同会话，记录同一提醒是否只响一次 | NOT_RUN |
| 真实设备睡眠/锁屏 | 开始会话，记录最后可靠净时长，设备睡眠或锁屏超过 60 秒后恢复；在用户选择前确认缺口不计净时长/休息次数，再分别记录确认或舍弃后的结果与是否补播过期提示 | NOT_RUN |
| 实际入口矩阵 | 若交付环境有直接 IP HTTP 与 HTTPS 域名，分别记录 URL、浏览器/版本、登录/CSRF/WebSocket 与声音结果；本机 loopback HTTP 已用于自动化，不能代替其他入口 | NOT_RUN |

不要把 API 播放调用次数当成“人已听见”的证据；设备静音与浏览器拒绝应分开记录。浏览器后台、锁屏、关闭页面时不承诺准点响铃。上述证据归档到 [acceptance-audit.md](acceptance-audit.md) 后，再把 AC-003/005 从待验改为实际结果，并更新交付状态。
