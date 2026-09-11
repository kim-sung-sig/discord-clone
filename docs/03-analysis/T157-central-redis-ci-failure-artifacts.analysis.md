---
slug: T157-central-redis-ci-failure-artifacts
ticket: T157
phase: analysis
hub: "[[T157-central-redis-ci-failure-artifacts]]"
---
> 🧭 [[T157-central-redis-ci-failure-artifacts]] · PDCA: [[T157-central-redis-ci-failure-artifacts.plan]] → [[T157-central-redis-ci-failure-artifacts.design]] → **analysis** → [[T157-central-redis-ci-failure-artifacts.report]] → [[T157-central-redis-ci-failure-artifacts.feedback]]

# T157 Central Redis CI Failure Artifacts Analysis

Date: 2026-05-20
Slice: T157 Central Redis CI Failure Artifacts

## Findings

- CI failures in Docker-backed smokes are difficult to diagnose without container state and logs.
- Gradle reports already exist after the backend smoke test; the artifact script only needs to copy them.
- Vitest did not previously emit a stable machine-readable report, so the Redis smoke now writes JUnit XML.
- Relative artifact paths must be normalized because npm workspace commands can run from the web app directory.

## Risk Controls

- Artifact collection is best-effort and does not mask the original CI failure.
- Artifact paths live under `qa/artifacts/`, which is already gitignored.

## Follow-Up

- Apply the same failure artifact pattern to the Kafka Gateway smoke.
