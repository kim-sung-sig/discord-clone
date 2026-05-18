# T39 Backup, Restore & Migration Drill Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T39 Backup, Restore & Migration Drill

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.contract.ps1` | PASS | Script presence, parse, safety snippets, restore guard, and orchestration contract passed. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-guard.contract.ps1` | PASS | Current Flyway SQL migrations have no unreviewed destructive patterns. |
| identical source/target restore negative check | PASS | `qa/db-restore.ps1` rejected identical source and target JDBC URLs. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.ps1 -SourceJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' -TargetJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord_restore' -PostgresCliContainer 'postgres-source' -BackendUrl 'http://127.0.0.1:18080' -BackendStartupTimeoutSeconds 180` | PASS | Source API smoke seed, Docker-backed `pg_dump`, clean target restore, and restored API smoke passed. |
| `.\\gradlew.bat :backend:boot:test --no-daemon` | PASS | Backend boot regression tests passed. |
| `git diff --check -- qa/...` | PASS | No whitespace errors in T39 script files. |

## Drill Artifact

Latest successful drill artifact:

```text
qa/artifacts/db-drill/20260517-151537/restore-drill-summary.md
```

Summary highlights:

- source: `jdbc:postgresql://127.0.0.1:15432/discord`
- target: `jdbc:postgresql://127.0.0.1:15432/discord_restore`
- dump: `qa/artifacts/db-drill/20260517-151537/20260517-151553/source.dump`
- source seed smoke: PASS
- restored API smoke: PASS
- retention: keep latest 5 local drill directories; never commit dump files

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Seeded database can be backed up and restored into clean target | PASS | Source API smoke seeded state before backup; restore cleaned `public` schema and loaded dump into `discord_restore`. |
| Restored database passes auth/guild/channel/message/invite API smoke | PASS | `api-smoke.log` contains `API_SMOKE_PASS` after restore. |
| Flyway migration validation is part of drill | PASS | Backend startup against source and restored target runs Flyway through the `postgres` profile. |
| Destructive migration patterns detected or reviewed | PASS | `migration-guard.contract.ps1` scans migration SQL and fails on unreviewed destructive patterns. |
| Backup artifacts have retention/location/cleanup rules | PASS | Drill summary records retention, and script prunes to latest 5 artifact directories. |
| Analysis/report records command/result/residual risks | PASS | This document and T39 report record command, result, and residual risks. |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| Initial drill failed with PowerShell `Host` read-only variable conflict | JDBC parser used `$host`, which conflicts with PowerShell's built-in `$Host` variable | Renamed local variable to `$dbHost`. |
| Initial drill health wait timed out | Redis health indicator returned 503 during postgres-only recovery drill | Drill backend process sets `MANAGEMENT_HEALTH_REDIS_ENABLED=false`. |
| First failed run left a bootRun Java child process listening on 8080 | Stop logic killed the wrapper process but not the Java child in that failed attempt | Process was manually stopped; successful run used explicit port 18080 and completed cleanup. |
| Host lacked PostgreSQL client tools | Current environment has PostgreSQL CLI inside Docker container, not on host PATH | Added optional `-PostgresCliContainer` path using Docker `pg_dump`, `pg_restore`, and `psql`. |

## Implementation Notes

- `qa/db-backup.ps1` creates a custom-format dump and redacted metadata.
- `qa/db-restore.ps1` refuses identical source/target URLs and requires `-ConfirmCleanTarget`.
- `qa/migration-drill.ps1` runs source seed API smoke before backup and restored API smoke after restore.
- Scripts refuse production-like database URLs and non-local hosts unless `-AllowNonLocal` is explicit.
- `qa/migration-guard.contract.ps1` requires `T39-DESTRUCTIVE-REVIEWED` for destructive migration patterns.

## Residual Risks

- The drill validates local Docker/PostgreSQL correctness, not production PITR or managed backup configuration.
- API smoke creates additional records after restore; future drills should add snapshot row-count/hash comparisons for restored pre-existing records.
- Java child cleanup is improved for successful runs, but a more robust process-tree cleanup helper would reduce risk after startup failures.
- Target database creation is still an operator prerequisite; the drill cleans schema but does not create the database.
