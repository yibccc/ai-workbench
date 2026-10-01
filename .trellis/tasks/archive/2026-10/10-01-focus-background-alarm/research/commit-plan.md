# Proposed work commit

Message: `fix(focus): preserve background alarm ownership and recover audio`

One coherent reminder-chain fix, including regression tests and executable contracts:

- `backend/src/main/java/com/aiworkbench/service/impl/FocusServiceImpl.java`
- `backend/src/test/java/com/aiworkbench/service/impl/FocusIntegrationTest.java`
- `backend/src/test/java/com/aiworkbench/service/impl/FocusConcurrencyIntegrationTest.java`
- `frontend/src/features/focus/useFocusController.ts`
- `frontend/src/features/focus/FocusPage.tsx`
- `frontend/src/App.tsx`
- `frontend/e2e/focus-alarm.spec.ts` (new)
- `frontend/e2e/workbench.spec.ts`
- `.trellis/spec/frontend/focus-page.md`
- `.trellis/spec/backend/focus-routines.md`

Unrelated existing untracked files excluded from this work commit:

- `docs/dev-sop/REVIEW-SKILLS.md`
- `docs/dev-sop/SOP.md`
- `docs/dev-sop/SOURCES.md`
- `docs/dev-sop/WEB-PROMPTS.md`
- `docs/dev-sop/WORKBENCH-CHECKLIST.md`

The task artifacts are handled by the later Trellis archive bookkeeping commit; the journal is recorded afterward. Do not amend. The user approved completion, push and MR creation on 2026-10-01. Work commit, archive and journal commits precede the authorized push. Use `codex/focus-background-alarm` from master `949026e` because the old completion-alarm PR9 is already merged.
