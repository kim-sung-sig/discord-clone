# T104 Trusted Proxy Subject Normalization Report

## Completed

- Added shared CSP rate-limit subject normalization.
- Defaulted CSP report rate-limit subjects to the direct peer address.
- Allowed forwarded headers only when the direct peer matches trusted proxy config.
- Wired both CSP report routes to the shared utility.
- Added trusted proxy env documentation.

## Verification

- RED observed first:
  - `npm test -w apps/web -- security-headers.test.ts` failed because `csp-rate-limit-subject` did not exist.
- GREEN after implementation:
  - `npm test -w apps/web -- security-headers.test.ts` passed with 17 tests.
  - `npm test -w apps/web -- security-headers.test.ts csp-report-rate-limiter.test.ts csp-report-rate-limiter.redis.test.ts` passed with 21 tests and 1 skipped Docker integration test.
  - `npm test -w apps/web` passed with 9 files and 84 tests, 1 skipped Docker integration test.
  - `npm run build -w apps/web` passed.
  - `git diff --check` passed for T104-touched files.

## Notes

- Web tests still emit the known Node experimental SQLite warning.
- Nuxt build still emits the known sourcemap warning and externalizes `node:sqlite`; build exits successfully.
