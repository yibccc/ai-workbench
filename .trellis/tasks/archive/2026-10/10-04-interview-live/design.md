# 真实模型验证方案

## 边界与数据流

PDF只读→忽略目录Markdown（已去联系信息）→隔离schema的current resume PASTE→default真实AgentScope网关→持久面试job/题单→验收示例提交→真实评估组→持久report。全程调用现有HTTP/CSRF业务接口，不从数据库直接插入题单或评分。

新EnvFile只含非秘密DB定位/schema/Redisnamespace/storage配置；不克隆既有native文件内的凭据。既有隔离DB/storage/bootstrap凭据和普通.env的模型三变量均只读入调用进程内存，由后端继承，不写进新文件，finally还原caller。新schema优先采用已确认不存在的`d9_interview_live_tests_20261004`；数据库仍属于既有隔离native delivery资源，Redis namespace也独立。default启动沿用scripts/local/start.ps1的8080/5173、隐藏窗口及身份state，端口被未知进程占用时不停止或复用。

验收账号为隔离bootstrap/新合成账号，绝不登录日常账号。每次mutation后GET权威版本，不从minimal receipt推断当前状态。模型调用遵循现有一次SDK尝试/手动重试合同；输出原始数据仅本地，公开记录采用摘要、结构计数及脱敏质检。

## 样例和测量

默认N=5，计划1次题单生成与5组评分（若生成成功），由执行代理按实际问题编写明确标为验收示例的技术回答，涵盖简历已有经历中的事实与通用工程原理，可包含明确薄弱回答以观察反馈。没有任何答案声称是用户本人现场作答。成本不通过另一付费模型生成答案。记录请求到可读终态的wall-clock耗时，注明job数并不等同外部抓包计数；无供应商usage则不报告token或账单。

## 保护与清理

普通.env原始字节SHA前后比较。后端key只在进程环境，Vite不得继承模型/DB/storage/bootstrap secret。使用Windows PowerShell 5.1既定入口，caller环境finally还原，stop仅核验精确PID/创建时间/命令/root。保留隔离schema与原始证据，不覆盖旧测试schema或恢复卷。前端无需修改。
