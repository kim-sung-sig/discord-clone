# T142 Admin CLI Smoke Database Isolation Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T142 Admin CLI Smoke Database Isolation

## Design

The smoke script treats an omitted `-SmokeUserId` as an owned temporary fixture:

| Step | Behavior |
| --- | --- |
| Fixture ID | Generate `New-SmokeUserId` with `[guid]::NewGuid()`. |
| Fixture labels | Derive username and email from a sanitized UUID suffix. |
| Seed | Insert/update the generated smoke user and auth account before the CLI list command. |
| Cleanup | In `finally`, remove audit, role, session, auth, and user rows for the generated fixture. |
| Explicit ID | If `-SmokeUserId` is provided, keep the previous compatibility path and do not auto-delete the caller-provided user. |

Cleanup failure is not swallowed. If the generated fixture cannot be removed, the smoke exits non-zero so CI and operators see that the shared database may need manual cleanup.

## Contract Test

The contract now requires:

- UUID generation.
- Unique `admin-cli-bootrun-smoke-...` email generation.
- `finally` cleanup.
- Explicit deletes for smoke-owned role, audit, auth, and user rows.
- No old deterministic UUID or static smoke email.
- No `$isWindows =` assignment, because PowerShell Core exposes `$IsWindows` as read-only.

## Security Review

- No database password is added to command arguments.
- The generated fixture is random by default and is not a reusable shared account.
- Cleanup deletes only the generated UUID/email pair in the default path.
- The explicit `-SmokeUserId` path does not auto-delete user-provided IDs, avoiding accidental destructive cleanup of real accounts.
