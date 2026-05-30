# T128 CSP Rate-Limit Subject Diagnostics Report

Date: 2026-05-20
Slice: T128 CSP Rate-Limit Subject Diagnostics

## Completed

- Added secret-safe CSP rate-limit subject diagnostics to `csp-rate-limit-subject.ts`.
- Added diagnostics to the guarded `/api/security/csp-telemetry` dashboard payload.
- Added a `/security` subject diagnostics panel.
- Added focused tests for diagnostic generation and UI rendering without raw IP exposure.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts -t "subject diagnostics"` failed because `cspRateLimitSubjectDiagnosticsFor` did not exist.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "subject diagnostics"` failed because the diagnostics panel did not exist.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts -t "subject diagnostics"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "subject diagnostics"` passed.
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts` passed with 24 tests.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` passed with 13 tests.
  - `npm test --workspace @discord-clone/web -- --run` passed with 101 tests and 6 skipped.
  - `npm run build --workspace @discord-clone/web` passed.

## Notes

- Build still emits the known Nuxt sourcemap warning and Vue package trailing slash deprecation warning; build exits successfully.
