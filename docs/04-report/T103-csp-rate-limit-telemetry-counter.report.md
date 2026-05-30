# T103 CSP Rate-limit Telemetry Counter Report

## Completed

- Added `CspRateLimitTelemetryStore`.
- Added in-memory rate-limit telemetry counter.
- Wired sync and async CSP report handlers to record limited reports.
- Wired both CSP report routes to the default rate-limit telemetry store.
- Added `rateLimit.limitedTotal` to the dashboard payload.
- Added tests for limited-report counting and dashboard exposure.

## Verification

- RED observed first:
  - `npm test -w apps/web -- security-headers.test.ts` failed because `csp-rate-limit-telemetry-store` did not exist.
- GREEN after implementation:
  - `npm test -w apps/web -- security-headers.test.ts` passed.
  - `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts csp-report-rate-limiter.test.ts security-dashboard-access.test.ts` passed.
  - `npm test -w apps/web` passed with 9 files and 79 tests, 1 skipped Docker integration test.
  - `npm run build -w apps/web` passed.
  - `git diff --check` passed for T103-touched files.

## Notes

- Web tests still emit the known Node experimental SQLite warning.
- Nuxt build still externalizes `node:sqlite`; build exits successfully.

