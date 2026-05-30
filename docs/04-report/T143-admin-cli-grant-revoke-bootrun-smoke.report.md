# T143 Admin CLI Grant/Revoke BootRun Smoke Report

Date: 2026-05-21
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Completed

- Extended `qa/admin-cli-bootrun-smoke.ps1` to run `grant`, list-after-grant, `revoke`, and list-after-revoke.
- Added SQL assertions for role state after grant and revoke.
- Added SQL assertions for `GRANT/APPLIED` and `REVOKE/APPLIED` audit rows with actor `admin-cli-bootrun-smoke`.
- Added separate bootRun artifact logs for each mutation phase.
- Strengthened `qa/admin-cli-bootrun-smoke.contract.ps1` to require the grant/revoke smoke contract.

## Verification

- RED observed first:
  - `pwsh qa/admin-cli-bootrun-smoke.contract.ps1` failed because `bootrun-grant.log` and the grant/revoke contract snippets were missing.
- GREEN after implementation:
  - `pwsh qa/admin-cli-bootrun-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.contract.ps1` passed.
  - `pwsh qa/admin-cli-bootrun-smoke.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.ps1` passed.
  - Postgres cleanup verification returned `0` rows for the generated smoke user across `users`, `auth_accounts`, `user_global_roles`, and `user_global_role_audit_log`.
  - `git diff --check` passed with CRLF warnings only.

## Notes

- Runtime increased because the smoke now launches Spring Boot five times through Gradle `bootRun`.
