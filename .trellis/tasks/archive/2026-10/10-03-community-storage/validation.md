# Storage implementation validation

Scope: shared worktree on `codex/community-oss`, baseline `1a7928c2b2fa15ce0d3148af187770d85aca96f7`; V18 plus attachment/backend publication integration, infrastructure and meaningful tests. V1–V17 are untouched by this agent. This is an in-progress evidence log, not parent AC completion.

## Passed

All Maven commands use Java17 `C:\Program Files\Java\jdk-17`, Maven3.9.9 `D:\APPS\apache-maven-3.9.9\bin\mvn.cmd`, `-s ./backend/maven-settings-aliyun.xml -f ./backend/pom.xml`, UTF-8 and `. ./.local-runtime/community-oss-import-env.ps1 | Out-Null`. Only isolated PostgreSQL15432 / Redis16379 / RustFS19000, database `wbcommunityoss_test`, bucket `wbcommunityoss-e2e`; current test schema `d9_community_storage_tests_20261003`, separate live schema `d9_live_acceptance`.

| Command / evidence | Actual result |
|---|---|
| `-Dtest=RustFsStorageIntegrationTest test` (initial combined file tests run) | SDK test PASS; 65,537-byte original SHA/length, explicit raw-SHA256 checksum, malformed checksum refused, anonymous GET/HEAD403, Put/Get/Delete and post-delete failure. Fixed AWS2.55.10/Apache5, non-chunked path style; no protocol fallback. |
| `-Dtest=AttachmentValidationTest test` | PASS; JPEG/PNG/PDF, UTF-8/BOM/empty MD; nonempty/empty-password encrypted PDF; bad extension/disguise/truncation/UTF8/NUL/control failures; MD exact1MiB/+1. |
| `-Dtest=AttachmentConcurrencyIntegrationTest,CommunityPublishingIntegrationTest,CommunityHttpIntegrationTest test` | Initial targeted core publishing14 PASS; attachment7 subsequently PASS after same-JVM-clock deadline injection. |
| `-Dtest=AttachmentHttpIntegrationTest test` | 3 PASS: actual Servlet/Cookie/CSRF/Redis, wrong owner404 before parsing, missingCSRF403, anonymousGET/HEAD401, foreign/ADMIN private404, Range/conditions authorized full200, no-store/private/nosniff/noredirect, originalPNGinline and emptyMDdownload; F1/F2/current/history, withdraw/hide. |
| `-Dtest=AttachmentValidationTest,AttachmentQuotaIntegrationTest test` | 8 PASS: real20MiB PDF/5MiB PNG and 50MiB total allow equality with original SHA download; +1 business413/collection409; retained history does not consume current quota; static/animation/high-compression WebP and truncation. |
| `clean verify` | 2026-10-03 04:43 Asia/Shanghai, exit0; **220 tests, 0 failures, 0 errors, 0 skipped**, Boot repackage succeeded. Log `.local-runtime/community-storage-full-verify.log`. Includes new DB READY-confirmation failure release, revision subwrite rollback preserving text/refs/pointer/version, deleting binding fencing and abandoned delete token recovery. |
| `-Dtest=AttachmentMigrationIntegrationTest test` | 1 PASS (added after full run): real compositeFK rejects crossowner/post/revision references and prevents deleting history-referenced attachment; no partial refs. |
| `python scripts/local/initialize-storage.py --env-file .local-runtime/community-oss-e2e.env --endpoint http://127.0.0.1:19000 --bucket wbcommunityoss-e2e` twice | Both exit0 initialized-and-verified: separate management/application, exact minimalprefix policy+admin explicitDeny, appPut/Get/Delete, anonymous403, crossprefix403, bucket policy/ACL/admin/self-management403. Existing exact canonicalpolicy/user reused without password replacement. |
| Actual fat JAR `java -Xmx128m -Dloader.main=com.aiworkbench.storage.AttachmentValidationWorker -cp backend/target/backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher <file> <extension>` | All7 originals exit0: exact20MiBPDF, exact5MiBPNG, emptyMD/BOMMD and static/animated/high-compressionWebP. Evidence `.local-runtime/storage-native-fixtures/fatjar-worker-evidence.json`; this proves Boot nested-dependency worker launch, separately from flat Surefire. |
| `python -m py_compile scripts/local/initialize-storage.py scripts/local/storage-smoke.py scripts/local/verify-compose.py`, `git diff --check` | exit0; existing LF→CRLF Git warnings only. |

