# T142 Admin CLI Smoke Database Isolation Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T142 Admin CLI Smoke Database Isolation

## Executive Summary

| View | Content |
| --- | --- |
| Problem | The admin CLI bootRun smoke used deterministic user fixtures and could leave or overwrite rows in the shared central development database. |
| Solution | Generate a unique smoke user by default and clean up the rows owned by that generated fixture in a `finally` block. |
| Operator Effect | Operators can run the central Postgres smoke repeatedly without dirtying shared database state. |
| Core Value | The smoke keeps real central-database coverage while reducing shared-state risk. |

## Scope

- Replace the default deterministic smoke user ID with a generated UUID.
- Generate smoke username/email values from that UUID.
- Delete generated user, auth, role, session, and audit rows after the smoke.
- Make cleanup failure fail the smoke so shared-state contamination is visible.
- Preserve explicit `-SmokeUserId` compatibility without automatically deleting caller-provided users.
- Strengthen the contract test for isolation and `pwsh` compatibility.

## Out of Scope

- Adding mutating grant/revoke bootRun smoke coverage.
- Replacing the shared central database with per-run databases.
- Archiving or cleaning historical deterministic smoke rows already present in developer databases.

## Success Criteria

- Contract test fails before implementation and passes after implementation.
- Default smoke run passes through the real bootRun path.
- Generated fixture rows are absent from central Postgres after the smoke exits.
- Both Windows PowerShell and PowerShell Core contract paths pass.
