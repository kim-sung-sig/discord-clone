# T142 Admin CLI Smoke Database Isolation Report

Date: 2026-05-21
Slice: T142 Admin CLI Smoke Database Isolation

## Completed

- Replaced the default deterministic smoke user with generated UUID fixtures.
- Derived smoke username/email values from the generated UUID.
- Added `finally` cleanup for generated user, auth, role, session, and audit rows.
- Made generated fixture cleanup failure fail the smoke instead of only warning.
- Strengthened the smoke contract to reject the old deterministic fixture.
- Fixed PowerShell Core compatibility by avoiding assignment to `$isWindows`.
- Updated the global admin role runbook with the default isolation behavior.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.contract.ps1` failed because `New-SmokeUserId` was missing.
  - `pwsh qa/admin-cli-bootrun-smoke.contract.ps1` later failed because the script still assigned `$isWindows =`.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.contract.ps1` passed.
  - `pwsh qa/admin-cli-bootrun-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.ps1` passed.
  - `pwsh qa/admin-cli-bootrun-smoke.ps1` passed.
  - Postgres cleanup verification returned `0` rows for the generated smoke user across `users`, `auth_accounts`, `user_global_roles`, and `user_global_role_audit_log`.

## Notes

- The smoke still uses the real central Postgres container and real Gradle `bootRun` path.
- Explicit `-SmokeUserId` remains available for compatibility, but the default path is now cleanup-safe.
