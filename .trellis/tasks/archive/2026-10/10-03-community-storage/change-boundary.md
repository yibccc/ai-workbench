# 实现边界

使用 trellis-start / trellis-before-dev，已读取完整 hook 文件、任务 manifest、父和本子规划、publishing storage-handoff、backend 分层/身份/运行/错误/本地部署规范与跨层复用指南。

最小行为差距：V17 仅支持无附件；V18 须交付真实私有 RustFS 文件、精确额度、引用快照、受保护读取与安全补偿。行为位于既有 publishing save/publish 事务及新增 attachment 编排/短事务/mapper，不在 UI 或 after-commit 补丁。

必要文件：新增 V18、attachment DTO/entity/mapper/XML、Controller/Service/独立事务 bean、ObjectStorage/RustFS/config/文件验证；修改 PublishingServiceImpl/CommunityServiceImpl/CommunityPostMapper XML 接入元数据和相同事务 refs，CommunityTestData/E2eMaintenance 同步新 FK；pom/application/compose/nginx/local scripts/env example/README 为真实运行及凭据边界；新增实际文件、DB/HTTP/并发/故障/S3 测试。

保持 V1–V17 原字节与既有 owner/session/CSRF/报告行为。本子不改 frontend，不添加 Aliyun SDK/API、云资源、格式转换、总账号配额、自动按日清理、兼容层/flag、commit/push。

验证直接覆盖 token/state fencing、事务回滚与真实隔离服务，编译不能替代这些证据。
