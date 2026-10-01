# Validation evidence

## Isolated environment

- Windows 11, Node v25.2.1, Maven 3.9.9, Java 17; Docker CLI via WSL Ubuntu.
- Dedicated containers created for this task: `focus-alarm-test-pg-20261001` (`postgres:17.6-alpine`, loopback 25432, database `focus_alarm_tests`) and `focus-alarm-test-redis-20261001` (`redis:7.4.2-alpine`, loopback 26379). Names verified absent before creation.
- Both endpoints reachable from Windows; pg_isready reported accepting connections.
- Backend schema: `d9_backend_tests`. Browser schema: `d9_e2e`. Synthetic credentials only; the development PostgreSQL 5432 and Redis 6379 remain untouched.
- Browser entry under the repository Playwright config: `http://127.0.0.1:15173`, backend `http://127.0.0.1:18080`.

## Evidence boundaries

Clock simulation, visibility getter injection and instrumented audio source lifecycle are behavioral regression evidence. Real elapsed waiting validates server time; it alone does not demonstrate browser-native hidden-tab throttling. No physical device-audibility observation has been made.

## Results

Backend `mvn clean verify` passed 172 tests. After adding the final clock-rollback regression, `mvn verify` passed 173 tests with no failures/errors/skips. Focus-specific integration/concurrency/HTTP run initially passed 35 tests. Override both TEST_DATABASE_URL and LIVE_ACCEPTANCE_DATABASE_URL for full-suite isolation; the latter uses `focus_alarm_tests/d9_live_acceptance` on port 25432.

Frontend lint/build passed. Initial browser runs exposed test-login waiting, AudioContext mock API omissions, virtual-clock installation order, and old sound-resume expectations; the tests are being corrected and rerun without retries. Do not count these initial runs as final passes.

Final product code was frozen before browser verification. Unified no-retry Playwright run selected 30 cases: 28 passed, two failed only the old absolute resume-count expectation (`>=2`, actual 1). Those expectations were changed to an increase from the pre-pause baseline; the two cases passed in a separate no-retry run (`--grep '复用曾启声的'`, 2 passed in 15.6 seconds), with no intervening product changes. All 30 selected cases are therefore verified: 13 new reminder regressions and 17 existing focus cases. No remaining failed/flaky case exists in the scoped verification set.

Native CDP probe confirmed Chrome/140.0.7339.186 reports real hidden while minimized. The first longer attempt was invalidated when visibility later changed to visible; it is not successful native-hidden evidence. A same-window control tab is being used for the final real-elapsed run, against a separate `focus_native_tests/d9_e2e` database and servers on 18081/15174.

### Final native result (PASS)

- Actual browser: `Chrome/140.0.7339.186`; entry: `http://127.0.0.1:15174` (task-only isolated server).
- Native visibility stayed hidden through all 20-second observations. No `document.visibilityState` override or virtual clock was used. A separate same-window blank tab was activated and the native window minimized; direct CDP attachment avoided Playwright's focus emulation.
- Real hidden time before the first completion loop: 359235 ms (approximately six minutes); first-loop delay relative to the server target: 528 ms.
- 12 hidden checkpoints; maximum observed checkpoint gap 60012 ms, demonstrating native background scheduling slowdown. One controller ID and generation 1 remained authoritative throughout.
- Target ended with exactly 360000 ms net focus and 0 ms break, one daily record (`count=1`, `sum(focus_ms)=360000`, `sum(break_ms)=0`). No `/end` request was sent.
- The real `AudioBufferSourceNode` loop started while the real context was running and the native document was hidden. The visual stop action dismissed the alarm with no additional loop start. Physical speaker audibility was not observed.
- Raw evidence: ignored `.local-runtime/focus-native-background-result.json`; reproducible direct-CDP script: `.local-runtime/focus-native-background.mjs`. Reset the isolated database through the test API before loading an existing active page; otherwise its checkpoint can contend with test-only TRUNCATE. This fixture issue required no product change.

### Cleanup

All task-owned browser processes, backend/frontend listeners (18080/15173 and 18081/15174), CDP listener 19229 and the two created test containers/anonymous volumes were removed. Native Java launcher spawned an actual JVM child; cleanup verified that child's PID, creation timestamp and exact command. Development PostgreSQL 5432, Redis 6379 and existing unrelated containers remain running.

### Final status

Code, contracts, full-scope review, lint/build, 173 backend tests, all 30 scoped browser cases and the native real-elapsed background check are complete. No unresolved code/test issue remains. The user subsequently approved completion, push and MR creation. Use a new `codex/focus-background-alarm` branch from master `949026e` (identical product tree to the tested checkout), since old PR9 is merged.

## Timer-throttling decision

Chrome's official timer-throttling documentation was checked during this task: hidden >5 minutes, silent >30 seconds and chained timer conditions permit one-minute checks. The previous 60-second lease matched that cadence without dispatch/network margin. The repair uses a 120-second lease, preserving bounded generation-fenced takeover. This is a reminder-lifecycle change, not focus accounting or compatibility behavior.
