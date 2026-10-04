# 只读准备证据

已核实正式Gateway profile为`(!test & !e2e) | live-acceptance`，fixture为`(test | e2e) & !live-acceptance`。不选择test/e2e。现有native隔离DB为wbinterview_native_delivery，源EnvFile为`.local-runtime/ai-interview-ops-real/native.env`；本次使用新schema与namespace，不修改其旧数据。

PDF已用系统Poppler pdftotext与pdftoppm读取/两页视觉核验；私人内容在`.local-runtime/ai-interview-live-20261004/resume/resume-model-input.md`，3002 codepoints，五项目与LangGraph/CAS/MCP/SGLang等完整；不要打印或提交全文。配置仅安全摘要：deepseek-flash、key exists、HTTPS api.deepseek.com、无userinfo。

登录CSRF、PASTE与Interview创建/提交/complete/report合同见适用private specs及控制器/DTO实际源码。严格按PENDING/PROCESSING/SUCCEEDED/FAILED等真实enum轮询，不把answer READY与generation终态混淆。

自有端口运行前核验无人占用；旧WindowsPowerShell既定清理入口避免pwsh7时间自动转换。敏感变量不能输出、不能放到启动参数或前端。只读准备阶段未发生模型调用；后续本轮请求已授权真实基础链路调用。

运行准备中自动审查拒绝克隆native.env，担心把DB/storage/bootstrap凭据复制持久化；被拒命令未执行。方案改为新EnvFile仅非秘密定位变量，所有既有凭据只进程内存注入。产品范围和真实调用授权不变，继续执行安全替代，不需要新增用户决定。
