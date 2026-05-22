# T167 CSP Alert Incident Lifecycle History Report

Date: 2026-05-22

## Completed

- Added `apps/web/server/utils/csp-alert-incident-store.ts` with in-memory and PostgreSQL implementations.
- Wired acknowledgement/snooze operations to append incident events.
- Exposed `alertIncidentHistory` from the guarded CSP telemetry dashboard payload.
- Rendered CSP operator history on `/security`.
- Added PostgreSQL migration DDL for `csp_alert_incident_events`.

## Verification

- RED: `npm.cmd test --workspace @discord-clone/web -- security-headers.test.ts -t "CSP alert incident"` failed on missing incident append and dashboard payload.
- RED: `npm.cmd test --workspace @discord-clone/web -- security-dashboard.test.ts -t "incident lifecycle"` failed on missing UI panel.
- GREEN: `npm.cmd test --workspace @discord-clone/web -- security-headers.test.ts security-dashboard.test.ts csp-telemetry-postgres.test.ts` passed with the Postgres test file skipped unless `NUXT_RUN_POSTGRES_TESTS=true`.
- GREEN: `npm.cmd run build --workspace @discord-clone/web` passed with existing Nuxt sourcemap and Node package export deprecation warnings.
- Browser check: `/security` rendered on `127.0.0.1:4310` at desktop and mobile widths without observed layout overlap.

## Residual Risk

- Real PostgreSQL incident event persistence remains environment-gated by `NUXT_RUN_POSTGRES_TESTS=true`.
- Dev-mode Vite inline style CSP console errors remain visible during local browser inspection and are not introduced by this slice.
