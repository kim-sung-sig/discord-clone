# T169 Durable Operator Token Store Report

Date: 2026-05-21
Slice: T169 Durable Operator Token Store

## Completed

- Added `PostgresSecurityDashboardOperatorTokenStore`.
- Added `createDefaultSecurityDashboardOperatorTokenStore`.
- Added `security_dashboard_operator_tokens` and `security_dashboard_operator_token_audit` to the SQL migration.
- Added `NUXT_SECURITY_DASHBOARD_OPERATOR_TOKEN_POSTGRES_URL` to `.env.example`.
- Added focused default-selection and gated Postgres persistence tests.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts -t "durable Postgres operator token"` failed because the default factory did not exist.
  - `NUXT_RUN_POSTGRES_TESTS=true ... npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts -t "operator token hashes"` failed because the Postgres store did not exist.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts -t "durable Postgres operator token"` passed.
  - `NUXT_RUN_POSTGRES_TESTS=true ... npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts -t "operator token hashes"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts security-dashboard.test.ts security-headers.test.ts` passed with 50 tests.
  - `npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` skipped 5 gated tests without Postgres env, as expected.
  - SQL migration applied to local `postgres-source`.
  - `NUXT_RUN_POSTGRES_TESTS=true ... npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts` passed with 5 tests.
  - `npm run build --workspace @discord-clone/web` passed.
  - `npm test --workspace @discord-clone/web -- --run` passed with 104 tests and 7 skipped after Nuxt build regenerated generated manifest artifacts.

## Notes

- Full Vitest hit the known Nuxt `#app-manifest` generated artifact timing issue before build; the same command passed after build regeneration.
