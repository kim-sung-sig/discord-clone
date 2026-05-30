# T106 Database-backed Dashboard Telemetry Plan

Date: 2026-05-19
PDCA Phase: Plan
Slice: T106 Database-backed Dashboard Telemetry

## Executive Summary

| Perspective | Content |
| --- | --- |
| Problem | T54 made CSP telemetry visible in `/security`, but the data is still process-local and disappears on restart. |
| Solution | Add a database-backed CSP telemetry store that can be enabled for Nuxt server runtime without changing the dashboard API contract. |
| Function UX Effect | 운영자는 서버 재시작 후에도 최근 CSP 위반과 directive 요약을 계속 볼 수 있다. |
| Core Value | Browser security telemetry moves from volatile diagnostics toward durable operational evidence. |

## Scope

- Add a SQLite-backed `CspTelemetryStore` implementation using Node runtime capabilities.
- Preserve the existing in-memory default when no DB path is configured.
- Select the database-backed store through `NUXT_CSP_TELEMETRY_SQLITE_PATH`.
- Store only normalized/sanitized CSP report fields.
- Keep existing `/api/security/csp-telemetry` dashboard contract unchanged.
- Add tests for persistence across store instances, bounded recent reports, summary counts, and default store selection.
- Re-rank remaining residual tasks after T106.

## Out of Scope

- Full PostgreSQL-backed Nuxt telemetry.
- Backend Spring Boot CSP telemetry ingestion.
- Dashboard admin RBAC.
- Time-series charts or alerting.
- Multi-instance distributed aggregation beyond a shared SQLite file on one host.

## Success Criteria

- A second store instance can read reports written by the first store instance.
- Recent reports are returned newest-first and bounded by caller limit.
- Summary counts by effective directive persist across store instances.
- Sensitive raw fields remain absent because only normalized reports are recorded.
- Existing T54 dashboard API/page tests keep passing.
- Build succeeds without adding new npm dependencies.

## Failure Criteria

- Database mode becomes the default and breaks local/dev test speed.
- Raw CSP report JSON, query strings, script samples, or user agents are persisted.
- Dashboard API response shape changes.
- Store fails hard when no DB path is configured.
- Implementation requires a new package without a clear need.