Exact proxy-fixture digests: PDF20MiB `f9bdc96ba32532a3899e5d9e13c3c3cf9b43578f454dfbb2898b2634cb18aa6c`; PNG5MiB `cbbef110575babcd4afe9a302500c337b268e51f5648f5be43ef601586772493`. Both subsequently proved through the actual Nginx/fat-JAR/RustFS download, as recorded below.

## Issues diagnosed and resolved

- First SDK Spring context applied early V18 before the empty-MD contract correction. Never repair/drop it: retain `d9_community_tests_20261003` as prior isolated evidence, use fresh `d9_community_storage_tests_20261003`. No daily schema or applied V1–V17 modified.
- Child parser stdout contained Commons Logging diagnostics; process outcome now uses controlled exit0/2/3, with stdout/stderr discarded. Worker has real heap/direct/metaspace budget and forced termination + wait; no false Future cancellation claim. DB/model/storage/Root/AWS credential env removed before spawn.
- First full run exhausted PostgreSQL100 connections from retained Spring contexts × Hikari10. Only Surefire cache limit8 added; production settings/pool concurrency unchanged. Entire suite then PASS.
- RustFS1.0 missing named policy info API returns500/InternalError. Initialization now uses authoritative list-canned-policies membership before create, and only reuses equal canonicalpolicy. It does not classify arbitrary500 as absence.
- Application and WSL DB clocks differ slightly; deadline failure tests set explicit application-clock timestamp instead of assuming `clock_timestamp()-1s` is earlier than Java's clock. Actual deadlines are created and checked in the same JVM clock domain.

## Pending / limits

- Initial default Docker build twice terminated during frontend `npm ci`: ECONNRESET152 then ETIMEDOUT146; backend build cancelled by joint build. Resolved through a temporary host-build-network overlay with official Dockerfiles/versions/TLS unchanged, no daemon or daily-service changes. Subsequent actual proxy/runtime/crash checks **PASS** below.
- Unexpected real JVM KILL/restart/recover now **PASS** below, with explicit past-deadline injection disclosed (no production timeout policy change).
- Normal9000 private RustFS/new application identity/.env append and prefix-SHA evidence supplied by parent runtime worker `.local-runtime/community-oss-delivery-day-state.json`; native daily app startup/measurement is parent-owned and separately assessed.
- File validation scope: JPEG/PNG actual sampled decode (PNG complete chunkCRC); all WebP RIFF/chunk/frame/encodingheaders checked. Small/safe WebP frames decode with TwelveMonkeys3.15.2. High-compression/full-raster branch preserves legal source images through explicitly limited structural validation; it does not prove every compressed bitstream. PDFBox strict file-backed xref/catalog/page-tree for unencryptedPDF; encryptedPDF outer xref/trailer/Root/Encrypt/offsets only, no password/private-content proof or virus certification. Resource exhaustion is503 rather than inventing a format/pixel quota.
- Aliyun adapter/API/resources/copy tools are **DEFERRED by approved scope**, no SDK/API call or cloud acceptance claim.

## Final backend gate (supersedes intermediate package)

After parser-scratch lifecycle, consistent RR reads and preserved FAILED upload receipts were added:

