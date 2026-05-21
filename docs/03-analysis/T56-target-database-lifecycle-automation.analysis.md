# T56 Target Database Lifecycle Automation Analysis

Date: 2026-05-21
Status: Completed

## RED

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.contract.ps1
# FAIL: db-restore.ps1 is missing required snippet: [switch] $EnsureTargetDatabase
```

## GREEN

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.contract.ps1
# PASS: MIGRATION_DRILL_CONTRACT_PASS
```

## Real Drill

The verification removed local test DB `discord_restore_t56`, then ran:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.ps1 `
  -SourceJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' `
  -TargetJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord_restore_t56' `
  -PostgresCliContainer 'postgres-source' `
  -BackendUrl 'http://127.0.0.1:18080' `
  -BackendStartupTimeoutSeconds 180
# PASS: RESTORE_DRILL_PASS
```

Evidence artifact:

- `qa/artifacts/db-drill/20260521-203552/restore-drill-summary.md`

The drill log included `created restore target database: jdbc:postgresql://127.0.0.1:15432/discord_restore_t56`, then passed snapshot hash comparison and API smoke.

## Cleanup

- The backend child process on port `18080` was stopped after verification.
- The local verification database `discord_restore_t56` was dropped after the drill passed.
