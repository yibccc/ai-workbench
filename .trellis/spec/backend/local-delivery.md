# Local Windows Delivery

## 1. Scope

Use these contracts for `scripts/local` operational scripts and local delivery documentation. The supported environment is a single Windows user, loopback application endpoints, and local Compose PostgreSQL/Redis (Docker CLI on Windows or WSL Ubuntu).

## 2. Commands

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/start.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/stop.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/check-safety.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/backup.ps1`
- Backup plus isolated restore: add `-RestoreTo d10_restore_<unique_suffix>`.
- Restore an existing dump: add both `-RestoreTo` and `-ArchivePath <existing_file>`.

## 3. Contracts

- Start uses the built backend JAR and installed Vite, resolves the actual JVM executable, and launches hidden processes on loopback ports 8080/5173.
- Persist PID, creation time, full command and repository root under ignored `.local-runtime`; stop/reuse only a matching identity. Refuse unrelated port occupants.
- `.env` values are injected into the backend process without logging them. Do not pass database/model credentials to the frontend process. Restore the caller environment after process creation.
- Stop application processes without deleting database containers or volumes. Restart verification must use a new JVM and read existing stored data.
- Use PostgreSQL custom archives through `pg_dump` and `pg_restore`; store sensitive dumps under ignored `.local-backups` and validate readability/checksum.
- Restore only into a new, explicitly named isolated database. Reject the current database and every existing destination; never overwrite/drop the working database as a restore convenience.
- Check native command exit codes. Clean up only exact per-run temporary filenames. Cleanup failure must not mask the original backup/restore error.
- Verify restored Flyway metadata and business content, not only that pg_restore exited successfully. Distinguish public business rows from test schemas.

## 4. Errors

| Condition | Required behavior |
|---|---|
| Missing build/dependencies/.env | Fail with actionable setup instruction |
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
- Unmanaged-port rejection, repeated start/stop, unset JAVA_HOME, and new-JVM persistence.
- Fresh restore and existing-archive restore; existing-destination rejection.
- Table counts/content digests and Flyway version; exact cleanup of created verification databases.
- User-operated README restart and sustained-use measurements remain separate acceptance observations.

## 7. Wrong vs Correct

Wrong: `Stop-Process -Name java` or a restore that drops the original database.

Correct: compare recorded PID identity before stopping, and restore only to a verified absent `d10_restore_*` database.
