# RustFS 私有附件与统一存储合同

## 目标与依赖

在本地WSL Docker真实RustFS上交付后端中转的私有图片/PDF/MD附件，绑定draft/当前发布/保留历史权限，不让任何云直连绕过撤回。依赖`../10-03-community-publishing/`完成V17与publish事务；父`../10-03-community-oss/prd.md`完整AC、`design.md`统一合同为权威。

## 范围

FR-05/06，FR-07附件联动，FR-09文件HTTP身份管线。仅当前RustFS adapter；未来阿里云固定ObjectStorage/key/业务DTO边界，不实施未申请的API/云资源。单稿10个/50MiB，图片5MiB/PDF20MiB/MD1MiB，owner+READY+current ref、失败释放额度与孤立对象显式安全清理。

## 验收

- [ ] 父AC-13～21及05/08/09相关边界通过；匿名401、他人草稿404、current revision读者与本人历史读取精确。
- [ ] 真文件结构/扩展名/实际字节白名单；各精确上限允许、+1拒绝，10个/50MiB并发重试不突破。
- [ ] F2仅draft时读者仍读F1，显式publish后F2当前且F1历史保留，ready/owner/额度/引用同发布事务验证。
- [ ] 中断/存储/DB确认失败不伪造READY，正文/其他附件保留，pending额度释放/cleanup可重试且不误删仍引用或他人对象。
- [ ] GET/HEAD/Range请求均先权限，no-store，无云凭据/URL/重定向；PDF/MD download，MD不导正文。
- [ ] WSL真实RustFS专用私有bucket put/get/delete与匿名直读拒绝有证据；native Windows与compose环境正确配置；前端子进程不得继承新增storage secrets。
- [ ] 原服务/volume和V1～V17保持；无自动清理日数/用户总配额。

## 非目标与状态

阿里云adapter/API/SDK实测/资源/存量搬迁工具本轮延期，不把统一接口当云已验收。无预签名、直传、CDN、多云运行、格式转换/病毒认证、新存储配额/旧版fallback/flag。产品无未决项，父最新规划已获“开工/批准并连续执行”；本任务等待publishing实际核心交付门禁后启动。
