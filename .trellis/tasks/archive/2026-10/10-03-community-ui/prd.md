# R2 原型复用与广场端到端交付

## 目标与依赖

把已批准的R2全部业务页面/状态源码布局接入真实工程，用户可以选择素材、保存/发布、成员阅读/下载、撤回或管理员下架，同时保留原工作台与唯一账号专注生命周期。

父`../10-03-community-oss/prd.md`为所有AC唯一正式合同，父design为固定API/路由/布局/身份约定；父research/frontend记录实际复用锚点。合同固定后可以做界面骨架，最终联调/验收依赖publishing+storage均完成并check通过。

## 范围

FR-02/03/04/05/07/08/09/10的UI：R2导航、广场、素材跨页、编辑/附件/预览、详情/作者/我的发布、真实登录与错误/空状态，手动save/dirty guard/409、图片大图/PDF-MDdownload、ADMIN hide理由/profile编辑。严格现行hash扩展、login允许目标返回，account root/RetainedView不随space重建。

## 验收

- [ ] 父AC-22完整R2源码+17图布局对照表/正式截图；所有区域/主要操作/桌面移动顺序保留，无demo fixtures/身份/状态/固定focus。
- [ ] AC-02/03/04/06/10/20真实跨层操作，默认不选/两页选择/日期切换、save不公开/显式更新、首次时间固定、失误/409输入保留。
- [ ] AC-07/08/09：往返保持原5工作区草稿/筛选、同一focus/声音；真实401清保护视图，403/404/network不误退出；账号切换取消旧请求/Blob/download状态。
- [ ] AC-13～21相关UI：上传失败定位重试、publish只READY、匿名/旧附件拒绝、真下载sha一致、无reader云URL/PDF-MDpreview、MD不改正文。
- [ ] AC-05/11/12：明确本站成员可见，无互动；撤回/下架下一reader请求不可访问，作者无隐藏后重发能力。
- [ ] 原已有lint/build/E2E/focus-alarm回归与新community流程通过；测试夹具截图不当业务PASS。

## 非目标与状态

不重绘新布局、不使用原型innerHTML/权限/简易Markdown、不加路由兼容或库、不改原focus行为，不增加历史restore/社交/匿名/云功能。产品决定均继承R3；父最新最终规划已获用户“开工/批准并连续执行”，本任务已in_progress并行搭建骨架，完整联调依核心/存储交付。
