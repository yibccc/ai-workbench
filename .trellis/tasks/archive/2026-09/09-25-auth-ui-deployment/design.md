# 身份界面与入口设计

## 本地结构与组件边界

`frontend/src/App.tsx` 目前直接取项目并构造四个 `RetainedView`；`navigation.ts` 的四个主入口固定；`AppShell.tsx` 有桌面 `.sidebar-footer` 与手机顶栏；`api/http.ts` 只包装非 2xx 错误；`ToastProvider.tsx` 以 page 过滤且切页清理；`hooks/realtime.ts` 的 pending ID 使用不分身份 localStorage。`DialogProvider` 在 `main.tsx` 根部，既有规范要求 Dialog 焦点与取消行为，单屏布局规范要求固定控件可达、内部滚动。管理页作辅助视图/路由，不加入 `navigation`。

在应用根部先解析当前身份，再装载已认证工作台；登录态以 userId 为作用域。退出、真实 401 或身份更换时取消旧请求、关闭 STOMP/轮询、清理或按 userId 隔离 pending ID 与业务缓存，并卸载旧身份的 `RetainedView`；迟到响应需校验发起时的身份世代，不能仅靠视觉隐藏。普通同身份切页仍保持已访问视图与草稿。密码和 Session 令牌只经 HttpOnly Cookie/安全提交，不写 localStorage/sessionStorage；既有折叠偏好等非业务设置另行检查，不把它当用户数据。

HTTP 客户端统一带同源 Cookie 与 CSRF 写请求凭证；只把已确认的认证 401 转为登录状态丢失。403 是管理权限不足，404 是资源不可见/不存在，网络或 STOMP 断线不自动踢出。认证失效要同时切登录页和调用现有 Toast；将 Toast 状态放在身份视图/页面切换之外，保留 5000ms 重计与关闭行为，普通业务提示继续沿用隐藏页不弹出的规则。失效后的业务内容即刻不可交互，不等提示消失。

P-001 的客户端合同：仅在已认证工作台中的明确用户交互（点击业务控件、键盘编辑、手动业务请求）向后端发送受认证、CSRF 保护的活动信号；可合并/限频，避免每次按键都发请求，但持续主动使用必须能及时延后期限。跟踪对象的15秒轮询、自动 STOMP 重连/订阅、心跳、纯推送和被动加载不发该信号；普通业务 `fetch` 不能默认标成主动。服务端活动标记是期限权威，客户端本地计时不替代它。多标签页同一 Session 共享活动期限，换账号后旧交互/迟到信号不得刷新新身份。

桌面侧栏底部和手机顶栏各提供同一账号菜单；菜单支持改密/退出，ADMIN 多用户管理。用户管理辅助页包含账号列表（用户名、角色、状态、创建时间、操作）、创建/启停/重置/角色 Dialog；复用 `DialogProvider` 与现有样式/焦点规则。登录页只含用户名、密码显隐和提交状态；生成图可借配色/层级，不引入图中的搜索、铃铛、常驻 Session 提示或重做四工作区。

## 部署与兼容

`deploy/nginx.conf` 目前在整站 `server` 上启用 `auth_basic`，`deploy/40-workbench-auth.sh` 生成 htpasswd，`frontend/Dockerfile` 安装 `apache2-utils` 并把脚本接入 Nginx entrypoint，`compose.yaml`/README/`.env.example` 使用 `WORKBENCH_AUTH_*`。应用 401/403、STOMP 握手/订阅和 direct backend 访问验证后，统一移除旧凭据/脚本/构建依赖与浏览器挑战；保留 `/api/` 与 `/ws/` 代理、`Host`/`X-Forwarded-Proto`/Upgrade、精确 Origin、`/actuator` 阻断与仅容器 loopback 的 `/health`。同步更新 README、环境模板、Dockerfile/入口脚本及 `.trellis/spec/backend/linux-deployment.md`，不能让旧规范继续要求 Basic Auth。

Windows 本机 Vite/JAR 以前依赖入口外无认证，切换后也须通过应用登录；Compose 保持单 backend、PostgreSQL/Redis 原网络和卷边界。生产 Cookie Secure 依赖 HTTPS 外层代理的正确转发；对本机 loopback HTTP 用明确开发配置。无清真实数据动作；旧 raw WS 页面与新 STOMP 服务不兼容，匹配前后端发布并提示刷新。回滚不把旧无 owner 应用连到多人库。

`frontend/playwright.config.ts` 当前用独立 `d9_e2e` schema 与 Vite 代理，`workbench.spec.ts` 每例在直连 backend 上匿名 reset 后写 API。改造后的浏览器 fixture 须先建立受限 E2E 管理/普通身份、登录与 CSRF，再在对应身份下发请求；reset 仍只在 E2E profile/隔离 schema 可用，正式 profile 没有匿名清理能力。测试不能通过临时关闭 Security/CSRF 来维持旧脚本。
