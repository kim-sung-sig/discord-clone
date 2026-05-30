# T135 Explicit CSP Telemetry Postgres Migration Report

Date: 2026-05-20
Slice: T135 Explicit CSP Telemetry Postgres Migration

## Completed

- Added explicit Postgres migration SQL for CSP telemetry schema.
- Added CSP telemetry Postgres migration runbook.
- Added `qa/csp-telemetry-postgres-migration.contract.ps1`.
- Applied the migration to local central `postgres-source`.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/csp-telemetry-postgres-migration.contract.ps1` failed because the migration SQL was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/csp-telemetry-postgres-migration.contract.ps1` passed.
  - `Get-Content apps/web/server/database/migrations/001_csp_telemetry_postgres.sql | docker exec -i postgres-source psql -U dev_user -d discord -v ON_ERROR_STOP=1` passed.
  - With `NUXT_RUN_POSTGRES_TESTS=true` and `NUXT_CSP_TELEMETRY_POSTGRES_URL=postgres://dev_user:dev_password@127.0.0.1:15432/discord`, `npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` passed with 3 tests.

## Notes

- Local apply reported `IF NOT EXISTS` notices because the lazy runtime stores had already created the tables.
