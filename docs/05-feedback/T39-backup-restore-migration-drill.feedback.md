# T39 Backup, Restore & Migration Drill Feedback

작성일: 2026-05-17  
PDCA Phase: Act  
Slice: T39 Backup, Restore & Migration Drill

## Decisions

- Use explicit source and target JDBC URLs; never infer production or default restore targets.
- Require separate target database and reject identical source/target URLs.
- Keep restore strategy as restore-forward, not Flyway down migration.
- Support Docker-hosted PostgreSQL CLI because the current Windows host does not expose `pg_dump`/`pg_restore` on PATH.
- Keep local retention to latest 5 drill directories and avoid committing dump files.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| PostgreSQL client tools were not available on host PATH. | Added `-PostgresCliContainer` support for Docker `pg_dump`, `pg_restore`, and `psql`. |
| Restore needed a destructive target cleanup guard. | Added `-ConfirmCleanTarget` and identical source/target rejection. |
| Migration review needed an automated tripwire. | Added destructive SQL pattern scanner with explicit review marker. |
| Redis health made postgres-only recovery startup look unhealthy. | Drill backend disables Redis health indicator with `MANAGEMENT_HEALTH_REDIS_ENABLED=false`. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T55 restore snapshot hash comparison | P1 | Current drill seeds before backup and smokes after restore, but should also compare selected restored row counts/hashes. |
| T56 target database lifecycle automation | P2 | Operators still create the target database before the drill. |
| T57 process-tree cleanup helper for QA harnesses | P2 | Failed startup can leave Java child processes if wrapper cleanup is interrupted. |
| T58 production backup runbook | P1 | Local drill exists; production PITR/cloud backup runbook remains out of scope. |

## Verification

- `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.contract.ps1`: PASS
- `powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-guard.contract.ps1`: PASS
- identical source/target restore rejection: PASS
- `qa/migration-drill.ps1` Docker-backed local drill: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS
