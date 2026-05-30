# T157 Central Redis CI Failure Artifacts Report

Date: 2026-05-20
Slice: T157 Central Redis CI Failure Artifacts

## Completed

- Extended the CI workflow contract with Redis artifact requirements.
- Added `qa/central-redis-ci-artifacts.ps1`.
- Updated `qa/central-redis-smoke.ps1` to support `CENTRAL_REDIS_ARTIFACT_DIR`.
- Updated the Redis smoke to emit `vitest-junit.xml`.
- Added failure artifact collection and upload steps to `qa-central-redis`.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because Redis artifact wiring was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-ci-artifacts.ps1` passed and emitted `CENTRAL_REDIS_CI_ARTIFACTS_COLLECTED`.
  - `$env:CENTRAL_REDIS_ARTIFACT_DIR='qa/artifacts/central-redis/ci'; powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.ps1` passed and wrote `vitest-junit.xml`.

## Notes

- The local verification used the same relative artifact directory style as CI to confirm path normalization.
