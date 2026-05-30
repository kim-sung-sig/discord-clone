# T110 Node SQLite Runtime Compatibility Gate Analysis

Date: 2026-05-21

## Findings

- Active CSP telemetry code uses Postgres when `NUXT_CSP_TELEMETRY_POSTGRES_URL` is present.
- The fallback backend is in-memory telemetry.
- `NUXT_CSP_TELEMETRY_SQLITE_PATH` is absent from `.env.example`.
- Existing runbook already described archive/delete behavior, but did not contractually require the Node compatibility boundary.

## Decision

Mark SQLite as legacy-only and avoid implementing a SQLite maintenance command unless a future explicit exception is approved. The existing runbook covers archive/delete handling without reintroducing runtime dependencies.

## Risk

Historical docs still mention SQLite because prior tasks implemented and then superseded it. The new contract reduces the risk that those historical references get interpreted as active runtime support.
