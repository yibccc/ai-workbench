# Execution plan

## Planning gate

- [x] Inspect controller, API, lease/settlement, UI and existing tests.
- [x] Converge PRD and design with no unresolved product decisions.
- [x] Curate implement/check spec manifests.
- [x] Final planning summary approved by the user: "开始修改和测试" (2026-10-01).

## Ordered implementation

1. [x] Backend: target-ended lease-only checkpoints; version/generation/owner fencing and settlement idempotency; real-DB renewal/takeover/terminal/clock-rollback regression coverage. Final Maven verify: 173 passed.
2. [x] Client: sound-eligible hidden renewal, pending-ended sync, known-session refresh/conflict recovery, no historical replay.
3. [x] Audio: separate opt-in/status, bounded guarded resume, user recovery, native looping source with same-generation stop extension, dismissal/account cleanup guards.
4. [x] Hidden completion detection, unchanged foreground microbreak rules, classified sound/action UI.
5. [x] Automated regressions: 13 new cases plus 17 existing focus cases verified, including suspended/interrupted/closed recovery, takeover, timeout, stop races and account cleanup. Native real-elapsed hidden test passed (~6 minutes, 528 ms first-loop delay).
6. [x] Full-scope Trellis review and fixes, lint/build, focus spec sync.
7. [x] Native real-elapsed hidden test, unique settlement verification and task-owned resource cleanup complete; final evidence in research/validation.md.
8. [x] Scoped commit plan presented; user authorized completing the work, pushing and creating an MR on 2026-10-01. Proceed with work commit, archive/journal, push and MR creation.

## Validation

- Frontend: npm run lint; npm run build.
- Backend: targeted Maven Focus integration/concurrency/HTTP tests using existing isolated PostgreSQL/Redis test setup; inspect configuration first.
- Browser: npm run e2e -- --grep <focus-pattern> with explicit isolated E2E_DATABASE_URL, POSTGRES_USER, POSTGRES_PASSWORD and REDIS_PORT required by playwright.config.ts. Never use development data/volumes.
- Run a real elapsed >5-minute hidden-tab scenario when the isolated environment permits; record browser/version, URL and detection/source-start delay. Audio mocks/source assertions do not prove physical audibility; report missing device-audibility validation explicitly.

## Risk and rollback

Review terminal settlement, version conflicts, lease takeover and async recovery as one lifecycle. Preserve unrelated untracked docs/dev-sop files. Fix/revert only this task's scoped changes; do not redesign time accounting or add a compatibility gate.
