# T123 CI Docker Test Toggle Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T123 CI Docker Test Toggle

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T116 added a Docker-backed web Redis integration test, but CI still only ran the default skipped path. |
| Solution | Add a dedicated CI job that checks Docker availability and runs the Redis limiter test with `NUXT_RUN_DOCKER_TESTS=true`. |
| Operator Effect | CI now proves the web CSP Redis limiter against a real Redis container when Docker is available. |
| Core Value | Distributed rate-limit behavior is covered by executable integration evidence, not only fake-client tests. |

## Scope

- Add a CI contract for Docker-backed web integration tests.
- Add a `qa-web-docker` workflow job.
- Run only the Docker-gated Redis limiter test in that job.
- Keep the default frontend test suite unchanged.

## Out of Scope

- Running every web test with Docker enabled.
- Adding Testcontainers.
- Changing Redis limiter behavior.

## Success Criteria

- CI workflow contains a Docker availability check.
- CI runs `csp-report-rate-limiter.redis.test.ts` with `NUXT_RUN_DOCKER_TESTS=true`.
- The Docker test remains loopback-bound and env-gated.
- The local Docker-backed test passes.
