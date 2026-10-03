# acceptance — AI Workbench AI 面试首版验证场景

> 本文件是**验证计划**，不是测试结果。所有“建议层级”只表示适合在哪一层验证。

| AC | 前置条件 | 操作 | 预期 | 建议验证层级 |
|---|---|---|---|---|
| INT-AC-001 | A/B/ADMIN 各有私有资料 | 交叉读写他人简历、会话、答案、报告 | 无内容/状态泄露；写入不发生；owner 正确 | HTTP + PostgreSQL + Redis |
| INT-AC-002 | 可控 AI gateway | N=3/5/20；注入少题、空追问、错 parent、超量 | 成功严格 6/10/40 轮；非法题单不可作答 | Service + DB + AI contract |
| INT-AC-003 | 当前轮可提交 | 首提后双击、旧轮重提、旧标签页、并发提 | 答案不覆盖；进度只推进一次 | HTTP 并发 + DB |
| INT-AC-004 | 当前轮有草稿 | 暂存、刷新、重登；模拟提交响应丢失 | 草稿恢复不推进；真实提交状态可恢复，不双推进 | Browser + HTTP |
| INT-AC-005 | 有未答轮次 | 提前交卷并确认 | 明示未答按 0；答卷冻结；未答不能补答 | Browser + API |
| INT-AC-006 | 固定有效评分 | 生成成功报告，多次读取 | 总分等权正确；历史结果稳定 | Service + API |
| INT-AC-007 | 超时/坏 JSON/缺题/错题号 | 触发评估失败并查看 | 不发布完整总分；系统失败不当用户 0；可重试 | AI contract + API |
| INT-AC-008 | 现有页面有草稿/筛选，专注运行 | 进入/离开新功能 | 原状态保留；专注会话不变 | Playwright/E2E |
| INT-AC-009 | current resume A | 建面试1，改 B，再继续/重试1并建2 | 面试1=A；面试2=B | DB + API |
| INT-AC-010 | 简历含 HTML/script/链接/伪 prompt | 保存并用于 AI | 只作数据，不执行、不抓取、不提权 | Unit + prompt contract |
| INT-AC-011 | 已有 current resume | 合法/非法 md、粘贴、制造保存失败 | 仅简历维护入口可改；失败不覆盖 current/编辑内容 | Browser + storage |
| INT-AC-012 | A/B 各有导入原件 | 交叉读取与检查网络 | 仅 owner 后端读取；无公开/预签名 URL | HTTP + RustFS |
| INT-AC-013 | 同用户两页面 | 同版本并发保存不同简历 | 至多一个 current 成功；另一方冲突/刷新 | DB 并发 |
| INT-AC-014 | current resume v2 | 伪造旧/他人 resumeId 或正文创建 | 只允许 current v2 或无简历 | API + DB |
| INT-AC-015 | 四方向/三难度 | 各创建并评估 | 配置冻结；React 非 Java；初级非高级 rubric | AI contract + API |
| INT-AC-016 | 已有成功报告 | 重复列表/详情/report GET/轮询并计 AI 调用 | 读取不新增模型调用 | Counting fake gateway |
| INT-AC-017 | 创建接口可用 | 四方向与非法方向；检查 React/全栈样例 | 仅四方向；React 不默认 Vue；全栈跨层 | API + AI fixtures |
| INT-AC-018 | JD 已解析 | 修改 1 字符再创建；重新解析再创建 | 旧分析失效；新快照固定 | Browser + API |
| INT-AC-019 | 可控 JD 失败/不匹配 | 解析失败后尝试创建 | 原文保留；不换方向/不静默忽略；明确移除后才无 JD | Browser + API |
| INT-AC-020 | current A + 历史快照 A | 保存失败、替换 B、删除 current | 失败仍 A；成功唯一 B；历史 A 保留；通用仍可建 | API + DB + storage |
| INT-AC-021 | 生成/作答/评估不同阶段 | 删除并制造迟到任务/重放 | 业务不可读；迟到不恢复；其他数据不变 | DB + TaskExecutor |
| INT-AC-022 | 引用/无引用/清理失败对象 + PG 备份 | 替换/删除/清理/隔离恢复 | 引用对象不删；失败可重试且不可读；PG/RustFS 恢复分开 | RustFS + ops |
| INT-AC-023 | 边界文本/文件 | 测 20k/+1、10k/+1、5k/+1、1MiB/+1 | 上限允许；+1 拒绝；不截断/覆盖/推进 | Unit + API + Browser |
| INT-AC-024 | 可阻塞任务/可重启 | 中断 PROCESSING、重启、显式 retry、旧任务迟到 | 不永久卡；不自动 retry；旧 token 不覆盖 | Integration + restart |
| INT-AC-025 | 隔离库、合成最大数据 | 20 主问题、40 轮、最大输入 | 完整成功或明确失败；无残缺题单/伪总分/静默截断 | Performance/contract |
| INT-AC-026 | 可创建多场 | 两场不同配置分别暂存/提交、刷新/重登、新建第三场、删一场、重放 requestId | 各场独立；删一场不影响其他；requestId 不重复 | Browser + API + DB |

## 建议回归范围

- auth/CSRF/session idle；
- records/tasks/focus/reports/projects；
- community publishing/private attachments；
- 原社区 RustFS 对象前缀权限；
- Flyway fresh schema + 当前数据版本升级；
- Windows local start/stop/backup isolated restore；
- frontend lint/build/E2E；
- backend Maven verify。

> 本交接包没有执行上述验证，没有执行结果。
