# T124 Distributed CSP Rate-limit Telemetry Report

Date: 2026-05-20
Slice: T124 Distributed CSP Rate-limit Telemetry

## Completed

- Added `PostgresCspRateLimitTelemetryStore`.
- Added `createDefaultCspRateLimitTelemetryStore()`.
- Added `NUXT_CSP_RATE_LIMIT_TELEMETRY_POSTGRES_URL` support with fallback to `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
- Updated `handleCspReportPayloadAsync()` to await async rate-limit telemetry writes.
- Updated sync handler to reject async rate-limit telemetry stores.
- Updated `buildCspTelemetryDashboard()` to await async rate-limit summaries.
- Added focused unit coverage for async rate-limit telemetry and Postgres store selection.
- Added gated Postgres coverage for central rate-limit telemetry aggregation.

## Verification

- `npm test --workspace @discord-clone/web -- --run security-headers.test.ts` passed with 19 tests.
- `npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` skipped as expected without the Postgres env gate.
- `$env:NUXT_RUN_POSTGRES_TESTS='true'; $env:NUXT_CSP_TELEMETRY_POSTGRES_URL='postgres://dev_user:dev_password@127.0.0.1:15432/discord'; npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` passed with 2 tests.

## Notes

- The new store preserves the existing dashboard contract while changing the default production storage behavior when central Postgres telemetry is configured.
