# T99 CSP Telemetry Retention Policy Report

## Completed

- Added `CspTelemetryRetentionPolicy`.
- Added age pruning to `InMemoryCspTelemetryStore`.
- Added age and count pruning to `SqliteCspTelemetryStore`.
- Wired retention env parsing into `createDefaultCspTelemetryStore`.
- Added `.env.example` retention controls.
- Added RED/GREEN tests for memory and SQLite retention.

## Verification

- `npm test -w apps/web -- security-headers.test.ts` passed.
- `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts security-dashboard-access.test.ts` passed.
- `npm test -w apps/web` passed with 8 files and 73 tests.
- `npm run build -w apps/web` passed.
- `git diff --check` passed for T99-touched files.

## Notes

- Node still emits the known experimental SQLite warning.
- Nuxt build still externalizes `node:sqlite`; build exits successfully.

