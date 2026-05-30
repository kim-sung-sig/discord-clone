# T123 CI Docker Test Toggle Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T123 CI Docker Test Toggle

## Findings

| Finding | Result |
| --- | --- |
| Docker-backed Redis test already existed | `apps/web/tests/components/csp-report-rate-limiter.redis.test.ts` was gated by `NUXT_RUN_DOCKER_TESTS=true`. |
| CI did not enable the gate | The default frontend job ran `npm test --workspaces`, which skips the Docker test. |
| Dedicated job is the lowest-risk CI change | A separate `qa-web-docker` job avoids making the main frontend job depend on Docker. |

## Security Review

The test remains safe for CI because Redis is bound only to loopback with an ephemeral host port and no secrets. The workflow does not expose service credentials or publish runtime logs containing sensitive data.

## Residual Risk

- CI Docker availability is assumed for GitHub-hosted Ubuntu runners; the job fails clearly at `docker version` if unavailable.
- Failure artifacts are minimal for this web-only Docker test. Add artifact capture only if this job becomes flaky in CI.
