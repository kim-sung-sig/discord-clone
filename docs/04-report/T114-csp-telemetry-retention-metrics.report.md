# T114 CSP Telemetry Retention Metrics Report

## Completed

- Added aggregate retention discard metrics to CSP telemetry stores.
- Added persistent SQLite retention metric counters.
- Added retention metrics to dashboard payload.
- Rendered total retention discards in `/security`.
- Added focused coverage for in-memory, SQLite, dashboard payload, and UI behavior.

## Verification

- RED observed first:
  - `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts` failed because `dashboard.retention` and `[data-testid="csp-retention-discarded"]` did not exist.
- GREEN after implementation:
  - `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts` passed with 25 tests.
  - `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts security-dashboard-access.test.ts` passed with 34 tests.
  - `npm test -w apps/web` passed with 9 files and 87 tests, 1 skipped Docker integration test.
  - `npm run build -w apps/web` passed.
  - `git diff --check` passed for T114-touched files.

## Notes

- Web tests still emit the known Node experimental SQLite warning.
- Nuxt build still emits the known sourcemap warning and externalizes `node:sqlite`; build exits successfully.
