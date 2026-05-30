# T169 Durable Operator Token Store Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T169 Durable Operator Token Store

## Design

Add `PostgresSecurityDashboardOperatorTokenStore` implementing the existing T129 store contract.

## Tables

`security_dashboard_operator_tokens`:

- `token_hash` primary key
- `actor`
- `issued_at`
- `expires_at`
- `revoked_at`

`security_dashboard_operator_token_audit`:

- `id`
- `action`
- `token_hash_prefix`
- `actor`
- `occurred_at`
- `reason`

## Default Selection

`createDefaultSecurityDashboardOperatorTokenStore` chooses:

1. explicit `databaseUrl` option
2. `NUXT_SECURITY_DASHBOARD_OPERATOR_TOKEN_POSTGRES_URL`
3. `NUXT_CSP_TELEMETRY_POSTGRES_URL`
4. in-memory fallback

## Security Review

The store hashes tokens before persistence. Audit rows use only the first 12 hex characters of the token hash. Raw `sdo_...` tokens are returned only from `issue()` and never written to database rows.
