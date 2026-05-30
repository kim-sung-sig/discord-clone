# T139 Dashboard Guard Health Smoke Check Report

Date: 2026-05-20
Slice: T139 Dashboard Guard Health Smoke Check

## Completed

- Added `qa/dashboard-guard-health-smoke.ps1`.
- Added `qa/dashboard-guard-health-smoke.contract.ps1`.
- Added `qa-dashboard-guard-health` to CI.
- Updated the shared CI workflow contract.
- Verified both ready and fail-closed smoke paths.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/dashboard-guard-health-smoke.contract.ps1` failed because `qa/dashboard-guard-health-smoke.ps1` did not exist.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/dashboard-guard-health-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - With `NUXT_SECURITY_DASHBOARD_TOKEN=ci-dashboard-guard-token`, `powershell -ExecutionPolicy Bypass -File qa/dashboard-guard-health-smoke.ps1` passed and emitted `DASHBOARD_GUARD_HEALTH_SMOKE_PASS`.
  - With no `NUXT_SECURITY_DASHBOARD_TOKEN`, `powershell -ExecutionPolicy Bypass -File qa/dashboard-guard-health-smoke.ps1 -SkipBuild -Port 3026 -StartupTimeoutSeconds 20` failed as expected and the captured log contained `fail-closed`.

## Notes

- The smoke writes build, server, metadata, and guard-health artifacts under `qa/artifacts/dashboard-guard-health`.
