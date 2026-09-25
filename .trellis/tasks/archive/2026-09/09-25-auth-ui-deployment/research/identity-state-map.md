# 前端身份切换状态核对（实施前只读）

当前 `App.tsx` 启动即取项目列表，四个 `RetainedView` 在首次访问后持续挂载，保留草稿、筛选与请求状态。新认证根边界应只在用户身份变化时卸载旧工作台；普通同身份切页仍保留视图。项目加载的 `AbortController` 仅覆盖其初次请求，其他功能也各自持有请求控制器，不能只清空 `App` 的 projects 就声称已隔离。

| 当前状态点 | 所在文件 | 换账号时的核对动作 |
| --- | --- | --- |
| 全局 pending ID、socket、重连计时器、15 秒兜底、事件去重/修订缓存 | `hooks/realtime.ts` | 停止旧身份连接与计时器，取消旧订阅和回调；pending ID 按 userId 隔离或清除，旧 `ai-workbench.pending.v1` 不可成为新身份待跟踪资源。纯后台兜底不发送主动活动信号。 |
| AI 输入与报告待跟踪结果 | `features/capture/AiCapturePanel.tsx`、`features/reports/{DailyReportPanel,WeeklyReportPanel}.tsx` | `trackedIds` 重读、迟到 `fetch`、当前选中版本/草稿和请求控制器须受身份世代限制，A 结果不能覆盖 B。 |
| 分页、项目、工作区状态 | `App.tsx`、`hooks/usePagedList.ts`、各业务 feature | 身份变化卸载旧 `RetainedView` 和项目缓存；同身份导航仍保留草稿/筛选。迟到响应只能写发起时的身份视图。 |
| Toast | `components/ToastProvider.tsx` | 当前按 page 过滤、切页清除；真实 401 后登录页要保留同一条 5 秒失效提示。普通业务提示仍遵守隐藏视图不弹规则。 |
| 折叠偏好 | `components/CollapsibleSection.tsx` | localStorage 中是展示偏好，不是业务内容；核对其键不含凭据。它可以保留为通用偏好，不能用它保存账号/Session 或 pending 业务对象。 |
| HTTP 统一错误 | `api/http.ts` | 现有客户端只把非 2xx 包为 `Error(status)`；新增身份边界只把真实 401 作为失效，403/404/网络失败不得统一踢出。带 Cookie 和写请求 CSRF。 |

主动续期 P-001：认证工作台内点击、键盘编辑或明确手动业务请求调用受保护的活动信号；`realtime.ts` 自动重连/订阅、15 秒兜底、纯推送以及被动初始加载均不能调用它。实际端点与 DTO 以认证子任务完成后的后端契约为准。
