# ai-workbench：已选方案与本地核实边界

交接 ID：`WB-20260925-auth-isolation-8cbe4aee` · revision：`1` · 项目：`yibccc/ai-workbench`

仓库基线：`master @ a4f86e48c1cd76c910b2fae9c94bfec6851277f4`。本文件属于 `draft` 交接包：已确认范围保持冻结，仅 P-001 的“活动判定”仍待决定；不代表批准编码、清库、提交或部署。

## 1. 已选方案与不再比较的替代项

【用户已决定 / selected】Spring Security认证；Spring Session Redis indexed集中式会话；STOMP Simple Broker私有通知；PostgreSQL主业务数据直接归user_id；原React/CSS界面增加登录、账号菜单及辅助用户管理页。账号为管理员开通的用户名+密码，项目在内全私有。允许多端；7天空闲超时但活动分类P-001未决；无记住我。

【已选择的取舍】普通密码而非临时密码/强制改密；本地弱密码表而非外部泄露服务；框架会话而非另造JWT/第二套token；应用用户条件而非workspace/RLS；进程内Simple Broker而非RabbitMQ。此处不重新投票或增加基础设施。后续如改变这些选择，须记录原因并回用户决定。

【验证边界】本包没有可直接应用的补丁、UI实现源码、编译结果、运行截图或测试结果。生成UI图不是浏览器预览。代码位置分为已读的旧实现与拟新增/待核实落点；名称不会被当作真实现有实现。

## 2. 证据与版本

### 2.1 仓库现状

