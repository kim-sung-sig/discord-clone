# T154 Central Redis Smoke CI Gate Report

Date: 2026-05-20
Slice: T154 Central Redis Smoke CI Gate

## Completed

- Added CI workflow contract requirements for the Redis smoke job.
- Added `.github/workflows/ci.yml` job `qa-central-redis`.
- Made `qa/central-redis-smoke.ps1` portable across Windows and Linux Gradle wrappers.
- Added native command exit-code enforcement inside the Redis smoke script.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because `qa-central-redis` was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.ps1` passed with backend Gradle tasks executed and web Vitest reporting 1 passed test.

## Notes

- The CI job runs the Redis smoke script instead of duplicating its commands in YAML.
