# Research: 正式设计存储段快速复核

- Query: 复核父任务 design.md 的 RustFS、附件管线、额度与清理合同，指出实现前应固定的技术细节。
- Scope: internal（依据storage研究已核实代码与官方资料）
- Date: 2026-10-03

## Findings

被审阅文件：`.trellis/tasks/10-03-community-oss/design.md`，本次读取其1–118行；本研究没有修改design。

总体一致：`:94` ObjectStorage边界、`:96` WSL RustFS新资源、`:98`后续同key复制/SHA核验后配置cutover、`:106`短事务额度预留、`:108`引用保护/锁顺序及`:110`逐次读取鉴权和缓存合同，与AC和storage研究一致。没有发现必须更换架构的存储冲突。

### 建议在实施合同中明确的四项

1. **运输上限保持一个确定值。** design `:102`使用约21MiB file/22MiB request，storage研究提出20MiB file/21MiB request；两种都可兑现业务精确5/20/1MiB，只要Nginx与Servlet同一请求上限且留足字段/文件头开销。请在实现选定精确字节并以有效20MiB文件实测。不能把代理1m保留到上传路径，也不能因“约”而产生配置不一致。有关真实代码为`deploy/nginx.conf:14`，官方默认见storage研究的Spring3.5.16引用。

2. **上传重试先识别已完成逻辑请求，再判断旧version。** design `:106`已要求expectedVersion/requestId，但只写了校验expectedVersion再预留。成功响应丢失后原version已经过期；仍需按owner/post/requestId+实际sha/长度查到同一成功结果，不能把正常幂等重放一律变409。取结果之前仍要校验owner，不同sha/大小才返回幂等冲突。可参照design `:61`发布合同的检查顺序。前端队列重放返回的aggregate version不能覆盖更新本地状态。

3. **失败attempt旧object key必须持久保留。** design `:106`的“可重试孤立对象记录”是必要条件，但V18表 `:51`–`:53`尚没有固定记录方式。若同requestId重试更新attachments.object_key，会丢掉Put成功/DB失败时旧key，后续无法安全补偿；应让每attempt有不可变记录/清理任务，或者保留失败attachment行且新attempt用新ID/key和明确定义的逻辑request关系。清理pending与删除失败也需持久状态/时间/错误分类，不能只在内存catch里删除。业务表仍无需provider/Bucket/URL。

4. **解析器限制不能悄悄新增产品限制。** design `:104`的“真实结构”应按ImageIO正确格式+WebP插件、PDFBox严格结构范围、MD严格UTF-8/BOM/NUL合同落地；**Tika不必引入**。PDFBox Loader为增量/可能宽松修复，不应仅Loader成功就通过所有结构验证；文件reader避免InputStream全段复制。动画WebP、加密PDF和超大解码尺寸可能影响合法输入，研究不能替用户自动决定拒绝全部这些子类型；若解析能力要求新增拒绝规则，须最终汇总说明影响。不能用“Future超时”证明不可中断decoder已经结束。

### 可直接采用的环境与依赖事实

- 已有Java可执行文件`C:\Program Files\Java\jdk-17\bin\java.exe`与Maven可执行文件`D:\APPS\apache-maven-3.9.9\bin\mvn.cmd`，各Test-Path为True；明确质量命令和配置键见`research/storage.md`。
- 当前最低依赖：AWS S3 SDK 2.55.10、PDFBox3.0.8、TwelveMonkeys imageio-webp3.15.2；Tika3.3.2只作备选研究信息。版本为官方核对，尚未实际Maven安装编译。
- `rustfs/rustfs:1.0.0`公开registry manifest只读查询实际exit0（OCI image index）；尚未拉层/运行。稳定release引用见storage研究；不误选1.0.1-preview.16。
- `start.ps1:35`只剔除旧后端环境前缀，需同步去除`WORKBENCH_STORAGE_`、`RUSTFS_`和可能的`AWS_`。storage凭据只进入后端，前端及VITE不带入。
- design `:102`先owner再读流要落实到真实MVC参数解析：MultipartFile绑定可能先于Controller方法体owner检查。区分有上限的Servlet接收缓冲和应用验证暂存，必要时lazy/request控制；还需以缺少CSRF header的请求测试解析与清理。单纯在方法体写owner判断不能作为已证实的解析顺序。
- WSL只读ss探测确认9000/9001/19000/19001当时未监听；未占端口，启动前仍复查。registry Linux amd64子manifest为 `sha256:ba0a1b53e36f321c0d46f3867104abef169f7bc59c467c664ddac87e7ddc9a8b`，两次manifest查询exit0。

## Caveats / Not Found

- 尚未检验实际产品实现或运行TC；此文只审规划，不能作为质量门禁通过记录。
- 最终review前父会话可能继续修改design；此结果针对本次读取快照，后续以明确的新合同为准。
- registry只读查询成功不等于镜像层下载、Bucket/IAM或JVM SDK读写成功；这些是规划批准后的实施步骤。
