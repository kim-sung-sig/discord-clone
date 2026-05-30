# T127 CSP Alert Acknowledgement Workflow Report

Date: 2026-05-20
Slice: T127 CSP Alert Acknowledgement Workflow

## Completed

- Added `apps/web/server/utils/csp-alert-acknowledgement-store.ts`.
- Added `POST /api/security/csp-alert-ack`.
- Added `csp_alert_acknowledgements` to the CSP telemetry Postgres migration.
- Wired alert fingerprints and acknowledgement state into `buildCspTelemetryDashboard`.
- Wired the default acknowledgement store into `/api/security/csp-telemetry`.
- Added `/security` acknowledgement status, reason, and optional snooze controls.
- Added focused tests for acknowledgement persistence, validation, UI submission, and token non-disclosure.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts` failed because `csp-alert-acknowledgement-store` did not exist.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` failed because the acknowledgement UI did not exist.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "acknowledges an active CSP alert"` passed.
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts -t "CSP alert acknowledgement"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts` passed with 12 tests.
  - `npm test --workspace @discord-clone/web -- security-headers.test.ts` passed with 23 tests.
  - `npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` skipped 4 gated tests without Postgres env, as expected.
  - `Get-Content apps\web\server\database\migrations\001_csp_telemetry_postgres.sql | docker exec -i postgres-source psql -U dev_user -d discord -v ON_ERROR_STOP=1` passed.
  - `$env:NUXT_RUN_POSTGRES_TESTS='true'; $env:NUXT_CSP_TELEMETRY_POSTGRES_URL='postgres://dev_user:dev_password@127.0.0.1:15432/discord'; npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` passed with 4 tests.
  - `npm run build --workspace @discord-clone/web` passed.
  - `npm test --workspace @discord-clone/web -- --run` passed with 99 tests and 6 skipped after Nuxt build prepared generated manifest files.

## Notes

- The first full Vitest run failed at import time on Nuxt virtual module `#app-manifest`; after the build regenerated Nuxt artifacts, the same full test command passed.
- Missing acknowledgement fingerprints now return 400, and stale fingerprints return 409.
