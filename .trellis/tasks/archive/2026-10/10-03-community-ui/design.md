# R2正式UI设计

完整合同见父`../10-03-community-oss/design.md`，真实源码/CSS/截图锚点见父`research/frontend.md`。以原型app.js页面区域和styles.css直接提取React组件/作用域CSS，fixtures/演示控制/原始innerHTML不入生产。社区shell218/192px侧栏/移动导航按R2；100dvh根下有命名正高度`.community-scroll`，导航/手机bottom bar在外、原main宽度/分组在内，global reset/portal样式作用域处理；同账号root下留RetainedView与唯一focus，原工作区CSS不改换。

所有权：`features/community/`、`features/publishing/`、对应api TS，strict hash parser、App/AppShell/layout条件分支、LoginPage布局适配、HTTP共享fetch decode管线、feature CSS/import、frontend package lock与community E2E。只为新增space必要调整App与http，绝不复制第二套鉴权/CSRF/epoch。

request现有FormData/identity语义保持，提取JSON/Blob decoder共用身份管线；所有图片objectURL在失效/换号/不再引用时撤销。react-markdown10.1.0/skipHtml/安全protocol + licensed attachment UUID组件，不开rehype-raw/GFM，外链图片不请求。PDF/MD下载Blob而非云URL；大小/错误UI不是服务端额度权威。

编辑本地输入与server draft/version分开，单稿writes/upload串行但用户可继续输入，ack不能覆盖在flight期间的新输入；保存清dirty只在对应输入未改变时。预览不隐式保存，publish一次提交当前确认的输入/READY集合+version/requestId，同事务保存private draft并生成新公开revision；retry保留同requestId与完全相同payload。跨页选择按ID集合、日期切换清范围并guard，读者只current revision。Dialog统一负责撤回/下架reason/dirty/focus，空/错误布局来自R2。profile维护放我的发布既有操作区，不新建管理平台。

页面直开严格parse只允许station路径，在/me确认前不加载保护数据；未登录hash target保留，登录后进入或显示404，不接任意redirect字符串。新增hash是当前路由而非兼容层。

BLOG summary保留R2原输入/预览/卡片位置，加入dirty快照、create/save/publish/read与不可变revision，保存不提前公开。R2类型切换仅“保存旧稿并新建另一类型”，不原地修改已有文章type；确认保存失败保留旧编辑输入、取消无变更，DAILY新类型去sources。完整有图/无图页面状态按父design覆盖表逐项验收，不增另一套route family。
