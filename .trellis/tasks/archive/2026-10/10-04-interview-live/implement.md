# 执行与审查

1. 核对当前JAR SHA、用户.env SHA和模型安全摘要；检查隔离资源Running+healthy、端口、schema不存在。规划/上下文验证后原生start。
2. 使用trellis-implement执行代理负责忽略目录runner/runtime配置及本任务validation，主负责规划/授权和总协调。读取适用spec与实际HTTP DTO；禁止输出secret或将用户正文加入task artifacts。
3. default真实JAR启动，GETcsrf→合成login→刷新csrf→PASTE→GET验证version/hash→create5主问→GET到生成终态。失败立即记录safeFailureCode，不由runner自动重试模型。
4. 真实题单成功后，提交10轮明确验收示例答案，GET更新version，完整完成并读取真实报告；核对评分、反馈、总分、snapshot与GET稳定。记录质量/耗时，原始响应本地保留。
5. trellis-check独立核证运行profile与fixture隔离、真实返回和数学、隐私/git范围、清理与普通.env SHA；发现问题在明确最小边界内修复，再针对验证，非必要不重跑271/117整套。
6. 主更新本任务验收与上轮delivery的真实基础链路延期结论（保留最大上下文等未测边界），必要spec沉淀；精确本地提交脱敏文档、原生归档和journal。原六项用户改动及bootstrap不动，不push/PR/发布。

既有启动：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1 -EnvFile <ignored-live-file>`；收尾既定stop.ps1。实际命令/返回码及guard拒绝写入validation，不凭规划假定成功。
