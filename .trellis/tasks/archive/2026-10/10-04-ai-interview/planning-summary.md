# AI 面试最终规划确认

日期：2026-10-04。任务：`.trellis/tasks/10-04-ai-interview`，及resume/engine/ui三个子任务。当前均planning，未开工。

## 目标和范围

直接迁入已有HTML原型的业务内容和CSS，并复用当前工作台账号、RetainedView、专注控制器、HTTP/Dialog/Toast、ObjectStorage和Markdown校验。接入真实账号私有当前简历、JD、固定文字面试、逐轮草稿/锁定、评估、历史、多场恢复和手动删除。

简历支持.md和Markdown粘贴；四方向Java后端/React前端/Agent开发/全栈；三难度默认中级；主问默认5、完整3–20，每题一条预生成追问，总2N轮。交卷未答按0，系统失败不伪造0/完整总分，失败只手动模型retry。

## 待批准的入口选择 P-01

采用原型建议：AI面试为第六个retained主工作区；私有个人中心仅由账号菜单进入。公开社区作者主页继续承担其现有公开资料。原五工作区、专注和账号隔离保持原合同。

## 已核实的关键技术处理

- 原型是静态screen，不把演示切屏/数据当真实实现；直接复用内容/CSS，补真实状态和完整40轮。
- 普通编辑已导入简历保留原件；显式粘贴替换/新File/删除才改变原件引用。原件字节与最终编辑正文分别保存。
- 成功JD分析同owner/direction/原文hash可多场复用；创建只复制快照，修改/移除/确认取消才使旧分析失效。
- SDK默认自动retry已查明，新面试gateway显式maxAttempts=1，并以真实SDK+local fake HTTP计数验证。
- 专用耐久jobs、逐组lease/token、排队期限、启动/周期只FAILED恢复；owner和receipt保护并发/删除迟到/丢ACK。
- 本地RustFS为唯一当前adapter。业务保持ObjectStorage/key/原bytes/size/SHA，未来补OSSadapter+同key迁移/校验后切配置，业务API/DB无需重做；本轮不申请/实现/调用阿里云API。
- 最小增加简历对象prefix，补专有Nginx上传运输限额与PG+对象联合隔离恢复。

## 验收和交付顺序

原26FR/26INT-AC完整保留：账号隔离、严格2N、提交锁定/草稿恢复、评分/失败区分、简历/JD快照、私有原件/清理、码点上限、手动retry/fencing、最大组合、多场隔离及旧工作区回归。

按resume → engine → ui → 父整合推进；批准后每子任务实现→独立审查/修复→真实隔离验证，UI骨架可在不冲突时并行。一般工程问题记录issues，在最后集中确认。

## 延期和非目标

语音/RAG/动态追问、PDF/Word、多份简历/第五方向、微服务/通用队列、自动模型retry、回收站/按天删除不在本期。阿里云账号/API/adapter/实际迁移、真实付费模型最大输入质量/费用/延迟延期；先以fake gateway/真实SDK fake HTTP和隔离PG/Redis/RustFS做工程验收。外部AGPL源码/Prompt/Skill本轮不复制。

## 文档和核验状态

父与三子共12份正式PRD/design/implement齐备；4任务Trellis validate实际PASS且无警告；14源/副本SHA一致；52条FR/AC与源正文精确相同；独立静态审阅无实质开放问题。产品测试全部NOT_RUN，不把规划校验当产品验收。

正式合同：[prd](prd.md)、[design](design.md)、[implement](implement.md)；证据：[planning-validation](research/planning-validation.json)、[静态审阅](research/planning-review-findings.md)、[问题台账](research/issues.md)。

## 一次批准的范围

“批准并连续执行”表示批准本最终方案、P-01、三个子任务实现/审查/修复/隔离验证、规范沉淀与本地提交/归档收尾；同一已批准范围持续推进，不重复索取权限，普通问题最后汇总。新增范围/具体兼容/不可逆生产操作、真实付费模型和外部push/PR/发布仍须独立明确授权。当前没有伪造批准或执行这些动作。

如需调整，保持planning并指出具体方案部分；下一条消息真实批准后按trellis-continue原生恢复。
