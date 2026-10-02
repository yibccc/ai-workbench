# Proposed Work Commit

Commit: `fix: merge linked focus records and use password eye toggle`

This commit contains the reviewed source, regression tests and corresponding executable specs for both approved requirements.

## Included Files
- `.trellis/spec/backend/focus-routines.md`
- `.trellis/spec/backend/pagination.md`
- `.trellis/spec/frontend/focus-page.md`
- `.trellis/spec/frontend/identity-session.md`
- `backend/src/main/resources/mapper/WorkRecordMapper.xml`
- `backend/src/test/java/com/aiworkbench/service/impl/DailyRecordPresentationIntegrationTest.java`
- `frontend/e2e/workbench.spec.ts`
- `frontend/src/components/Icon.tsx`
- `frontend/src/features/auth/LoginPage.tsx`
- `frontend/src/features/records/RecordsList.tsx`

## Excluded Existing Untracked Files
These predated this task and are outside its scope:
- `docs/dev-sop/REVIEW-SKILLS.md`
- `docs/dev-sop/SOP.md`
- `docs/dev-sop/SOURCES.md`
- `docs/dev-sop/WEB-PROMPTS.md`
- `docs/dev-sop/WORKBENCH-CHECKLIST.md`

## After Approval
User approved the work commit and requested an MR on 2026-10-02. Create the work commit, then archive the completed task and record the session journal with Trellis's normal bookkeeping commits. Task planning/validation artifacts and the blank login screenshot belong to the archive commit. Publish `codex/login-focus-record-merge` and open a pull request targeting `master`. Do not restart daily application processes.

## Quality Gate
Independent review passed without changes. Backend180/180, targeted Chromium3/3, frontend lint/build and diff whitespace checks all passed. Dedicated test containers and their anonymous test volume were cleaned up; business containers/data were untouched.
