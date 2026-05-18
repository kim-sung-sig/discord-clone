# T35 Redis-backed Runtime Controls Feedback

작성일: 2026-05-16  
PDCA Phase: Act  
Slice: T35 Redis-backed Runtime Controls

## Current State

T35 is implemented and verified. Targeted Redis runtime-control tests, boot regression tests, and the full Gradle test suite pass.

## Decisions

- Use `redis` Spring profile to select Redis runtime-control adapters.
- Keep in-memory adapters for default local/unit-test runs.
- Require `redis` in production to prevent process-local runtime controls.
- Fail closed for rate-limited auth/write/gateway endpoints when Redis is unavailable.
- Degrade presence/typing reads to offline/empty when Redis is unavailable because presence is ephemeral.

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| Redis runtime integration harness | P1 | T35 currently has focused mocked Redis tests; a Docker/Testcontainers-backed Redis smoke should be added once command execution and CI Docker policy are available. |
| Redis key scan replacement | P1 | `RedisPresenceTtlStore.keys(prefix)` uses Redis key pattern lookup for the current small runtime scope. High-volume Gateway/presence work should replace this with SCAN or explicit registry sets. |
| Clock bean qualification cleanup | P2 | `authClock` and `presenceClock` coexist and rely on parameter-name resolution. Add qualifiers if future compiler or Spring config changes weaken name-based injection. |

## Required Before Completion

- Completed: targeted T35 backend tests.
- Completed: boot regression tests.
- Completed: full Gradle test suite.
- Completed: `production,postgres,redis` config validation.
