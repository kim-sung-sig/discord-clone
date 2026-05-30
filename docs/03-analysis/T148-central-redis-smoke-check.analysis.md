# T148 Central Redis Smoke Check Analysis

Date: 2026-05-20
Slice: T148 Central Redis Smoke Check

## Findings

- Existing backend Redis tests validate store behavior with mocks, not the central endpoint.
- Existing web Redis integration starts an isolated Docker Redis container, which is useful for limiter behavior but does not prove central resource wiring.
- Local central resources may already be running as standalone containers named `ms-redis`, `ms-kafka`, and `postgres-source`; the smoke script must tolerate that state.

## Root Cause From First Smoke Failure

The first script run tried to start Compose `ms-redis`, but port `16379` was already allocated by an existing `ms-redis` container. The script now checks that container first and reuses it when healthy.

## Risks

- The smoke currently assumes the development Redis password is available to the local operator or CI job.
- Gradle test caching can hide backend smoke execution unless the script forces a rerun.

## Follow-Up

- Promote the central Redis smoke to a CI/QA gate once Docker availability and shared resource lifecycle are defined.
