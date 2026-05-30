# T159 Compose Health Diagnostic Failure Smoke Report

Date: 2026-05-20
Slice: T159 Compose Health Diagnostic Failure Smoke

## Completed

- Added `CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE` forced diagnostic mode to `qa/central-compose-health.ps1`.
- Added `qa/central-compose-health-diagnostics-smoke.ps1`.
- Added `qa/central-compose-health-diagnostics-smoke.contract.ps1`.
- Updated `qa/central-compose-health.contract.ps1`.
- Updated `qa/ci-workflow.contract.ps1`.
- Added the diagnostic smoke contract to `.github/workflows/ci.yml`.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health-diagnostics-smoke.ps1` failed because partial output from the failing child process was not retained.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because CI did not reference the diagnostic smoke contract.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health-diagnostics-smoke.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health-diagnostics-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.ps1` passed and emitted `CENTRAL_COMPOSE_HEALTH_PASS`.

## Notes

- The diagnostic smoke does not mutate central Compose resources.
- The existing Redis CLI command-line password warning was observed during normal health verification and is tracked as follow-up.
