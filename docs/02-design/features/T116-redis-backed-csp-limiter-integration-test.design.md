# T116 Redis-backed CSP Limiter Integration Test Design

## Test Strategy

Add `apps/web/tests/components/csp-report-rate-limiter.redis.test.ts`.

The test is skipped unless:

```text
NUXT_RUN_DOCKER_TESTS=true
```

When enabled, the test:

1. Starts `redis:7-alpine` with Docker.
2. Reads the mapped host port through `docker port`.
3. Creates two `RedisCspReportRateLimiter` instances using the real Redis URL.
4. Confirms the third report for the same subject is denied across instances.
5. Stops the container in `afterAll`.

## No New Testcontainers Dependency

Use Docker CLI directly instead of adding a large Testcontainers dependency. This keeps package churn small and works on the current Windows development environment.

## Safety

- Bind Redis to `127.0.0.1`.
- Use `--rm` and explicit cleanup.
- Generate a unique container name per test run.