| 事实 | 已读位置/符号 | 对本任务的影响 |
| --- | --- | --- |
| 后端POM声明Boot3.5.16、Java17；有WebSocket/Data Redis，没有直接声明Security/Session依赖。 | [backend/pom.xml](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/backend/pom.xml)，固定commit复读。 | 增加既定认证/会话依赖，不升级整个技术栈。 |
| 旧入口由Nginx共享Basic Auth保护；Compose已有PostgreSQL/Redis/backend/frontend。 | deploy/nginx.conf、compose.yaml（历史master读取及blob标识见HANDOFF）。 | 移除共享认证的同时，必须先有应用鉴权；不让API/WS出现匿名窗口。 |
| 业务SQL当前按资源ID/日期等查询；项目名及请求幂等/报告版本边界未按用户限定。 | ProjectMapper.xml、InputMapper.xml、ReportMapper.xml、TaskMapper.xml、WorkRecordMapper.xml；V1/V2/V4/V5/V6/V8/V9等已读迁移。 | 归属改造覆盖选择、写入、关联、幂等、版本与子表；只改列表不够。 |
| 旧事件Hub向所有WebSocketSession广播，事务提交后发通知。 | events/WorkbenchEventHub.java 的 publishAfterCommit/publish；WorkbenchWebSocketConfig.registerWebSocketHandlers。 | 替换传输与路由，不改变事务提交后通知及GET权威原则。 |
| 前端共享realtime模块含15秒查询、重连/去重，localStorage pending键不分账号。 | [frontend/src/hooks/realtime.ts](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/frontend/src/hooks/realtime.ts)，固定commit复读。 | 认证边界要清理/分隔跟踪状态；后台查询可能影响空闲过期，见P-001。 |
| ToastProvider在page变化时清除非本页提示，计时5000ms，并挂载在业务App内部。 | [ToastProvider.tsx](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/frontend/src/components/ToastProvider.tsx)固定复读；App.tsx历史读取。 | 必须调整认证切换时的拥有者/投递时机，让登录页上的失效提示可见；不是新增通知组件。 |
| 前端规范保留四工作区、RetainedView草稿、分页、抽屉及本地错误/确认语境。 | [前端规范](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/spec/frontend/directory-structure.md)，固定commit复读。 | 身份变化与普通切页分开处理；不能用清理身份之名破坏同一用户的正常保留页。 |
| 数据库规范要求已应用Flyway迁移不可改写。 | [数据库规范](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/spec/backend/database-guidelines.md)，本轮复读1–150行。 | 清旧测试数据不授权重写V1–V12；新结构仍通过符合本地规范的版本迁移建立。 |
| D10说明AI先提交请求/来源，事务外模型调用，再短事务保存；版本及恢复依赖数据库。 | [D10架构说明](https://github.com/yibccc/ai-workbench/blob/a4f86e48c1cd76c910b2fae9c94bfec6851277f4/.trellis/tasks/archive/2026-09/09-15-d10-local-delivery/research/architecture.md)，固定commit复读。 | 所有后台执行均从持久父实体取得owner，不能在异步线程猜当前登录用户。 |

上表不声称已审完服务实现/全部测试/真实数据库；完整读取范围与ref限制在HANDOFF证据表中。

### 2.2 外部官方依据

以下资料于2026-09-25核查，仅支持框架能力，不证明此仓库已接通。包里不沿用未经本轮核实的“前端STOMP当前最新版”结论；`@stomp/stompjs`的实际锁定版本由本地核对选定。Boot受管版本与实际effective POM/锁文件也必须区分。

| 编号 | 作者/资料 | 适用版本 | 核查日期 | URL与支持范围 |
| --- | --- | --- | --- | --- |
| E-01 | Spring官方 / Spring Boot受管版本 | 3.5.16 | 2026-09-25 | [https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)<br>核实到Framework 6.2.19、Security 6.5.11、Session 3.5.7受该版本管理；不是实际依赖解析结果。 |
| E-02 | Spring官方 / Spring Security WebSocket安全 | 6.5 | 2026-09-25 | [https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html)<br>复用HTTP Principal；入站CONNECT需要CSRF；必须限制订阅/发送目的地，不能只靠/user命名。 |
| E-03 | Spring官方 / Spring Session Redis配置 | 3.5 | 2026-09-25 | [https://docs.spring.io/spring-session/reference/3.5/configuration/redis.html](https://docs.spring.io/spring-session/reference/3.5/configuration/redis.html)<br>indexed repository支持按主体查找会话及生命周期事件；需实际接线与验证。 |
| E-04 | Spring官方 / SessionRepositoryMessageInterceptor API | 3.5.7 | 2026-09-25 | [https://docs.spring.io/spring-session/reference/3.5/api/java/org/springframework/session/web/socket/server/SessionRepositoryMessageInterceptor.html](https://docs.spring.io/spring-session/reference/3.5/api/java/org/springframework/session/web/socket/server/SessionRepositoryMessageInterceptor.html)<br>按匹配消息类型更新访问时间；默认列出CONNECT/MESSAGE/SUBSCRIBE/UNSUBSCRIBE，不含HEARTBEAT。 |
| E-05 | Spring官方 / Spring Framework User Destinations | 6.2 | 2026-09-25 | [https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/user-destination.html](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/user-destination.html)<br>按已认证用户路由到其订阅会话；多会话可接收同一用户消息。 |
| E-06 | Spring官方 / Spring Framework Simple Broker | 6.2.19 | 2026-09-25 | [https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/handle-simple-broker.html](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/handle-simple-broker.html)<br>内存维护订阅；可配置调度器支持心跳；不是新增持久消息队列。 |
| E-07 | Spring官方 / Spring Security Password Storage | 6.5 | 2026-09-25 | [https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)<br>使用成熟自适应单向PasswordEncoder，具体算法参数由本地在8–64字符合同内确定。 |


## 3. 数据与身份边界

### 3.1 逻辑数据模型（设计，不是已存在的DDL）

| 对象 | 既定归属/状态 | 待本地细化 |
|---|---|---|
| 账号（拟新增持久模型） | 稳定userId、全局不区分大小写的用户名、单向密码摘要、ADMIN/USER、启用/禁用。用户名不可变；不加显示名、邮箱或手机号。 | 实际表名/字段、用户名规范化与唯一约束、密码编码器、创建时间展示来源。不得默加已禁止的功能。 |
| projects / capture_inputs / todo_items / work_records / reports | 主业务实体直接建立非空user_id；创建从认证身份取owner。 | 新迁移编号、复合索引、Mapper参数、事务验证/数据库归属约束。 |
| task_events / report_sources / capture_generated_items | 从属/快照记录从父对象继承owner；查询与写入必须证明父对象归属及子关联同属。 | 是否冗余owner字段及约束方式由本地说明；“不加字段”不等于允许无归属查询。 |
| Redis Session | indexed会话按主体可检索，可区分当前会话和其他会话。 | 主体索引与应用userId一致性、序列化、命名空间、销毁事件与WebSocket映射。 |
| 浏览器状态 | 密码及会话令牌不写入localStorage/sessionStorage等脚本存储；会话标识仅按既定HttpOnly Cookie方案承载。已有业务草稿、pending ID、缓存必须避免跨账号串用。 | 清理/命名空间/中止请求与响应身份校验方案；不能将此前localStorage键视为用户隔离。 |

用户名可用于登录和显示，但业务owner不随用户名大小写形式变化。Principal、Redis主体索引、向STOMP用户发送时的目标标识必须统一映射到同一账号，不能混用用户名与UUID导致漏发或串发。

### 3.2 SQL与后台链路必须一起覆盖

项目活动名唯一范围变成用户内；输入幂等、报告requestId查找/冲突处理按用户分开。周报版本链的锁、上一版本查找及关联也以用户+原周期为边界，不能只修锁而保留全局latest查询。

所有列表、搜索、分页计数、详情、修改、删除、完成/重开、完成结果、事件历史、AI retry/revert、报告取材/来源分页/编辑/删除均带归属授权。联表中的项目、自动完成记录、报告来源也不能仅因UUID存在就被信任。404应在泄露实体内容/状态前确定，不要先返回他人资源的“已归档”“版本冲突”等信息。

后台处理、任务恢复与消息发布使用持久请求/报告的原始owner；SecurityContext或HttpSession不能成为异步任务唯一owner来源。沿用已有短事务与处理token/租约，owner归属不得因退出/切换身份改变。此要求不新增“账号禁用后取消所有已开始模型任务”的产品行为；后台执行取消策略未选择，不凭空增加。

### 3.3 对旧测试数据的处理

用户已选从干净状态开始，不开发历史认领、user_id回填旧业务数据或迁移兼容产品。仍需创建新账号结构并演进业务归属约束；保留不可改写已应用迁移的仓库规则。清库方法、目标数据库/schema/volume和安全确认由本地核实，网页端未清库，不提供可误执行的删除命令。

## 4. 会话、权限与状态变化

| 触发 | 账号/凭证变化 | 现有会话与STOMP | UI |
| --- | --- | --- | --- |
| 正确登录 | 已启用账号通过用户名+密码验证。 | 新增独立Session，不挤掉其他会话。 | 进入现有工作台。 |
| 主动退出 | 不改账号数据。 | 仅撤销当前Session及关联连接。 | 立即回登录页。 |
| 用户自改密码 | 验证当前密码后更换摘要；不强制周期改密。 | 保留当前认证体验；撤销其他会话和对应连接。 | 当前页保持登录，5秒成功Toast。 |
| 管理员重置密码 | 直接设置普通新密码，旧密码不可新登录。 | 目标用户全部Session及连接失效，包括目标是操作者本人时。 | 检测到失效后登录页+5秒Toast。 |
| 禁用账号 | 账号DISABLED；业务数据保留。 | 全部Session及连接失效；不能新登录。 | 检测到失效后登录页+5秒Toast。 |
| 重新启用 | 账号恢复启用，不删除/复制数据。 | 不复活旧Session，须新登录。 | 管理成功Toast。 |
| 实际角色变化 | ADMIN与USER相互调整。 | 全部旧Session撤销；新登录后读取新权限。 | 自身角色变化会退出；他人操作反馈5秒Toast。 |
| 最后管理员保护 | 拒绝使启用ADMIN数变为0的状态/角色操作。 | 失败操作不得部分修改状态/撤销别人的会话。 | 明确失败反馈；服务端最终约束。 |
| 7天空闲超时 | 账号/密码不变。 | 该Session不再有效；活动分类P-001待定。 | 前端发现后立即登录页+5秒Toast。 |


【待本地核实】权限变更、数据库提交、Session撤销、正在进行的登录、STOMP订阅/发送之间存在并发边界。仅扫描并删除一次Session并不自动证明所有旧身份都失效。具体采用事务协调、账号版本校验或其他局部方式由本地设计，不在网页端伪造已实现机制；验收以变更成功后的实际权限为准。

至少一个启用管理员的约束要能抵御两个管理员同时降级/禁用的竞争，不能仅以UI禁用按钮或不加协调的“先count再update”作为证明。允许多管理员不等于普通用户能自助提权。

## 5. 接口与STOMP协议影响

### 5.1 HTTP接口只冻结能力和授权，不编造现有端点

本轮未选定新增认证/管理接口的具体URI、DTO名或JSON结构。下列为待本地命名的能力，不是现有端点清单：登录、退出、读取当前身份/CSRF信息、自改密码；ADMIN账号列表/创建/启停/重置/角色修改。不要把示意接口当作已读代码。

既有业务接口保持业务语义与成功响应兼容，增加认证/归属边界：未认证业务请求401，已认证USER访问管理能力403，他人业务对象404。原业务400/409等冲突语义保留，但先完成owner判定。新的管理校验/限流错误状态码、响应结构及凭证传输的具体接口由本地与现有Problem Detail风格协调并在正式设计中固定。

登录失败不区分用户名不存在或密码错误；网络故障、CSRF失败、403、404不统一包装成会话过期。不能为了让SPA连通而全局禁用CSRF或依靠Nginx替代应用鉴权。[E-02]

生产会话使用HttpOnly+Secure Cookie，SameSite策略明确；普通Session Cookie不等于承诺关闭浏览器后仍登录7天。8–64字符是用户规则，不用密码算法的输入上限静默缩短该范围；特别要验证非ASCII长口令的长度/编码及不截断行为。[E-07]

### 5.2 选定的STOMP边界

| 项目 | 已选设计 |
|---|---|
| 传输/握手 | 原/ws/events路径上改用STOMP；不加SockJS。 |
| 服务端broker | Spring Simple Broker，当前单backend部署；无外部消息中间件。 |
| 客户端订阅 | /user/queue/workbench-events，仅当前认证身份自己的事件。 |
| 身份 | 复用HTTP Session的Principal；不通过CONNECT里可伪造的用户名/用户ID决定身份。 |
| 业务消息 | INPUT/REPORT状态通知继续作为GET刷新信号；业务修改走HTTP。 |
| 多端 | 向该用户所有有效订阅会话定向发送；不是全局广播。 |
| 故障恢复 | 保留去重、重连后GET、待处理对象的兜底查询；无持久队列/离线重放需求。 |

/user路由不是完整鉴权：应只允许既定私有订阅，拒绝直接订阅底层queue、越权通配目的地、伪造他人用户路径；客户端业务SEND不能冒充后端通知。ADMIN与USER都需要正常接收自己的通知，不应只检查USER角色而遗漏ADMIN。[E-02、E-05]

CONNECT的CSRF与Origin限制要明确接入；Session销毁到STOMP关闭要有映射/监听及必要的失效防护。不能将“引入依赖”写成“已自动实现全端下线”。Simple Broker维护的是进程内订阅，Redis集中会话不会自动变成多backend消息路由；本次不扩到分布式消息部署。[E-02、E-03、E-06]

### 5.3 P-001与官方行为的更正

前面讨论将“STOMP有活动会刷新Session”说得过宽。本次核到Spring Session3.5.7 API的默认匹配类型为CONNECT、MESSAGE、SUBSCRIBE、UNSUBSCRIBE，并不列HEARTBEAT；不能宣称心跳默认一定续期，也不能把服务端推送直接等同用户主动活动。[E-04]

前端现有15秒兜底HTTP查询和自动重连本身可能造成自动访问；如何纳入用户所说“无活动”尚未决定。保留P-001为draft，不擅自新增活动追踪、主动心跳接口或更改7天期限。ACTIVITY分类选定后，本地再映射到Spring Session与客户端行为并补对应验证。

## 6. 已确认UI与实际附件

本包的`assets/ui-concept.png`是本轮已展示并获得确认的生成示意图，非运行截图、非真实账号/工作记录。此前没有实际交付可运行UI源码或浏览器验证；本包不把图当成已完成的代码。正式UI实现属于本地后续授权阶段。

| 位置 | 冻结的内容与交互 |
|---|---|
| 登录页 | 独立品牌卡片、用户名、密码/显隐、登录按钮与提交中状态；无注册/找回/记住我；不加导航或营销功能。 |
| 账号菜单 | 桌面侧栏底部，手机顶栏；用户名/角色、改密/退出；ADMIN另有用户管理。 |
| 用户管理 | 辅助页；用户名、角色、启用状态、创建时间、操作菜单、创建按钮；不加搜索/分页/批量或业务数据查看。 |
| 创建账号 | Dialog，用户名、角色（默认USER）、密码、确认密码；提示用户名不可改及密码8–64；不发邀请邮件。 |
| 自改密码 | 当前密码、新密码、确认密码；说明本会话保留，其他会话失效。 |
| 管理员重置 | 新密码、确认新密码；说明目标全部会话失效。 |
| 角色/禁用 | 明确角色权限变化或禁用及数据保留、全端下线副作用；保护最后管理员；满足规则允许自操作。 |
| 临时通知 | 现有5秒Toast；失效时先进入登录页而不是等待；取消独立Session过期条/页。 |

图中的搜索框、铃铛、示意工作记录表格及手机纵向导航不构成新增功能或改造四工作区的授权；图中独立Session提示卡被最后确认的5秒Toast规则取代。以明确的文字功能边界为准，不能为了像图而增加搜索/通知中心或重做业务布局。

配色、圆角、表单、Dialog、Toast、响应式与焦点规则复用已读styles.css/组件/规范。身份变化时卸载或隔离旧身份数据，不破坏同用户RetainedView正常导航的草稿保留。5秒Toast的认证根布局适配属于已有组件接入，不另建一套提示系统。

## 7. 改动落点与本地未知（不是执行计划）

| 已读旧位置 | 可确定的影响 | 本地需核实/候选 |
|---|---|---|
| backend/pom.xml；application.yml；compose.yaml | 声明认证/会话依赖与配置；使用已有Redis。 | 新Security/Session/引导初始化配置与类，实际命名未定；effective POM和生产环境。 |
| backend/src/main/resources/db/migration/ 下已读迁移 | 新账号及owner/唯一约束。 | 新版本迁移文件为候选，不预设编号、不修改旧迁移；只处理已获授权的空库起点。 |
| resources/mapper/ 下Project/Task/WorkRecord/Input/ReportMapper.xml | owner条件、联表、幂等及版本锁边界。 | service/controller/DTO的实际调用链需要补读；不凭模式断言全覆盖。 |
| events/WorkbenchEventHub.java、WorkbenchWebSocketConfig.java | 替换raw handler为既定STOMP模式，保留after-commit。 | 新STOMP配置、安全消息授权及Session生命周期接线，都是待实现候选。 |
| frontend/src/App.tsx、main.tsx、api/http.ts、hooks/realtime.ts | 认证状态边界、HTTP未认证处理、STOMP、身份切换清理。 | 登录/账号UI新模块候选；不能直接把网页示意代码当可编译补丁。 |
| components/layout/AppShell.tsx、navigation.ts；DialogProvider.tsx、Icon.tsx、ToastProvider.tsx、styles.css | 原导航保持，账号入口与对话框、Toast复用。 | 图标/组件名、根Provider位置、跨页Toast作用域与可见性细节。 |
| deploy/nginx.conf；compose.yaml；README.md | 移除旧Basic Auth，应用鉴权与代理配置同步。 | 目录发现的deploy/40-workbench-auth.sh及Docker构建/本机启动脚本联动均为候选复核，未声称本轮已读其实现。 |

【delegated】算法参数/denylist来源与大小、限流有限阈值和窗口、CSRF接入、Cookie SameSite精确值、并发会话撤销、Session失效事件延迟、无Redis时拒绝未验证身份而非匿名放行、新接口命名、测试隔离环境由本地核实。8–64、7天、无强制改密、私有数据、Toast等用户行为不得随之改变。

【未知】本地分支/HEAD/未提交文件、真实运行数据库、部署域名、初始化凭据、已安装依赖实际版本、真实测试状态；仓库任务目录的可见状态不能代替本地活动任务或未提交工作。

## 8. 兼容、风险与回滚

| 影响/风险 | 边界与处理 |
|---|---|
| raw WebSocket客户端不兼容STOMP | 前后端协议需要作为同一交付兼容单元；不新增双协议兼容层。陈旧页面需刷新，不把旧JSON客户端当STOMP可用证明。 |
| 删除共享Basic Auth | 仅在应用认证完整保护API/握手/订阅后替换；不能留下公开后端旁路。 |
| 旧测试数据清空 | 清理后未备份内容无法凭本包恢复；核实目标且保留操作证据是本地责任；无网页清库动作。 |
| 新多用户数据遇到旧无owner版本 | 不允许把旧共享数据版本直接公开连到含多人数据的新库。必要时先关闭公网入口、保留当前库，再使用验证过的匹配版本/快照；不得拿“回滚”当删除正式数据许可。 |
| 业务数据库与Redis职责 | PostgreSQL业务记录仍是权威；Redis承载会话并不把业务持久化权威迁走。无法验证会话时不能降级匿名。 |
| 多端撤销/并发管理员 | 本地验证实际授权边界而非只看Cookie清除或按钮状态。数据库/Session部分失败时不得宣称已全部完成。 |
| 角色与隐私 | ADMIN不越过业务owner检查；管理员能设置别人密码是已选择的运维信任边界，不能宣传成防管理员冒用的加密隔离。 |
| API key/模型调用 | 沿用后端统一配置，不增加每用户额度/计费/密钥系统；验收默认隔离替身，不默认触发付费调用。 |

回滚细化只在本地授权规划中完成：明确受影响的应用/配置/数据库版本与验证点。本文不提供最终部署顺序、删除命令、任务分解、工期或Trellis执行计划。
