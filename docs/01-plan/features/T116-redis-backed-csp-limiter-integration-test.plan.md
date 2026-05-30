# T116 Redis-backed CSP Limiter Integration Test Plan

## Objective

Verify the Redis-backed CSP report limiter against a real Redis server, not only a fake client.

## Current State

- T102 added Redis limiter behavior tests with a fake Redis client.
- Docker is available locally.
- The web test suite uses Vitest with Nuxt environment.

## Scope

1. Add a Docker-backed Redis integration test.
2. Keep full default tests fast by requiring an explicit environment flag.
3. Start Redis on a random host port.
4. Verify two limiter instances share the same Redis counter.
5. Stop and remove the Redis container after the test.

## Acceptance Criteria

- `NUXT_RUN_DOCKER_TESTS=true npm test -w apps/web -- csp-report-rate-limiter.redis.test.ts` runs against a real Redis container.
- Default `npm test -w apps/web` does not require Docker and skips the integration test.
- Full web tests and build still pass.

