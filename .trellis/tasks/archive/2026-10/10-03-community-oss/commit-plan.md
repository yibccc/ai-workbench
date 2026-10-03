# 本地提交与Trellis收尾计划

状态：用户于2026-10-03 Asia/Shanghai后续明确回复“确认”，已批准本清单3次本地业务提交、本需求4任务归档和journal；不得扩展为push/PR/云操作。

业务分支 `codex/community-oss`，基线 `1a7928c2b2fa15ce0d3148af187770d85aca96f7`。精确文件清单如下，禁止git add . / amend / push。

## 1. feat(community): add member publications and private RustFS attachments

76个实际文件：

- `.env.example`
- `backend/pom.xml`
- `backend/src/main/java/com/aiworkbench/config/CommunityCacheControlFilter.java`
- `backend/src/main/java/com/aiworkbench/config/StorageProperties.java`
- `backend/src/main/java/com/aiworkbench/controller/AttachmentController.java`
- `backend/src/main/java/com/aiworkbench/controller/CommunityController.java`
- `backend/src/main/java/com/aiworkbench/controller/CommunityModerationController.java`
- `backend/src/main/java/com/aiworkbench/controller/CommunityProfileController.java`
- `backend/src/main/java/com/aiworkbench/controller/PublishingController.java`
- `backend/src/main/java/com/aiworkbench/dto/community/AttachmentModels.java`
- `backend/src/main/java/com/aiworkbench/dto/community/CommunityModels.java`
- `backend/src/main/java/com/aiworkbench/dto/publishing/PublishingModels.java`
- `backend/src/main/java/com/aiworkbench/entity/community/AttachmentRow.java`
- `backend/src/main/java/com/aiworkbench/entity/community/CommunityRows.java`
- `backend/src/main/java/com/aiworkbench/enums/CommunityPostStatus.java`
- `backend/src/main/java/com/aiworkbench/enums/CommunityPostType.java`
- `backend/src/main/java/com/aiworkbench/exception/ApiExceptionHandler.java`
- `backend/src/main/java/com/aiworkbench/exception/AttachmentException.java`
- `backend/src/main/java/com/aiworkbench/mapper/AttachmentMapper.java`
- `backend/src/main/java/com/aiworkbench/mapper/CommunityPostMapper.java`
- `backend/src/main/java/com/aiworkbench/mapper/CommunityProfileMapper.java`
- `backend/src/main/java/com/aiworkbench/mapper/WorkRecordMapper.java`
- `backend/src/main/java/com/aiworkbench/service/AttachmentService.java`
- `backend/src/main/java/com/aiworkbench/service/CommunityModerationService.java`
- `backend/src/main/java/com/aiworkbench/service/CommunityProfileService.java`
- `backend/src/main/java/com/aiworkbench/service/CommunityService.java`
- `backend/src/main/java/com/aiworkbench/service/PublishingService.java`
- `backend/src/main/java/com/aiworkbench/service/impl/AttachmentPersistenceService.java`
- `backend/src/main/java/com/aiworkbench/service/impl/AttachmentServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/CommunityModerationServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/CommunityProfileServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/CommunityServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/E2eMaintenanceServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/service/impl/PublishingServiceImpl.java`
- `backend/src/main/java/com/aiworkbench/storage/AttachmentValidationWorker.java`
- `backend/src/main/java/com/aiworkbench/storage/AttachmentValidator.java`
- `backend/src/main/java/com/aiworkbench/storage/ObjectStorage.java`
- `backend/src/main/java/com/aiworkbench/storage/PdfStructureValidator.java`
- `backend/src/main/java/com/aiworkbench/storage/RustFsObjectStorage.java`
- `backend/src/main/java/com/aiworkbench/storage/StorageException.java`
- `backend/src/main/java/com/aiworkbench/storage/ValidatedAttachment.java`
- `backend/src/main/java/com/aiworkbench/storage/WebPStructureValidator.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V17__community_publishing.sql`
- `backend/src/main/resources/db/migration/V18__community_attachments.sql`
- `backend/src/main/resources/mapper/AttachmentMapper.xml`
- `backend/src/main/resources/mapper/CommunityPostMapper.xml`
- `backend/src/main/resources/mapper/CommunityProfileMapper.xml`
- `backend/src/main/resources/mapper/WorkRecordMapper.xml`
- `backend/src/test/java/com/aiworkbench/community/AttachmentConcurrencyIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/AttachmentHttpIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/AttachmentMigrationIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/AttachmentQuotaIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/AttachmentReadConsistencyIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/CommunityHttpIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/CommunityMigrationIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/CommunityPublishingIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/CommunitySourcesIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/community/CommunityTestData.java`
- `backend/src/test/java/com/aiworkbench/e2e/E2eResetControllerTest.java`
- `backend/src/test/java/com/aiworkbench/storage/AttachmentValidationTest.java`
- `backend/src/test/java/com/aiworkbench/storage/RustFsStorageIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/support/AttachmentTestFiles.java`
- `backend/src/test/resources/storage/GeneratePublicKeyPdf.java`
- `backend/src/test/resources/storage/README.md`
- `backend/src/test/resources/storage/animated.webp`
- `backend/src/test/resources/storage/high-compression.webp`
- `backend/src/test/resources/storage/public-key-encrypted.pdf`
- `backend/src/test/resources/storage/static.webp`
- `compose.yaml`
- `deploy/nginx.conf`
- `scripts/local/browser-env-check.mjs`
- `scripts/local/initialize-storage.py`
- `scripts/local/start.ps1`
- `scripts/local/storage-smoke.py`
- `scripts/local/verify-compose.py`

