---
slug: T135-explicit-csp-telemetry-postgres-migration
ticket: T135
phase: feedback
hub: "[[T135-explicit-csp-telemetry-postgres-migration]]"
---
> 🧭 [[T135-explicit-csp-telemetry-postgres-migration]] · PDCA: [[T135-explicit-csp-telemetry-postgres-migration.plan]] → [[T135-explicit-csp-telemetry-postgres-migration.design]] → [[T135-explicit-csp-telemetry-postgres-migration.analysis]] → [[T135-explicit-csp-telemetry-postgres-migration.report]] → **feedback**

# T135 Explicit CSP Telemetry Postgres Migration Feedback

Date: 2026-05-20
Slice: T135 Explicit CSP Telemetry Postgres Migration

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T136 CSP Telemetry Postgres Health Metric | P2 | Schema ownership is explicit, but operators still need database health/write-failure visibility. |
| T137 CSP Telemetry SQLite Legacy Cleanup Note | P2 | Postgres migration is documented, but old SQLite telemetry files still need archive/delete guidance. |

## Notes

- T135 closes the explicit migration ownership gap for CSP telemetry Postgres tables.
