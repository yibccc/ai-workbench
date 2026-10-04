# Local Windows Delivery

## 1. Scope

Use these contracts for `scripts/local` operational scripts and local delivery documentation. The supported environment is a single Windows user, loopback application endpoints, and local Compose PostgreSQL/Redis (Docker CLI on Windows or WSL Ubuntu).

## 2. Commands

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1 -EnvFile <explicit_ignored_file>` for isolated synthetic local verification; omitting the parameter retains `.env` behavior.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/stop.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/check-safety.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/backup.ps1`
- Backup plus isolated restore: add `-RestoreTo d10_restore_<unique_suffix>`.
- Restore an existing dump: add both `-RestoreTo` and `-ArchivePath <existing_file>`.
- PG plus private-object bundle uses `python -B scripts/local/interview-backup.py backup|restore|verify --help`; exact source/target/schema/writer/environment arguments are documented in `scripts/local/interview-backup.md`. `prepare-volumes` must precede creation of a new restore project's PG/RustFS containers; restore requires its fresh-volumes record.

## 3. Contracts

- Start uses the built backend JAR and installed Vite, resolves the actual JVM executable, and launches hidden processes on loopback ports 8080/5173.
- Private RustFS setup has separate root/application credentials, a new dependency volume and explicit private-bucket initialization; see [Private Attachments](./private-attachments.md). Native application endpoint is localhost9000, while container endpoint is rustfs9000.
- Persist PID, creation time, full command and repository root under ignored `.local-runtime`; stop/reuse only a matching identity. Refuse unrelated port occupants.
- Newly launched backend state also captures service/module/actual working directory, resolved envFile/SHA and the effective host/port/database/schema identity at launch. No password or key appears in state. An unprovable JVM/JSON configuration is captured as databaseIdentity=null plus a safe captureError; ordinary application startup continues, while backup/pause refuses unproven or older state rather than inventing proof. The existing ordinary stop command keeps its process-ownership contract.
- By default, `.env` values are injected into the backend process without logging them. `-EnvFile` changes only the source file for that invocation; it must exist, and a missing explicit file fails before launch. Do not pass database/model credentials to the frontend process. Restore the caller environment after process creation.
- Exclude RUSTFS management values from the native backend child. Exclude DB/model/report/storage/RUSTFS/AWS/bootstrap credentials from Vite, and exclude DB/model/storage/root credentials from the trusted file-validation process. Browser verification drivers receive only their explicitly required synthetic login, not storage/admin credentials. Never use expanded Compose output or command-line secret values as diagnostic evidence.
- For local auth smoke tests, use an ignored synthetic EnvFile with `DEEPSEEK_API_KEY` empty and a verified absent dedicated PostgreSQL schema; never replace or read values from the user's `.env` into output. Run the existing `stop.ps1` in `finally`, then verify 8080/5173 listeners and owned PID files are gone and the original `.env` hash is unchanged.
- Stop application processes without deleting database containers or volumes. Restart verification must use a new JVM and read existing stored data.
- Use PostgreSQL custom archives through `pg_dump` and `pg_restore`; store sensitive dumps under ignored `.local-backups` and validate readability/checksum.
- Restore only into a new, explicitly named isolated database. Reject the current database and every existing destination; never overwrite/drop the working database as a restore convenience.
- Check native command exit codes. Clean up only exact per-run temporary filenames. Cleanup failure must not mask the original backup/restore error.
- Bundle backup proves each designated writer belongs to the chosen source database/network before stopping it; require explicit exclusive writers and zero remaining source connections. Read exact current-resume/retained-community references from the dump-restored snapshot, stream key/bytes/size/SHA without ListBucket, and install complete last. Reject complete/incomplete coexistence. Restore only after full bundle verification into fresh-role volumes proven by the prepare record and new DB/bucket; preserve partial failure for diagnosis, never overwrite/restart automatically. Online deletion does not erase historical bundles.
- Verify restored Flyway metadata and business content, not only that pg_restore exited successfully. Distinguish public business rows from test schemas.

## 4. Errors

| Condition | Required behavior |
|---|---|
| Missing build/dependencies/.env | Fail with actionable setup instruction |
| Explicit `-EnvFile` is missing or blank | Reject before starting either process; default `.env` behavior remains unchanged |
| Occupied port without matching ownership | Refuse to stop or reuse it |
| Reused PID with different creation time/command/root | Refuse process operation |
| Existing restore destination | Reject before writing any restored data |
| Dump/restore failure | Return failure, preserve diagnostic cause, never report success |
| Cleanup also fails | Report separately and retain original failure |

## 5. Cases

- Good: archive the working database, restore into a fresh verification database, compare content and delete only that verification database.
- Base: repeated start/stop handles owned running/stopped processes without affecting other applications.
- Bad: kill all java/node processes, run compose down -v, restore into public in place, or put API keys in process arguments.

## 6. Verification

- Script parser checks and matching/mismatched process-identity tests.
- Unmanaged-port rejection, repeated start/stop, unset JAVA_HOME, and new-JVM persistence. Exercise both default `.env` parsing and an explicit synthetic `-EnvFile` without printing credential values.
- Through loopback Vite/JAR with a fresh test schema, verify application login/CSRF and a business API via the Vite proxy; stop both owned processes and check port/PID cleanup. This runtime smoke complements the isolated Docker/browser checks; it does not authorize touching the working database.
- Fresh restore and existing-archive restore; existing-destination rejection.
- Table counts/content digests and Flyway version; exact cleanup of created verification databases.
- User-operated README restart and sustained-use measurements remain separate acceptance observations.

## 7. Wrong vs Correct

Wrong: `Stop-Process -Name java` or a restore that drops the original database.

Correct: compare recorded PID identity before stopping, and restore only to a verified absent `d10_restore_*` database.
