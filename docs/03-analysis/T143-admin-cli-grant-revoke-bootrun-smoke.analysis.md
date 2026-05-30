# T143 Admin CLI Grant/Revoke BootRun Smoke Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Findings

- `GlobalAdminRoleCommandRunner` already covers `grant`, `revoke`, and `list` at unit level.
- The existing bootRun smoke covered only `list`, so Spring profile wiring, datasource wiring, and Flyway-backed mutation paths were not checked end-to-end.
- Audit rows are written by `JdbcAuthStore.recordGlobalRoleAudit` with uppercase enum names, so SQL verification can assert `GRANT/APPLIED` and `REVOKE/APPLIED`.
- The T142 generated fixture cleanup is sufficient for grant/revoke because it deletes role and audit rows before deleting the smoke user.

## Risk Review

| Risk | Control |
| --- | --- |
| Mutating smoke leaves admin role behind | Verify revoke state and run cleanup in `finally`. |
| Audit path regresses silently | Assert audit counts for grant and revoke. |
| Large combined log hides failing phase | Write one log per bootRun phase. |
| Secret exposure in artifacts | Use actor and aggregate SQL checks only; keep DB password in env variables. |

## Remaining Gaps

- Duplicate grant/revoke `NOOP` audit results are unit-tested but not yet covered through the real bootRun smoke.
