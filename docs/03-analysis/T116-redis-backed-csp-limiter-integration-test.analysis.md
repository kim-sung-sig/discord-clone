# T116 Redis-backed CSP Limiter Integration Test Analysis

## Result

The Redis-backed CSP report limiter now has a Docker-backed integration test against a real `redis:7-alpine` container.

## Behavior

- Test file: `apps/web/tests/components/csp-report-rate-limiter.redis.test.ts`
- Default test suite skips the Docker test.
- `NUXT_RUN_DOCKER_TESTS=true` enables the test.
- The test starts Redis on a random localhost port, verifies shared counters across limiter instances, then stops the container.

## Additional Change

`NodeRedisCspReportRateLimitClient` now exposes `close()` so integration tests can release Redis connections cleanly.

## Verification

The first integration run proved the limiter behavior but failed cleanup because `close()` did not exist. After adding `close()`, the Docker-backed test passed.

