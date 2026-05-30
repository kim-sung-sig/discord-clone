# T126 CSP Alert Persistence Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T126 CSP Alert Persistence

## Design

Add `csp-alert-transition-store.ts` with a narrow store interface:

| Method | Purpose |
| --- | --- |
| `recordIfChanged(alert, observedAt)` | Store a transition only when active state or reasons changed. |
| `recent(limit)` | Return recent transitions for dashboard review. |

The default store selects Postgres through `NUXT_CSP_ALERT_POSTGRES_URL` or `NUXT_CSP_TELEMETRY_POSTGRES_URL`, otherwise it falls back to in-memory storage.

## Dashboard Flow

1. Dashboard route builds the aggregate CSP summary.
2. `evaluateCspTelemetryAlert` computes the current alert state.
3. The transition store records the state only if it changed.
4. The API returns `alertHistory`.
5. `/security` renders active and cleared transitions.

## Security Review

Alert transitions store only:

- `active`
- `observedAt`
- sanitized aggregate threshold reasons

They do not store raw CSP reports, IP addresses, rate-limit subjects, headers, or user-agent values.
