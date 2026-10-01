# Validation

## Environment

- Windows 11; tests use dedicated containers `focus-completion-test-pg-20261001` (PostgreSQL 17.6, loopback 25433, database `focus_completion_tests`) and `focus-completion-test-redis-20261001` (Redis 7.4.2, loopback 26380).
- Backend schema `d9_backend_tests`; browser schema `d9_e2e`. Synthetic test credentials only. Existing business PostgreSQL 5432 and Redis 6379 are excluded.
- Repository Playwright entry `http://127.0.0.1:15173`, isolated backend `http://127.0.0.1:18080`.
- Backend final `mvn verify`: 177 tests, zero failures/errors/skips; BUILD SUCCESS. TEST_DATABASE_URL and LIVE_ACCEPTANCE_DATABASE_URL both explicitly target the isolated database with separate schemas. Full command output: ignored `.local-runtime/focus-completion-backend-final.log`.
- Frontend final `npm run lint` and `npm run build` (TypeScript + Vite) passed after the check agent's final queue-preservation fix.
- `git diff --check` passed.
- Initial backend run found one newly-added test incorrectly asserting empty string against the original nullable work-record progress; corrected its expectation to NULL and the full rerun passed. Initial browser run used incorrect status-role assertions for error toasts; errors render as alerts. These initial failures are not counted as passing evidence.
- Final unified browser run: `npm run e2e -- --grep '待办专注保存进展|保存版本冲突保留进展|临时专注可保存空进展|专注独立页从待办|带入待办时长优先|结束专注只在记录列表|结束已提交但响应丢失' --retries=0`: all 7 passed in 34.5 seconds. This includes real linked completion/result, failure text retention/retry, 409 refresh, next task brought in while save is pending, summary failure after acknowledged save, unlinked optional save/reset, retained-view flow and existing record/report behavior. Output: ignored `.local-runtime/focus-completion-e2e-final.log`.
- Final frozen code includes the independent check agent's queue-preservation fix. No open quality findings remain.
- Test servers on 15173/18080 exited. Dedicated test container names/IDs were inspected before removal; both test containers and their anonymous test volumes were removed. Existing development containers remain outside cleanup scope.

## Scope

Progress-save atomic task completion, matching completion result, failed-save rollback/text retention, duplicate prevention, successful new-form reset and queued next-task preservation. No reminder timing or physical audibility claims are needed for this change.