## 2. feat(community): reuse R2 publishing views and protected attachments

28个实际文件：

- `frontend/e2e/community.spec.ts`
- `frontend/e2e/focus-alarm.spec.ts`
- `frontend/e2e/start-vite.mjs`
- `frontend/e2e/workbench.spec.ts`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/playwright.config.ts`
- `frontend/src/App.tsx`
- `frontend/src/api/community.ts`
- `frontend/src/api/http.ts`
- `frontend/src/api/publishing.ts`
- `frontend/src/components/DialogProvider.tsx`
- `frontend/src/components/dialogContext.ts`
- `frontend/src/components/layout/AppShell.tsx`
- `frontend/src/components/layout/routes.ts`
- `frontend/src/features/auth/AccountMenu.tsx`
- `frontend/src/features/auth/LoginPage.tsx`
- `frontend/src/features/community/AttachmentMedia.tsx`
- `frontend/src/features/community/CommunityChrome.tsx`
- `frontend/src/features/community/CommunityPages.tsx`
- `frontend/src/features/community/CommunityWorkspace.tsx`
- `frontend/src/features/community/PublicationDocument.tsx`
- `frontend/src/features/community/community.css`
- `frontend/src/features/community/integration.css`
- `frontend/src/features/community/model.ts`
- `frontend/src/features/community/ui.tsx`
- `frontend/src/features/publishing/PublishingEditor.tsx`
- `frontend/src/features/publishing/SourcePicker.tsx`

## 3. docs(community): record delivery and publication contracts

11个实际文件：

- `.trellis/spec/backend/community-publication.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/linux-deployment.md`
- `.trellis/spec/backend/local-delivery.md`
- `.trellis/spec/backend/private-attachments.md`
- `.trellis/spec/frontend/community-publishing.md`
- `.trellis/spec/frontend/dialogs.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/index.md`
- `README.md`
- `docs/dev-sop/WB-20261003-community-oss-3f9aaa/delivery.md`

## 随后的原生Trellis记录

业务提交全部在前。先依次归档本需求的publishing/storage/UI三子任务，再归档父community-oss，均使用原生task.py archive的精确范围与自动commit；最后add_session记实际三个work commit，产生journal自动commit。其他bootstrap任务保留active，不并入此次归档。

原生归档helper实际stage整个archive，journal helper收集kira全部journal/index；当前这些范围干净。执行每一步前复查既有归档/日志与staging，之后核对本次commit只出现上述需求路径，保证实际提交差异符合本计划。详细代码证据见research/finish-scope-audit.md；分组、文件清单和排除范围不变。

当前四任务下的规划、不可变输入、脱敏验证/审查报告、原型参考/正式PNG、geometry及task.json/context由各自归档commit记录，不混进业务分组。Raw trace/video/secret env/运行日志/构建产物在ignored目录，不归档提交。

执行第3文档commit前，把已经生成的第1/2业务SHA写回delivery；其本身/后续archive/journal SHA不可能自引用预填，实际最终SHA在journal/最终答复记录。

## 明确排除的已有用户文件

这些是会话开始前5份无关未跟踪SOP，保持原样，全部不提交：

- `docs/dev-sop/REVIEW-SKILLS.md`
- `docs/dev-sop/SOP.md`
- `docs/dev-sop/SOURCES.md`
- `docs/dev-sop/WEB-PROMPTS.md`
- `docs/dev-sop/WORKBENCH-CHECKLIST.md`

## 确认边界

`.trellis/workflow.md` Phase3.4原文：“Present the plan once, ask for one-shot confirmation”。回复同意后执行上述本地提交+仅本需求归档/日志，不推送。回复manual/我自己来则按该流程等待用户手动提交，再进行获准的收尾，不改分组反复请求。

没有auto-review拒绝。此确认来自仓库明确流程及既有授权边界，不是重新询问开工。
