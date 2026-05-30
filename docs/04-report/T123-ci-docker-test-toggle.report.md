# T123 CI Docker Test Toggle Report

Date: 2026-05-20
Slice: T123 CI Docker Test Toggle

## Completed

- Added `qa/web-docker-tests.contract.ps1`.
- Added `qa-web-docker` to `.github/workflows/ci.yml`.
- Updated `qa/ci-workflow.contract.ps1`.
- The CI job installs web dependencies, checks Docker availability, and runs the Redis limiter integration test with `NUXT_RUN_DOCKER_TESTS=true`.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/web-docker-tests.contract.ps1` failed because CI did not contain `qa-web-docker`.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/web-docker-tests.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `docker version` passed.
  - `NUXT_RUN_DOCKER_TESTS=true npm test --workspace @discord-clone/web -- csp-report-rate-limiter.redis.test.ts` passed with 1 test.

## Notes

- The default frontend job still runs without Docker-enabled tests.
- The Docker-backed test remains scoped to the CSP Redis limiter integration case from T116.
