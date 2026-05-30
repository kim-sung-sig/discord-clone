# T122 Admin Role Runbook Report

Date: 2026-05-20
Slice: T122 Admin Role Runbook

## Completed

- Added `docs/runbooks/global-admin-role-runbook.md`.
- Added `qa/admin-role-runbook.contract.ps1`.
- Documented pre-check, user lookup, grant, verify, audit review, revoke, rollback, and failure handling.
- Documented secret handling: database passwords stay in environment variables and out of Gradle `--args`.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/admin-role-runbook.contract.ps1` failed because `docs/runbooks/global-admin-role-runbook.md` did not exist.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/admin-role-runbook.contract.ps1` passed.

## Notes

- The runbook references `qa/admin-cli-bootrun-smoke.ps1` as the pre-change smoke check.
