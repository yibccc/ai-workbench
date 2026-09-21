# D9 测试数据治理记录

执行时间：2026-09-20（Asia/Shanghai）

## 归属证据

- 项目候选同时满足：名称为 `AI捕获项目-<UUID>` 形状；关联待办和记录全部具有 `mixed-%` 的 `capture_inputs.client_request_id`；没有其他来源关联。
- 待办候选同时满足：来源 requestId 为 `mixed-%` 或 `task-only-%`（与 `AiCaptureIntegrationTest` 固定前缀一致）；`version=0`；未软删；无 task event；无自动完成记录。
- 报告快照只作为不可变历史引用保留，不作为可删除对象。没有读取工作原文、备注或密钥。
- `AI工作台`、`hello-agent` 和不满足全部条件的数据均未进入候选。

## 候选与执行结果

- 确认测试项目：17 个；按 `created_at,id` 取最早 `floor(17/2)=8` 个，仅归档，不硬删。
- 确认测试待办：51 个；按 `created_at,id` 取最早 `floor(51/2)=25` 个，通过条件更新软删并把版本从 0 增至 1。
- 项目 ID：`3d2e8b80-5a17-49d5-a3b7-4dd966877aff`、`5e439a62-2a1f-4b00-b22f-4e09ccb8e640`、`5446309b-fd1e-4a73-a768-27fead55c95d`、`f942b79e-caa0-495c-b2c4-9b8229a414c2`、`d541febd-8e8a-40b4-80ac-17e1c788db17`、`04055f3d-32d7-4257-a66f-cbdf676fc9d2`、`a751a7ef-007a-49bd-bb42-64a675d44d37`、`09b9c0b1-bcd1-40cc-bf4d-ef1f07968f6a`。
- 待办 ID：`479fb2a6-8eff-4893-9fa4-be0337104469`、`61c12089-6d9d-437e-85da-feda1df78a8a`、`f7d305d2-d0dd-457f-a432-f5de84c7aac3`、`2e176d17-384d-4f84-adac-ec48a083019f`、`e53896ac-5fa0-4a00-b37c-49246048029e`、`0c16fd57-af41-4ac4-84ad-e64ee254febf`、`7979ed6e-34bc-42b7-a2e5-5e9679aa3fd5`、`cb226641-091e-4bbb-bf1b-a385cb2a8088`、`0c378df4-1eb0-452d-8f91-37d3373247ad`、`90962316-b68a-4cf2-abca-503385e56f06`、`c1e5ecbe-90f5-4ec1-aa14-ec3eec9fc706`、`0602f5eb-1f1e-41d0-8809-c42eae5a3669`、`45dcb740-82b2-4043-8043-73d8cec206e4`、`78882a3e-096f-4450-be45-55523b207aa2`、`8e0c6f4b-7222-4c28-82a4-827071b3dd06`、`ab89f72e-0c65-41d5-9331-a9c292b35457`、`f3adc2e1-eae5-48ff-ba1d-67729b2ebbeb`、`3a820c6e-b49d-435a-b4c8-e3d22475a609`、`52371039-8fb1-4317-9571-0eff827695b5`、`c3149be1-03ef-478f-8cc6-d23becf6afce`、`55d6c96f-22f2-47da-92f6-31a5bafd69f1`、`24327708-95a0-4a80-bf8f-9c6d8394d904`、`56fe1cf6-9625-46a9-aaef-a5d755454d9f`、`d569c430-21a6-4513-a867-fd2d1514d63d`、`2382c166-1066-4391-a994-6e0e24dc8979`。

## 保护与恢复

- 清理前备份：`.local-backups/d9-test-project-cleanup-20260920.txt` 与 `.local-backups/d9-test-cleanup-20260920.txt`；目录已加入 `.gitignore`。
- 备份逐行包含 ID、可读名称/标题、创建时间、requestId（待办）和带状态条件的恢复 SQL。
- 清理在 `SERIALIZABLE` 事务中执行；实际更新数与预选数量不一致时整体回滚。
- 恢复时先复核 ID，再在一个事务内执行备份中的 SQL。报告快照和事件未删除，无需恢复。
- 后续 Maven 集成测试改用 `d9_backend_tests` schema；Playwright 继续使用 `d9_e2e`，不再向业务 `public` schema 写测试夹具。

## 终审只读复核

- 2026-09-20 终审仅执行 SELECT：备份列出的 8 个项目仍全部为 `ARCHIVED`，25 个待办仍全部为软删除状态。
- `public.report_sources` 仍有 232 行；`task_events` 当前为 0 行。终审未执行任何清理、恢复或业务写入。
- 当前默认可见数据为 11 个活动项目、28 个未软删待办；默认项目/待办查询继续排除归档和软删除数据。
- 两份本地备份仍可读，`.local-backups/` 命中 `.gitignore`，凭据模式扫描无命中。
