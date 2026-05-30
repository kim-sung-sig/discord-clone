# T123 CI Docker Test Toggle Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T123 CI Docker Test Toggle

## Design

Add a separate `qa-web-docker` GitHub Actions job rather than changing the default frontend job.

| Element | Design |
| --- | --- |
| Contract | `qa/web-docker-tests.contract.ps1` |
| Docker check | `docker version` |
| Test command | `npm test --workspace @discord-clone/web -- csp-report-rate-limiter.redis.test.ts` |
| Toggle | `NUXT_RUN_DOCKER_TESTS=true` |

## Rationale

The default frontend job should stay fast and Docker-independent. The dedicated QA job makes the integration requirement visible, keeps failures isolated, and matches the existing pattern of Docker-backed QA gates.

## Security Review

- The Redis test binds the container to `127.0.0.1`.
- The container is unique per process and removed after the test.
- No production credentials are injected into the job.
