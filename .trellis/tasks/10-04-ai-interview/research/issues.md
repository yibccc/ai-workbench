# 规划及后续问题台账

## 需要用户最终确认

| ID | 问题/推荐方案 | 状态/阻塞边界 |
|---|---|---|
| P-01 | 第六retained工作区“AI面试”；私有个人中心仅AccountMenu入口，公开社区profile保留 | 用户已在最终确认选“批准并连续执行”，已实施/验收 |

最终批准已覆盖三个子任务及本地提交/归档；一般问题交付末尾汇总。无新增需要用户决策的产品阻塞。

## 已由证据处理，不重复问用户

| ID | 发现 | 规划处理 |
|---|---|---|
| T-01 | 当前RustFS唯一adapter，OSS名称不是已实现支持 | ObjectStorage/key/bytes/SHA合同；未来adapter+迁移验收，本轮不云API |
| T-02 | SDK默认maxAttempts=3与manual-only冲突 | 新gateway显式1+真实SDK fakeHTTP计数 |
| T-03 | 原异步恢复不完整，排队lease易假过期 | 独立统一jobs、领取后lease、queue deadline、启动+周期只FAILED |
| T-04 | 原型仅静态/题量离散/HTML与PNG不一 | 原HTML/CSS直接迁业务content，补完整状态，截图参考 |
| T-05 | 新profile容易被'/'community判定误分类 | #interview主区/#profile辅助，明确routes，无旧alias |
| T-06 | Markdown byte/text上限复合 | 独立byte gate和codepoint gate验证，不截断 |
| T-07 | 当前IAM只社区，initializer旧policy guard拒更新 | 显式最小canonical policy更新，保原prefix、禁止admin/全桶 |
| T-08 | 代理default1m不足multipart | 仅resume import2m，业务1MiB不变 |
| T-09 | backup只PG且固定container | 补明确隔离目标与object manifest/新DB+桶恢复 |
| T-10 | SDK/http、业务表、缓存guard差异 | 私有no-store/owner、d9_*_tests_日期schema、实际commands |
| T-11 | current delete版本ABA/删除create重放 | singleton单调version与最小receipt，敏感正文清除 |
| T-12 | 网页工作台commit不可本地diff | 明确bad object限制，用逐文件真实核对，不伪造diff |

## 延期/非阻塞事项（最后汇报）

| ID | 内容 | 后续需要 |
|---|---|---|
| D-01 | 阿里云adapter/API申请/真实访问/同key对象迁移 | 用户有资源后独立云接入任务与配置/迁移验收 |
| D-02 | 最大组合真实模型质量/延迟/费用/context容量 | 先fakeHTTP/fixture工程验收；真实付费调用明确授权后测量 |
| D-03 | 外部ai-interview AGPLJava/Prompt/Skill复制 | 当前不复制；将来具体范围与许可义务核对/明确批准 |
| D-04 | Vite主产物约563KB的构建提示 | 构建通过，非阻塞；后续按实际性能需求评估拆包 |

## 实施中已解决的问题

| 问题 | 处理与最终证据 |
|---|---|
| 迟到put、lease到期、版本ABA及删除重放可能复活旧数据 | 持久reservation/receipt、单调version、最后wall-clock CAS；真实PG并发/到期/重启用例通过 |
| SDK默认重试3次及非可信正文进入系统消息 | maxAttempts=1，System/User分离；本地真实HTTP线计数与完整JSON用例通过 |
| UI旧GET覆盖ACK、失去身份后对话框和私有投影残留 | 版本与generation barrier、清理重试元组、强制dismiss；fixture与真实链通过 |
| 报告主问题序号按turnIndex显示错误 | mainIndex/2+1；真实浏览器主问题编号与13.35报告通过 |
| 完整后端首轮测试继承synthetic bootstrap且live URL不正确 | 窄化测试env并使用独立schema，未改变账号规则；最终271全通过 |
| Python→WinPS模块环境与psql多行JSON解析影响真实备份 | 子进程移除PSModulePath、quiet/full JSON解析；33ops与真实2对象恢复通过 |
| 撤销会话旧夹具点击已销毁导航，根临时清理在pwsh7时间比较拒绝 | 修复夹具时序保真实401与完整Toast；既定WinPS5.1入口严格核身份清理，117浏览器通过 |

最终无开放产品阻塞或新增兼容方案。上述问题在授权范围内修复后已复审，不需要再次批准。

不得把DEFERRED或静态检查写成产品PASS。真正新增产品/兼容/不可逆风险只停止依赖动作，其余已授权工作继续。
