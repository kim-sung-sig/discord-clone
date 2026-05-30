# T130 CSP Retention Metrics Breakdown UI Report

Date: 2026-05-21
Slice: T130 CSP Retention Metrics Breakdown UI

## Completed

- Added RED coverage for age and max-entry CSP retention breakdown counters.
- Updated `/security` to show `discardedByAge` and `discardedByMaxEntries` under the existing retention total.
- Added compact CSS for the retention breakdown list.
- Updated the residual queue so T130 is marked complete and T131 is next.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` failed because `[data-testid="csp-retention-discarded-by-age"]` did not exist.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` passed with 14 tests.
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts security-dashboard.test.ts security-headers.test.ts` passed with 50 tests.
  - `npm run build --workspace @discord-clone/web` passed.
  - `npm test --workspace @discord-clone/web -- --run` passed with 104 tests and 7 skipped.
  - `git diff --check` passed with CRLF warnings only.

## Notes

- The UI remains aggregate-only and does not render discarded request IDs or raw report origins.
- Nuxt build still emits the known sourcemap and Vue package trailing slash deprecation warnings; build exits successfully.
