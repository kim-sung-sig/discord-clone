# T39 Backup, Restore & Migration Drill Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T39 Backup, Restore & Migration Drill

## Summary

T39 added a local/CI-safe database recovery drill for the PostgreSQL profile. The drill seeds source data through public APIs, backs up the source database, restores into a separate clean target database, starts the backend against the restored target, and runs API smoke to verify recovered application behavior.

## Delivered

- Added `qa/db-drill-common.ps1` shared safety and command helpers.
- Added `qa/db-backup.ps1` for explicit source database dumps.
- Added `qa/db-restore.ps1` for clean target restore with source/target safety guard.
- Added `qa/migration-drill.ps1` orchestration script.
- Added `qa/migration-guard.contract.ps1` destructive migration scanner.
- Added `qa/migration-drill.contract.ps1` script contract test.
- Added Docker-container PostgreSQL CLI fallback via `-PostgresCliContainer`.
- Produced a successful restore drill artifact.

## Verification

- `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.contract.ps1`: PASS
- `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-guard.contract.ps1`: PASS
- identical source/target restore rejection: PASS
- `qa/migration-drill.ps1 ... -PostgresCliContainer 'postgres-source' ...`: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS

Successful artifact:

```text
qa/artifacts/db-drill/20260517-151537/restore-drill-summary.md
```

## Coverage

- local JDBC URL safety checks
- production-like URL refusal
- source/target equality refusal
- destructive migration static guard
- source API smoke seed
- Docker-backed `pg_dump`
- clean restore target schema
- Docker-backed `pg_restore`
- restored backend startup through `postgres` profile
- restored API smoke
- artifact retention policy

## Operator Procedure

1. Ensure the source and target PostgreSQL databases exist and are disposable development/CI databases.
2. Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.ps1 `
  -SourceJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' `
  -TargetJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord_restore' `
  -PostgresCliContainer 'postgres-source' `
  -BackendUrl 'http://127.0.0.1:18080'
```

3. Check `qa/artifacts/db-drill/{timestamp}/restore-drill-summary.md`.
4. Keep latest 5 local drill directories; never commit dump files.

## Residual Risks

- No production PITR or cloud-managed backup automation.
- No large-volume restore timing benchmark.
- No automatic target database creation yet.
- Future improvement should compare restored pre-existing data hashes, not only run API smoke after restore.

## Next Recommended Task

Proceed to T40 Cross-node Gateway Fanout. T39 leaves the persistence recovery path rehearsed enough to continue distributed realtime work with less state-loss risk.
