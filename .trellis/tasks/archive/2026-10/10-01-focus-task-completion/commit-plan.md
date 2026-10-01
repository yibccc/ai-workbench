# Proposed commit

`fix(focus): complete linked task when saving progress`

## Included code, tests and specs

- backend/src/main/java/com/aiworkbench/service/impl/FocusServiceImpl.java
- backend/src/test/java/com/aiworkbench/service/impl/FocusConcurrencyIntegrationTest.java
- backend/src/test/java/com/aiworkbench/service/impl/FocusIntegrationTest.java
- backend/src/test/java/com/aiworkbench/service/impl/FocusUpgradeIntegrationTest.java
- frontend/src/features/focus/FocusPage.tsx
- frontend/e2e/workbench.spec.ts
- .trellis/spec/backend/focus-routines.md
- .trellis/spec/frontend/focus-page.md

The current task's PRD, design, execution/context manifests and verification notes are tracked with the authorized task bookkeeping. The user approved commit, archive, remote push and MR creation. Create the work commit first, then archive this task and record the journal through the Trellis scripts; push branch codex/focus-task-completion and open a PR against master.

## Existing unrelated files excluded

- docs/dev-sop/REVIEW-SKILLS.md
- docs/dev-sop/SOP.md
- docs/dev-sop/SOURCES.md
- docs/dev-sop/WEB-PROMPTS.md
- docs/dev-sop/WORKBENCH-CHECKLIST.md

## Validation

Final frontend lint/build passed; 177 backend tests and 7 browser regressions passed with no failures; diff whitespace check passed. See validation.md and check.md for evidence.
