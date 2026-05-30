# T109 PostgreSQL Centralized CSP Telemetry Backend Report

Date: 2026-05-19
Slice: T109 PostgreSQL Centralized CSP Telemetry Backend

## Summary

T109 replaced the browser security dashboard's SQLite telemetry backend with a centralized PostgreSQL implementation. The local Docker `postgres-source` container already had the `discord` database, and the Nuxt telemetry store now selects Postgres through `NUXT_CSP_TELEMETRY_POSTGRES_URL`.

## Loop Result

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 완료 > 28/30 PASS > 다음 계획 진행 가능

## Implemented Changes

- Verified `postgres-source` is mapped to `127.0.0.1:15432`.
- Verified `discord` database exists.
- Added `postgres` dependency to `apps/web`.
- Removed `node:sqlite` telemetry usage from app code.
- Added `PostgresCspTelemetryStore`.
- Changed default durable telemetry config to `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
- Updated dashboard builder and telemetry route for async store reads.
- Added Docker-backed Postgres telemetry integration test.

## Verification

Initial RED:

```powershell
$env:NUXT_RUN_POSTGRES_TESTS='true'
$env:NUXT_CSP_TELEMETRY_POSTGRES_URL='postgres://dev_user:dev_password@127.0.0.1:15432/discord'
npm test -w apps/web -- csp-telemetry-postgres.test.ts
```

Failed because `PostgresCspTelemetryStore` did not exist.

GREEN:

```powershell
$env:NUXT_RUN_POSTGRES_TESTS='true'
$env:NUXT_CSP_TELEMETRY_POSTGRES_URL='postgres://dev_user:dev_password@127.0.0.1:15432/discord'
npm test -w apps/web -- csp-telemetry-postgres.test.ts
npm test -w apps/web -- security-headers.test.ts
npm test -w apps/web
npm run build -w apps/web
```

Notes:

- Postgres integration test: 1 file, 1 test passed.
- Focused CSP/security test: 1 file, 16 tests passed.
- Web workspace test: 9 files passed, 2 skipped; 86 tests passed, 2 skipped.
- Nuxt production build passed.

## Six-Metric Review Score

| Metric | Score |
| --- | ---: |
| Plan/Design Alignment | 5/5 |
| TDD Evidence | 5/5 |
| Security/Privacy | 5/5 |
| Integration Compatibility | 4/5 |
| Documentation/Traceability | 5/5 |
| Residual Risk Control | 4/5 |

Total: 28/30

Decision: PASS
