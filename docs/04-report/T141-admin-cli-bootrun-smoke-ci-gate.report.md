# T141 Admin CLI BootRun Smoke CI Gate Report

Date: 2026-05-20
Slice: T141 Admin CLI BootRun Smoke CI Gate

## Completed

- Added `qa-admin-cli` to `.github/workflows/ci.yml`.
- Updated `qa/ci-workflow.contract.ps1`.
- Strengthened `qa/admin-cli-bootrun-smoke.contract.ps1`.
- Made `qa/admin-cli-bootrun-smoke.ps1` cross-platform.
- Added Compose-based `postgres-source` startup/reuse.
- Added fresh database migration warmup before seeding.
- Added admin CLI bootRun artifact logs.
- Moved datasource credentials from `bootRun --args` to environment variables.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/admin-cli-bootrun-smoke.contract.ps1` failed because the smoke lacked cross-platform/Compose/artifact requirements.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because `qa-admin-cli` was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/admin-cli-bootrun-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/admin-cli-bootrun-smoke.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/admin-cli-bootrun-smoke.ps1 -Database discord_admin_cli_t141_fresh_smoke -SmokeUserId 00000000-0000-0000-0000-000000001143` passed after creating and migrating a fresh database.

## Notes

- The fresh verification database was dropped after the smoke.
- The CI job uploads `qa/artifacts/admin-cli` for bootRun log inspection.
