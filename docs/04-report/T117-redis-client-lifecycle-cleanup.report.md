# T117 Redis Client Lifecycle Cleanup Report

## Completed

- Added optional close support to CSP report rate limiters.
- Added close delegation from Redis limiter to the Redis client.
- Cleared the cached Node Redis client promise after close.
- Added a Nitro close hook plugin for the default limiter.

## Verification

- RED observed first:
  - `npm test -w apps/web -- csp-report-rate-limiter.test.ts` failed because `limiter.close` was not a function.
- GREEN after implementation:
  - `npm test -w apps/web -- csp-report-rate-limiter.test.ts` passed with 5 tests.
  - `npm test -w apps/web -- csp-report-rate-limiter.test.ts csp-report-rate-limiter.redis.test.ts security-headers.test.ts` passed with 23 tests and 1 skipped Docker integration test.
  - `npm test -w apps/web` passed with 9 files and 88 tests, 1 skipped Docker integration test.
  - `npm run build -w apps/web` passed.
  - `git diff --check` passed for T117-touched files.

## Notes

- Web tests still emit the known Node experimental SQLite warning.
- Nuxt build still emits the known sourcemap warning and externalizes `node:sqlite`; build exits successfully.
