# T148 Central Redis Smoke Check Design

Date: 2026-05-20
Slice: T148 Central Redis Smoke Check

## Design

The smoke check is intentionally opt-in because it depends on Docker and the shared local Redis endpoint.

## Backend Smoke

- Test: `CentralRedisConnectivitySmokeTest`
- Gate: `DISCORD_RUN_CENTRAL_REDIS_SMOKE=true`
- Endpoint defaults:
  - `SPRING_DATA_REDIS_HOST=127.0.0.1`
  - `SPRING_DATA_REDIS_PORT=16379`
  - `SPRING_DATA_REDIS_PASSWORD=dev_password`
- Behavior: create a Lettuce connection, write `discord:central-redis-smoke:backend`, then read it back.

## Web Smoke

- Test: `csp-report-rate-limiter.central-redis.test.ts`
- Gate: `NUXT_RUN_CENTRAL_REDIS_SMOKE=true`
- Endpoint: `NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL`
- Behavior: create two Redis-backed CSP limiter instances with the same key prefix and verify the third report is blocked by the shared counter.

## QA Script

`qa/central-redis-smoke.ps1` first reuses an already running `ms-redis` container when it responds to `redis-cli ping`. If no central Redis is running, it starts the Compose `ms-redis` service and waits for readiness.

The backend Gradle test uses `--rerun-tasks` so the smoke produces fresh execution evidence even when Gradle previously cached the test task.
