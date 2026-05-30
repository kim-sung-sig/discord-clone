# T148 Central Redis Smoke Check Report

Date: 2026-05-20
Slice: T148 Central Redis Smoke Check

## Completed

- Added a central Redis smoke contract.
- Added a backend Redis connectivity smoke test.
- Added a web CSP rate limiter central Redis smoke test.
- Added a QA script that reuses existing central `ms-redis` or starts Compose `ms-redis`.
- Forced the backend smoke path to rerun through Gradle for fresh evidence.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.contract.ps1` failed because the smoke script was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.contract.ps1` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.CentralRedisConnectivitySmokeTest` passed with the env gate disabled.
  - `npm test -w apps/web -- csp-report-rate-limiter.central-redis.test.ts` skipped with the env gate disabled.
  - `powershell -ExecutionPolicy Bypass -File qa/central-redis-smoke.ps1` passed with backend Gradle tasks executed and web Vitest reporting 1 passed test.

## Notes

- The first central smoke run exposed an environment conflict: an existing `ms-redis` container already owned port `16379`.
- The smoke script now treats a healthy existing `ms-redis` as the central Redis resource instead of trying to create a duplicate container.
