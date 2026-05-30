# T109 PostgreSQL Centralized CSP Telemetry Backend Plan

Date: 2026-05-19
Slice: T109 PostgreSQL Centralized CSP Telemetry Backend

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

The browser security dashboard previously used local SQLite durability from T106. The user provided a central Docker Postgres source at `127.0.0.1:15432`, user `dev_user`, password `dev_password`, and requested the `discord` database plus a PostgreSQL-backed telemetry implementation instead of SQLite.

## Implementation Plan

Major topics:

1. Central database setup
   - Verify `postgres-source` is running on port `15432`.
   - Ensure the `discord` database exists.

2. Postgres telemetry store
   - Replace SQLite telemetry persistence with `PostgresCspTelemetryStore`.
   - Keep the existing `CspTelemetryStore` contract while allowing async store operations.
   - Preserve retention metrics and sanitized report storage.

3. Runtime configuration
   - Replace `NUXT_CSP_TELEMETRY_SQLITE_PATH` with `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
   - Keep in-memory fallback when no Postgres URL is configured.

4. Verification
   - Add a Docker-backed Postgres telemetry test gated by `NUXT_RUN_POSTGRES_TESTS=true`.
   - Run focused CSP/security tests and Nuxt build.

## Acceptance Criteria

- `discord` database exists in `postgres-source`.
- CSP telemetry can persist into central Postgres.
- SQLite runtime dependency and env path are removed from app code.
- Dashboard builder and routes support async telemetry stores.
- Postgres integration test, web tests, and build pass.
