# T55 Restore Snapshot Hash Comparison Analysis

Date: 2026-05-21
Status: Completed

## RED

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.contract.ps1
# FAIL: migration-drill.ps1 is missing required snippet: Write-DatabaseSnapshotHash
```

## GREEN

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.contract.ps1
# PASS: MIGRATION_DRILL_CONTRACT_PASS
```

## Real Drill

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.ps1 `
  -SourceJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' `
  -TargetJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord_restore' `
  -PostgresCliContainer 'postgres-source' `
  -BackendUrl 'http://127.0.0.1:18080' `
  -BackendStartupTimeoutSeconds 180
# PASS: RESTORE_DRILL_PASS
```

Evidence artifact:

- `qa/artifacts/db-drill/20260521-201824/restore-drill-summary.md`

## Security Review

- No row values are written to the summary or hash artifacts.
- Hash comparison runs before restored API smoke so post-restore smoke mutations cannot mask restore mismatch.
- The run again exposed the known T57 process-tree cleanup gap; the leftover backend child on port `18080` was manually stopped after verification.
