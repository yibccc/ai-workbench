# R2复用与集成执行清单

前置：父批准最终规划，API合同固定。本任务完整联调依赖publishing和storage都check通过；骨架可与不同文件后端工作并行，必须遵守文件所有权。所有命令环境见父implement/storage研究。

1. 逐页阅读R2源码与17图/无图状态，登记source→React/CSS复用表，不生成新设计；安装pin react-markdown并保持lock一致。
2. strict hash parser/站内login return与R2 gated shell真实LoginPage，AppShell社区分支与root生命周期，保留原5工作区/ADMIN。
3. HTTP共享identity/CSRF fetch管线加Blob/FormData支持，objectURL cleanup与真实401处理，不改变旧JSON接口。
4. 广场分类/detail/author/my-posts、profile维护，源分页选择/日期、三类型编辑/手动save/preview/publish-update，upload/retry/remove/image lightbox/download、withdraw/admin hide共享Dialog。
5. 本地输入/dirty/版本409、晚到请求/账号epoch隔离；scope固定MEMBERS，source公开字段/专注单独项，no demo/interaction。
6. 桌面/手机全部R2对照截图，geometry/键盘/focus/长正文及错误空状态reachability，所有已批准区域不漏；状态夹具单独标记。
7. 新community.spec真实HTTP+backend+RustFS流程，匿名深链login-return/撤回、A→B晚到upload、两页source/两日期、F1/F2、files真实sha与原focus/工作区回归。
8. `npm --prefix ./frontend run lint`、`build`、`e2e`，原focus-alarm suites按实际script一起跑；最后trellis-check跨层全scope，不只最后UIdiff。

截图和实际命令/exit/环境结果写validation，更新父22AC/21TC证据；使用Mock的UI状态只能布局取证。普通已修问题留最后报告，主要布局/兼容改变须用户具体确认。未授权git/云部署。
