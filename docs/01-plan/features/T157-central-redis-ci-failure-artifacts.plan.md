# T157 Central Redis CI Failure Artifacts Plan

Date: 2026-05-20
Slice: T157 Central Redis CI Failure Artifacts

## Objective

Make `qa-central-redis` CI failures diagnosable by uploading Docker, Redis, Gradle, and Vitest artifacts.

## Current State

- T154 added the `qa-central-redis` GitHub Actions job.
- The job ran the Redis smoke but did not upload failure artifacts.
- The Redis smoke did not produce a stable Vitest report file for CI artifact collection.

## Scope

1. Extend the CI workflow contract with Redis artifact requirements.
2. Add a central Redis artifact collection script.
3. Make the Redis smoke write a Vitest JUnit report to the configured artifact directory.
4. Upload Redis failure artifacts from GitHub Actions when the smoke job fails.

## Acceptance Criteria

- CI workflow contract fails before artifact wiring exists and passes after implementation.
- Redis smoke contract requires artifact output and Vitest JUnit report generation.
- `qa/central-redis-ci-artifacts.ps1` collects Docker state, Redis logs, Gradle reports, and Vitest artifacts.
- Local Redis smoke still passes with `CENTRAL_REDIS_ARTIFACT_DIR` set.
- T157-touched files pass `git diff --check`.
