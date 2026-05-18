# T39 Backup, Restore & Migration Drill Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T39 Backup, Restore & Migration Drill

## Architecture Decision

Use a local/CI-safe drill harness that operates on explicit development databases only. The harness should never infer or touch production database URLs. It should create evidence artifacts, restore into a separate target, and run API smoke against the restored state.

## Drill Flow

```text
seed source database
  -> validate Flyway state
  -> pg_dump source
  -> create clean restore database/schema
  -> pg_restore or psql restore
  -> start backend against restored database
  -> run qa/api-smoke.ps1
  -> write drill summary artifact
```

## Script Boundary

Proposed scripts:

| Script | Purpose |
| --- | --- |
| `qa/db-backup.ps1` | Dump explicit source DB to `qa/artifacts/db-drill` |
| `qa/db-restore.ps1` | Restore dump into explicit target DB |
| `qa/migration-drill.ps1` | Orchestrate seed, backup, restore, Flyway validate, API smoke |
| `qa/migration-guard.contract.ps1` | Static guard for destructive migration patterns |

Every script must require explicit JDBC or PostgreSQL connection parameters for source and target. No script should default to a production-like host.

## Safety Controls

- Refuse URLs containing `production`, `prod`, or non-local hosts unless an explicit `-AllowNonLocal` flag is passed.
- Refuse restore when target equals source.
- Refuse restore without a confirmation flag for non-empty target schemas.
- Write connection metadata with passwords redacted.
- Store artifacts under `qa/artifacts/db-drill/{timestamp}`.

## Migration Guardrail

Detect and require review for Flyway scripts containing high-risk operations:

- `DROP TABLE`
- `DROP COLUMN`
- `TRUNCATE`
- type narrowing or column rename without compatibility notes
- `DELETE FROM` without a bounded predicate

The guard does not replace human migration review, but it prevents destructive changes from being invisible.

## Restore Smoke

Minimum restored-state checks:

- login works for seeded user,
- guild list contains seeded guild,
- channel list contains seeded channel,
- message pagination returns seeded message,
- invite lookup/accept path remains consistent when seeded.

When real seeded data is unavailable, the drill should create seed data through public APIs before backup.

## Artifact and Retention Policy

Artifacts:

- dump file,
- restore log,
- Flyway validation log,
- API smoke log,
- redacted summary markdown.

Default retention:

- local: keep latest 5 drill directories,
- CI: upload artifacts for the run and rely on CI retention,
- never commit dump files.

## QA Strategy

- Contract tests for script safety checks.
- Local drill against disposable PostgreSQL database.
- Negative test proving source and target cannot be identical.
- API smoke after restore.

## Risks

- Windows and Linux PostgreSQL CLI availability may differ; scripts should fail with clear missing-tool guidance.
- Large dumps are out of scope; the first drill validates correctness, not throughput.
- Flyway rollback is not automatic; recovery path is restore-forward rather than down migration.
