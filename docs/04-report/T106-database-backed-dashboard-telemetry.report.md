# T106 Database-backed Dashboard Telemetry Report

Date: 2026-05-19
PDCA Phase: Report
Slice: T106 Database-backed Dashboard Telemetry

## Summary

T106 added a SQLite-backed CSP telemetry store for the browser security dashboard. When `NUXT_CSP_TELEMETRY_SQLITE_PATH` is configured, normalized CSP reports persist across Nuxt server restarts while the `/security` dashboard and `/api/security/csp-telemetry` response contract stay unchanged.

## Loop Result

계획 검토 됨 > 설계 문서 작성 됨 > RED 테스트 확인 > 구현 진행 함 > 검증 완료 > 27/30 PASS > 잔여 task 우선순위 재정렬 완료

## Implemented Changes

- Added `SqliteCspTelemetryStore`.
- Added SQLite schema and indexes for CSP telemetry.
- Added durable `record`, `recent`, `summary`, and `close` behavior.
- Added `createDefaultCspTelemetryStore`.
- Kept `InMemoryCspTelemetryStore` as the default when no DB path is configured.
- Added `.env.example` entries:
  - `NUXT_SECURITY_DASHBOARD_TOKEN`
  - `NUXT_CSP_TELEMETRY_SQLITE_PATH`
- Added tests for persistence across store instances and factory selection.
- Added residual task priority reorder document.

## Verification

Passed:

```powershell
npm test -w apps/web -- security-headers.test.ts
npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts
npm test -w apps/web
npm run build -w apps/web
```

Observed:

- Focused CSP test: 1 file, 12 tests passed.
- T54/T106 focused tests: 2 files, 14 tests passed.
- Web workspace tests: 7 files, 63 tests passed.
- Nuxt production build passed.
- Node emitted an experimental warning for `node:sqlite`.

## Runtime Configuration

Default local behavior remains in-memory:

```env
NUXT_CSP_TELEMETRY_SQLITE_PATH=
```

Durable dashboard telemetry:

```env
NUXT_CSP_TELEMETRY_SQLITE_PATH=./data/csp-telemetry.sqlite
```

Optional dashboard API guard:

```env
NUXT_SECURITY_DASHBOARD_TOKEN=replace-with-local-dashboard-token
```

## Residual Risks

- SQLite is single-node durability, not distributed multi-instance aggregation.
- `node:sqlite` emits an experimental runtime warning in Node 24.
- Dashboard API still needs real admin RBAC.
- Retention/time-window policy is not enforced yet.
- CSP limiter remains process-local.

## Next Recommended Task

Proceed with `T105 admin RBAC for security dashboard` before exposing the dashboard in a real multi-user environment. Then handle Redis-backed CSP limiter and retention/alerting in the reordered residual backlog.
