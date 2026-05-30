# T106 Database-backed Dashboard Telemetry Feedback

Date: 2026-05-19
PDCA Phase: Act
Slice: T106 Database-backed Dashboard Telemetry

## Decisions

- Use Node `node:sqlite` to avoid adding npm dependencies.
- Keep in-memory as the default for local/test speed.
- Enable durable dashboard telemetry only when `NUXT_CSP_TELEMETRY_SQLITE_PATH` is configured.
- Persist normalized CSP report fields only.
- Keep dashboard API/page contract unchanged.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| T54 dashboard telemetry was process-local. | Added SQLite-backed store. |
| Dashboard telemetry disappeared after restart. | Store can be reopened and read from the same DB file. |
| Durable mode needed low-friction local configuration. | Added `.env.example` entry for SQLite path. |
| Existing routes imported a fixed default store. | Added factory while preserving `defaultCspTelemetryStore` import path. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T105 admin RBAC for security dashboard | P1 | Durable dashboard data increases the need for real admin-only access control. |
| T99 CSP telemetry retention policy | P1 | SQLite store currently keeps records until external cleanup. |
| T109 PostgreSQL/centralized CSP telemetry backend | P2 | SQLite is useful for single-node durability, but not multi-instance aggregation. |
| T110 Node SQLite runtime compatibility gate | P2 | `node:sqlite` emits an experimental warning and should be guarded in CI/runtime docs. |

## Verification

- `npm test -w apps/web -- security-headers.test.ts`: PASS
- `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts`: PASS
- `npm test -w apps/web`: PASS
- `npm run build -w apps/web`: PASS

## Loop Decision

T106 scored 27/30 and passed the threshold. Continue with T105, then T102/T99/T103/T100 depending on whether access control or telemetry operations is the immediate production gate.
