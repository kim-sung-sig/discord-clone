# T55 Restore Snapshot Hash Comparison Report

Date: 2026-05-21
Status: Completed

## Result

The PostgreSQL migration drill now proves restored pre-existing data, not only post-restore API behavior. It records source and restored table row-count/hash snapshots and fails the drill if the restored target differs before running API smoke.

## Files Changed

- `qa/db-drill-common.ps1`
- `qa/migration-drill.ps1`
- `qa/migration-drill.contract.ps1`

## Verification

- `powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.contract.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.ps1 -SourceJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' -TargetJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord_restore' -PostgresCliContainer 'postgres-source' -BackendUrl 'http://127.0.0.1:18080' -BackendStartupTimeoutSeconds 180`

Both passed.

## Output Artifacts

- `source-snapshot-hashes.tsv`
- `restored-snapshot-hashes.tsv`
- `snapshot-hash-comparison.txt`
- `restore-drill-summary.md`

These are runtime artifacts under `qa/artifacts/db-drill` and should not be committed.

## Wiki Updated

- `wiki/QA Infra Operations.md`
- `wiki/Current Roadmap And Risks.md`
- `log.md`
