# 前端身份与部署实施计划

前置：父任务最终规划获用户后续明确确认；认证/归属/实时协议合同已确定。只在本子任务由原生 Trellis 激活后实施。

1. 建立登录态根边界、登录页、当前身份读取、统一 HTTP 401/403/404/CSRF 分类；退出与换账号取消旧请求、卸载旧业务树并清理身份专属 pending/连接。保持同用户 `RetainedView` 普通导航的草稿。
2. 在 `AppShell` 桌面侧栏底部/手机顶栏接账号菜单，ADMIN 用户管理辅助页；复用 `DialogProvider` 实现创建、自改、重置、角色/启停表单与副作用提示。无第五主导航、搜索/批量或通知中心。
3. 改 `ToastProvider` 的登录切页生命周期，真实失效立即跳转且保留 5 秒提示；普通失败留原语境。与实时子任务完成 STOMP 客户端、用户专属 pending 跟踪。对明确用户点击/键盘编辑/手动业务操作发送受保护且限频的活动信号，后台查询/自动协议通信不发送；用父任务本地 TC-017～019 验证服务端期限。
4. 应用鉴权端到端验证后，移除 `deploy/nginx.conf` 的 Basic Auth、`40-workbench-auth.sh` 的接入、Compose/环境模板/README 中旧共享凭据；保留代理/HTTPS/健康边界，并同步旧部署规范。Windows 本机和 Compose 路径各自烟测。
5. 用浏览器与 API/协议完成 TC-031～035、TC-038/040；覆盖桌面/手机/短视口、对话框取消焦点、真实 5000ms Toast、A→B 迟到响应、未保存草稿及旧页面刷新。执行 `frontend/` 下 `npm run lint`、`npm run build`、`npm run e2e`；后端 `mvn clean verify` 与隔离 Compose/HTTPS 烟测作为跨层 review 门禁。WSL Ubuntu Docker/Compose 可用，本轮仅验证配置解析；运行烟测前核实独立项目/schema。

入口变更回滚点：保留验证过的匹配前后端/代理配置和数据库快照；应用鉴权未覆盖 API/STOMP 前不得移除 Basic Auth，新多人库不可用旧无 owner 应用版本访问。
