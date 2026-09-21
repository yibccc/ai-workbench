# 后端兼容性初查

## 输入与当前版本

参考包：changes/workbench-ui-refactor.zip，用户提供的正式src与changes.patch。预览HTML已在Chromium打开并检查桌面效果。

按UTF-8读取并统一LF后逐文件比对：capture/http/pagination/projects/records/reports/status/tasks八个API模块，以及hooks/realtime.ts，均与当前源码一致。此为文本比对，包内演示的验证结论另以本项目实际测试复核。

## 交互与已有接口对应

| 新界面需要 | 已有能力 | 初查结论 |
|---|---|---|
| 项目独立页/搜索/包含归档 | GET /api/projects/page的q、includeArchived和PageHelper分页 | 可复用 |
| 单行项目选择超过50项 | GET /api/projects?includeArchived=true返回完整列表 | 去掉前端人为截断即可 |
| 记录抽屉编辑 | GET /api/records/{id}、PUT /api/records/{id} | 可复用 |
| 任务抽屉与版本冲突 | GET/PUT /api/tasks/{id}，version字段 | 可复用 |
| 任务完成/重开/补充结果 | complete/reopen/completion-result专用操作 | 保留事务合同 |
| 日报/周报切页与来源侧栏 | reports/page、report详情和sources/page | 可复用 |
| 日报删除 | DELETE /api/reports/{id}?version | 保留软删除及跨窗口事件 |
| 跨工作区后台AI | /ws/events与GET详情、已有input/retry/revert | 由前端保留组件/订阅状态实现 |

真实隔离后端全套 19/19 浏览器回归通过（retries=0），包括分页、105 个项目、记录与待办、报告保存/来源/删除、实时恢复及筛选/草稿保留。本子任务无需修改后端：新工作区、抽屉和状态保留由前端完成，现有 API 已满足需求。

联调发现的项目归档后无法立即搜索，根因是确认弹窗在写成功后继续等待列表读取，导致背景保持 inert；已在前端解除该等待。日报跨窗口删除检查改为等待业务 `/ws/events` 握手（而非 Vite 热更新 WebSocket），原后端事件和前端订阅可直接复用。

旧版以key刷新TasksPanel造成筛选重置，以及旧查询内容短暂显示在新日期下，属于前端状态管理问题。
