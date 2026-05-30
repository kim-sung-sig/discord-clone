# T124 Distributed CSP Rate-limit Telemetry Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T124 Distributed CSP Rate-limit Telemetry

## Store Design

Add `PostgresCspRateLimitTelemetryStore` beside the existing in-memory store.

| Field | Type | Purpose |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Stable ordering |
| `received_at` | `TIMESTAMPTZ` | Limited report timestamp |
| `subject_hash` | `TEXT` | SHA-256 hash of normalized subject |
| `reset_at` | `TIMESTAMPTZ` | Limiter reset time |

The table is bounded by `maxEntries`, defaulting to 1,000.

## Runtime Selection

`createDefaultCspRateLimitTelemetryStore()` uses:

1. explicit `databaseUrl` option,
2. `NUXT_CSP_RATE_LIMIT_TELEMETRY_POSTGRES_URL`,
3. `NUXT_CSP_TELEMETRY_POSTGRES_URL`,
4. in-memory fallback.

## Async Contract

- `CspRateLimitTelemetryStore.recordLimited()` and `summary()` may return promises.
- Sync `handleCspReportPayload()` rejects async telemetry stores.
- Async `handleCspReportPayloadAsync()` awaits rate-limit telemetry writes.
- `buildCspTelemetryDashboard()` awaits async rate-limit summaries.

## Risk Controls

- The persisted subject is hashed; raw IP/header-derived subjects are not stored.
- The route already uses async handling, so Postgres writes can be awaited in production paths.
- Existing in-memory tests preserve local behavior.
