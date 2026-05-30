# T168 Privacy-Reviewed Subject Distribution Summary Report

Date: 2026-05-30

## Completed

- Added `subjectDistribution` to CSP rate-limit telemetry summaries.
- Implemented in-memory and PostgreSQL aggregation by stored subject hash while returning only ranked counts.
- Exposed the distribution from `buildCspTelemetryDashboard`.
- Rendered a privacy-safe subject distribution panel on `/security`.
- Added focused payload and dashboard tests to prevent raw subject/hash exposure.

## Verification

- GREEN: `npm test --workspace @discord-clone/web -- security-headers.test.ts security-dashboard.test.ts csp-telemetry-postgres.test.ts` passed with `csp-telemetry-postgres.test.ts` skipped under the existing `NUXT_RUN_POSTGRES_TESTS` gate.
- GREEN: `npm run build --workspace @discord-clone/web` passed with existing Nuxt sourcemap and Node package export deprecation warnings.

## Residual Risk

- Live PostgreSQL round-trip for the distribution remains environment-gated.
- The panel intentionally avoids stable subject identifiers, so it supports triage of concentration but not subject-level investigation.
