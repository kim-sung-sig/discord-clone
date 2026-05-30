# T154 Central Redis Smoke CI Gate Analysis

Date: 2026-05-20
Slice: T154 Central Redis Smoke CI Gate

## Findings

- GitHub Actions Ubuntu runners provide Docker, but the existing Redis smoke script needed a portable Gradle wrapper path.
- The central Redis smoke starts or reuses Docker Compose `ms-redis`, so no GitHub Actions Redis service is required.
- The web portion requires npm dependencies, so the CI job must run `npm ci` before the smoke.

## Risks

- The job depends on Docker Compose availability in the runner.
- The first run may be slower because it pulls the Redis image and Gradle/npm dependencies.

## Follow-Up

- Capture Docker container logs and `docker ps` output on central smoke failures.