- `-Dtest=AttachmentValidationTest,AttachmentConcurrencyIntegrationTest,AttachmentReadConsistencyIntegrationTest test`: exit0, **18 PASS / 0 failure / 0 error / 0 skipped**, 2026-10-03 05:12 Asia/Shanghai.
- `clean verify`: exit0, **223 PASS / 0 failure / 0 error / 0 skipped**, 05:14:30, all latest source included and actual Boot jar repackaged. `.local-runtime/community-storage-final-verify.log` is the final backend gate; previous220 is intermediate evidence only.
- RR concurrent test uses real SqlSession/MyBatis/PG query then a deterministic barrier, publishes F2 while F1 body has been read, and proves one coherent F1 body+attachment response; next request returns coherent F2. Initial Mockito dynamic-proxy `callRealMethod` sync-point error fixed before declaring PASS.
- Original FAILED upload replay remains its original code/resultVersion after physical cleanup. DELETE_FAILED DTO retains a current safe storage-failure message while DB retains the original request receipt.
- Real PublicKey-encrypted `Adobe.PubSec` + compressed `ObjStm` PDF fixture1581bytes is accepted without a certificate/private key; truncated counterpart rejected. SHA256 `d2367e3d48aabaabbf097e678fcd52d5629b99ef06be7ff40fe299412403d7aa`; generation uses available PDFBox3.0.8+BC1.76 tooling only, no added production crypto dependency or persisted private key.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/check-safety.ps1`: exit0, process identity matching/mismatch rejection and all PS script parse checks PASS.

Parent runtime worker repaired the isolated Docker build network through an ignored `build.network:host` overlay (official Dockerfiles/versions/HTTPS unchanged), both builds exit0. It verified no d10 resources existed and explicitly handed project ownership back.

## Final container / proxy / real crash gate

- Rebuilt official backend Dockerfile from current complete backend source/pom/Dockerfile, all source SHA entries recorded before and after build; unchanged aggregate `958199ef3cb13af6345332dce748df8c72cd2fb494dc1b17f7fdb9330374e2b0`, embedded image label same. Build exit0; final backend image `sha256:4de0448e3b1cd28ed082c81628faa4b35589ce0ac8da2093badb0f728004c4de`. Source evidence `.local-runtime/community-storage-docker-current-source.json`; host compiler's latest223 JAR SHA `63b7df371897bd1cede8bc1c11169655f914f4854c70cff5ef2cac55b0d0c74a`.
- Initial image class audit showed3 bytecode SHA differences solely around explicit String.valueOf(Object) vs typed invokedynamic concat due different javac patch optimizations; current-source rebuild and immutable before/after digest removes snapshot ambiguity. Identical critical Row/Service/Community RR/XML/V18/application bytes also retained in audit evidence.
- `wsl.exe -d Ubuntu --cd /mnt/e/projects/workbench -- python3 scripts/local/verify-compose.py --skip-build`: latest-source image run exit0, `.local-runtime/community-storage-compose-latest-source.log`.
- `... verify-compose.py --skip-build --browser-node /mnt/d/APPS/node/node.exe`: **exit0**, `.local-runtime/community-storage-compose-browser-final.log`. This is the final infrastructure gate and repeats proxy+real-crash on the exact current-source image.
- Fresh isolated `d10deployvalidation`, PostgreSQL15439/Redis16389/RustFS19009+19010/ingress18088, private bucket `d10deployvalidation-community`; all5 healthy and application IAM private/read/write/delete/management-denied verified.
- True Nginx→packaged application→worker→RustFS: two20MiBPDF and two5MiBPNG accepted exactly50MiB; each original SHA/length downloaded; per-file+1 returns413 and collection+1 returns409, body/version unchanged by rejection.
- Real crash synchronization: pause only owned RustFS, actual multipart upload, observe persisted UPLOADING, `docker kill --signal KILL backend`, unpause, recreate new JVM. Original request cannot claim201 READY; row survives. Explicitly inject past deadline, author recover returns version2 FAILED, reservation removed and body preserved, cleanup removes orphan while retaining DELETED tombstone/key.
- Same-browser backend restart, STOMP reconnect, HTTP fallback, private browser storage, account lifecycle/unchanged bootstrap/session/data persistence and credentials-not-in-responses/logs all PASS; Flyway18.
- Reviewer caught Docker browser-node inheriting backend/storage env. `browser_environment` now strips storage/root/AWS/DB/AI/REPORT and restores only required synthetic login/input. Real standalone Node counter and actual guarded compose-restart Node process both report **forbiddenCount0 / syntheticLoginAndInputPresent:true**. `scripts/local/browser-env-check.mjs` validates before importing the existing browser script. No frontend source changed by this agent.
- Every invocation refuses preexisting project containers/volumes/networks, only its newly owned project is cleaned. Final explicit Docker label queries for d10 containers/volumes/networks all empty, all exit0. Day9000 and test19000 projects/volumes untouched.

All backend/infrastructure files are released to the independent checker; no Maven, browser server, Docker validation job, or d10 resource remains live under this agent. No commit/push/cloud call/deployment performed.
