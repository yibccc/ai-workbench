# 本地归档与日志范围核验

用户具体Git写入确认仍PENDING。此轮仅只读脚本/配置与状态，未执行commit、archive、add_session；自动goal续行消息不代替用户确认。

独立explorer及主会话共同核读真实实现，补齐原生自动提交边界：

- `common/safe_commit.py:176–187` 的归档helper实际传入整个 `.trellis/tasks/archive` pathspec，而非单个归档目录；`common/task_store.py:1498/1554` 使用它stage/commit。本需求依次三子→父归档，每一步先确认现有整个archive工作区/暂存区无其他脏文件，再执行；每步后核对commit变更仅相应任务原/归档路径。
- 子归档保留父children；父最后归档只查active子目录，已归档三子不会再被重写。原生CLI更新status/completedAt并移动目录、清除本会话指向任务的ignored runtime记录，不自行重写生命周期。
- `add_session.py:1115–1125` 在无current-task时虽然调用helper的task_name=None，但马上过滤所有 `.trellis/tasks/` pathspec；父归档清掉当前任务后，journal只stage开发者kira的journal-*.md/index.md，不提交00-bootstrap或其他任务。
- `safe_commit.py:94–108` 确实收集kira的全部journal-*.md及index，而非仅刚写文件；写journal前先检查该范围不存在其他会话脏改动。当前只有已跟踪且干净的journal-1/index，journal约437行，旋转上限2000。
- 所有archive/session配置hook均注释；add_session没有生命周期hook调用。core.hooksPath/commit.gpgsign未配置，`.git/hooks`无非sample；没有隐藏push或外部消息动作。

实际前置状态：`git status --porcelain -- .trellis/tasks/archive .trellis/workspace/kira` 无输出；全局staging0，当前HEAD仍基线1a7928c，115业务及296原托管任务快照均0变化。5份用户SOP不在任一原生提交范围，00-bootstrap保持active。

操作策略属于已呈现的同一个提交分组/授权范围，不修改Trellis工具或增加兼容路径。如果未来执行前出现其他会话脏路径，先隔离准确的本次差异；不能让wide pathspec暗中带入。当前没有这种脏状态，所以可以使用已计划的原生自动提交。

日志内容已在ignored `.local-runtime/community-oss-journal-body.md` 准备，真实work SHA仅在获批提交后通过`--commit`注入；正文路径也要按实际归档输出更新。具体Git确认只等待现有问题回复，不再次提出开工或另一套提交计划。

## 获批后真实执行问题与处理

首次publishing原生archive已正确move/completed/stage11，但没有生成commit。helper仍把已经移走、从未tracked的source_rel放入git commit pathspec；只读dry-run实际报“pathspec did not match any file(s) known to git”。helper返回not source_was_tracked导致CLIexit0，所以exit0不足以证明commit存在，根通过HEAD变化断言发现。

没有再次归档或改Trellis源码。保留首次原生生命周期结果，用唯一实际destination完成同名chore提交；其余使用已有archive --no-commit负责原生生命周期，再git add/commit精确destination。提交数/消息/文件范围仍原批准的4归档，无amend/push。每一步读实际新SHA/变更路径/完成状态，保留故障证据。
