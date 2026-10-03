# RustFS 附件执行清单

前置：publishing已通过核心check且其表/API/事务合同不变；父最新规划经批准，context validate。本机工具/具体隔离命令按父`research/storage.md`，全AC门禁按父`implement.md`。

1. 确认WSL/Docker/9000/9001，获取固定稳定RustFS镜像；新增持久卷/私有初始化bucket，native/compose端点配置，保留既有服务/数据。
2. 锁依赖、V18 owner/ref约束与ObjectStorage/S3实现、safe config及异常；扩展local frontend-env秘密剥离与README/env example，不输出值。
3. 暂存与大小/真实结构/UTF8验证、multipart/proxy上限；无附件byte[]整个读取。
4. post锁/version/upload requestId与真实预留（已有结果先于旧version校验）、事务外put、token/state条件READY确认；失败保旧行/key，新requestId重试；逾期reservation显式recover；发布save/publish同事务附件集成；safe孤立对象cleanup状态/引用竞争。
5. protected GET/HEAD的作者/reader路径、stream生命周期、download/inline/no-store/nosniff，Range鉴权且不206旁路。
6. 文件边界、伪装、ownership、并发额度、F1/F2隔离、HTTP401/404、故障注入、cleanup竞争、延迟put与另tab移除/cleanup不复活、JVM崩溃后占额recover真实PostgreSQL/Redis测试。
7. RustFS真实专用bucket匿名直读拒绝+put/get/delete、真实proxy→backend20MiB与50MiB集合验收；记录散列、配置脱敏证据与exit，不能mock代替。
8. trellis-check修复，交接UI稳定DTO与下载/Blob合同；阿里云结果记DEFERRED。

拟新增`AttachmentValidationTest`/`AttachmentHttpIntegrationTest`/`AttachmentConcurrencyIntegrationTest`/`RustFsStorageIntegrationTest`，不是当前已存在命令。全后端clean verify及compose/local safety检查；精准实际命令见父执行计划和storage研究。

失败不down -v/删业务schema或有效bucket对象；保留历史引用与失败补偿记录。常规问题修复后最后汇总，新增产品/兼容范围另行确认。未授权commit/push/云资源/部署。
