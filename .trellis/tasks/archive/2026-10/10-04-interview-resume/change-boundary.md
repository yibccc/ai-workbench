# 实施改动边界

已批准的缺口：当前工程没有私有当前简历及导入原件业务；已有社区附件不能充当账号简历。增加owner singleton、版本/幂等、严格文本/文件输入、受保护原件、耐久清理和必要运维，沿现有ObjectStorage及共享validator。

后端实现代理独占新resume Controller/Service/Impl/Persistence/Mapper/DTO/entity、V19（实施前重查）、对应测试；共享Markdown helper及现validator最小无行为变化提取、private cache匹配。ops代理独占scripts/local新增policy维护与联合备份、deploy/nginx.conf的resume import位置及对应ops测试。主会话独占隔离环境/运行验证、父记录及最终跨层提交；其余代理不改这些文件。

只改变已批准新业务，不迁旧数据、不增兼容路径/feature flag，不改原社区配额/历史迁移、输入/报告模型行为。共享validator refactor以原AttachmentValidationTest与原社区测试证明行为不变。明确依赖：引擎在resume snapshot/version接口验收后启动，UI骨架可独立并行，真实联调等待后端验收。

独立ops审查发现本需求的native联合备份必须从实际启动时记录envFile绝对路径/hash和databaseIdentity，而既有start/common只记PID。后续ops实现代理新增最小启动捕获（scripts/local/start.ps1/common.ps1及必要capture验证，沿已有EnvFile解析），只给新backend进程state保存非秘密身份；frontend不获凭据，未知既有运行进程不事后伪造。此变更落实已批准backup的可靠停写身份，不添加旧state兼容或影响原应用功能。实际网络/DB/schema必须与停止工具一致。
