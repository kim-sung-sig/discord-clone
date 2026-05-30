# T107 CSP Telemetry Trend Chart Report

Date: 2026-05-21
Slice: T107 CSP telemetry trend chart

## Completed

- Added six-hour CSP telemetry trend buckets to the dashboard payload.
- Added bounded trend read options to the dashboard builder.
- Rendered a compact CSP trend chart in `/security`.
- Added CSS with stable chart dimensions.
- Added tests for payload buckets, UI rendering, zero buckets, and secret-safe output.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts security-dashboard.test.ts` failed because `dashboard.trend` and `[data-testid="csp-trend-chart"]` did not exist.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts security-dashboard.test.ts` passed with 40 tests.
  - `npm run build --workspace @discord-clone/web` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts security-headers.test.ts csp-telemetry-postgres.test.ts` passed after build with 40 passed and 5 skipped.
  - `npm test --workspace @discord-clone/web -- --run` passed with 109 passed and 7 skipped.

## Notes

- The first related test run before build hit the known Nuxt `#app-manifest` issue; the same command passed after `npm run build`.
