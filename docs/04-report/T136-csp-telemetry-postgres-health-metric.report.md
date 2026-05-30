# T136 CSP Telemetry Postgres Health Metric Report

Date: 2026-05-20
Slice: T136 CSP Telemetry Postgres Health Metric

## Completed

- Added `CspTelemetryStorageHealth`.
- Added in-memory and Postgres telemetry store health reporting.
- Added Postgres write failure counting and sanitized last error capture.
- Added `health.storage` to the CSP telemetry dashboard payload.
- Added `/security` summary cards for telemetry storage and write failures.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts` failed because `dashboard.health.storage` was missing.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` failed because `[data-testid="csp-telemetry-storage-health"]` was missing.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts` passed with 21 tests.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` passed with 11 tests.
  - `npm test --workspace @discord-clone/web -- --run` passed with 96 tests and 5 skips.
  - With `NUXT_RUN_POSTGRES_TESTS=true`, `npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` passed with 3 tests.
  - `npm run build --workspace @discord-clone/web` passed.

## Notes

- Nuxt build still emits the existing sourcemap/deprecation warnings, but exits successfully.
