---
slug: T142-admin-cli-smoke-database-isolation
ticket: T142
phase: analysis
hub: "[[T142-admin-cli-smoke-database-isolation]]"
---
> 🧭 [[T142-admin-cli-smoke-database-isolation]] · PDCA: [[T142-admin-cli-smoke-database-isolation.plan]] → [[T142-admin-cli-smoke-database-isolation.design]] → **analysis** → [[T142-admin-cli-smoke-database-isolation.report]] → [[T142-admin-cli-smoke-database-isolation.feedback]]

# T142 Admin CLI Smoke Database Isolation Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T142 Admin CLI Smoke Database Isolation

## Findings

- `qa/admin-cli-bootrun-smoke.ps1` used a fixed UUID and fixed email by default.
- The seed SQL used `ON CONFLICT DO UPDATE`, which made repeated runs stable but also allowed shared-state mutation.
- The script only exercised `list`, so generated fixture cleanup can safely remove the smoke user after verification.
- `pwsh` execution failed because `$isWindows` conflicts with PowerShell Core's read-only `$IsWindows` variable.

## Risk Review

| Risk | Control |
| --- | --- |
| Shared database fixture pollution | Generate a unique UUID by default and delete owned rows in `finally`. |
| Cleanup silently failing | Let cleanup failure fail the script. |
| Accidental deletion of caller-provided users | Only cleanup when the script generated the user ID. |
| CI uses `pwsh` while local runs use Windows PowerShell | Add a contract guard against `$isWindows =` and verify both shells. |

## Remaining Gaps

- The explicit `-SmokeUserId` compatibility path can still mutate the caller-provided user fixture. A separate non-mutating custom-user mode should be considered before using custom IDs against shared databases.
