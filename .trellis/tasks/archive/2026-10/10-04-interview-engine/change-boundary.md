# Engine实施边界

已有简历snapshot及私有API已真实验收；缺口是正式固定文字面试的服务端持久业务。修改只落在新Interview Controller/Service/独立Persistence/Mapper/XML/DTO/entity、V20、专用gateway/job executor/recovery与对应测试。直接复用resume mandatory snapshot、CurrentUser/CSRF、PageQueries、ObjectMapper和AgentScope SDK，不改resume/附件/cache现有文件、frontend、scripts/deploy、git/lifecycle。

所有外部模型调用在事务外，一次用户授权调度一次SDK attempt。当前轮提交/首次冻结/有效终态由短事务owner+version+token+lease保护；GET为纯持久读取。统一PENDING队列而非先领取全部模型lease；删除擦本场正文且保最小收据防重建。旧capture/report模型与执行器不重构、不加兼容/feature flag/默认禁用。
