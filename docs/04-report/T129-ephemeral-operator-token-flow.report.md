# T129 Ephemeral Operator Token Flow Report

Date: 2026-05-21
Slice: T129 Ephemeral Operator Token Flow

## Completed

- Added `apps/web/server/utils/security-dashboard-operator-token-store.ts`.
- Added `POST /api/security/operator-token/exchange`.
- Added `POST /api/security/operator-token/revoke`.
- Extended dashboard authorization to verify issued operator tokens through a store.
- Updated guarded CSP dashboard routes to use issued tokens when `NUXT_SECURITY_DASHBOARD_TOKEN` is configured.
- Updated `/security` to exchange the bootstrap token, store the issued token, render expiry, and revoke on clear.
- Updated `.env.example` to document `NUXT_SECURITY_DASHBOARD_TOKEN` as a bootstrap secret.

## Verification

- RED observed first:
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts -t "ephemeral operator tokens"` failed because the operator token store did not exist.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "exchanges a bootstrap"` failed because the UI did not call the exchange endpoint.
- GREEN after implementation:
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts -t "ephemeral operator tokens"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "exchanges a bootstrap"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard.test.ts -t "lets an operator save"` passed.
  - `npm test --workspace @discord-clone/web -- security-dashboard-access.test.ts security-dashboard.test.ts security-headers.test.ts` passed with 49 tests.
  - `npm test --workspace @discord-clone/web -- --run` passed with 103 tests and 6 skipped.
  - `npm run build --workspace @discord-clone/web` passed.

## Notes

- Initial parallel Vitest runs hit the known Nuxt `#app-manifest` generated artifact timing issue; the same tests passed after build regeneration.
- Build still emits the known Nuxt sourcemap warning and Vue package trailing slash deprecation warning; build exits successfully.
