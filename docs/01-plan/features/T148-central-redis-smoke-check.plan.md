# T148 Central Redis Smoke Check Plan

Date: 2026-05-20
Slice: T148 Central Redis Smoke Check

## Objective

Prove backend Redis connectivity and Nuxt CSP Redis rate limiting against the shared central Redis endpoint.

## Current State

- Central runtime profiles point Redis clients at `127.0.0.1:16379` / `ms-redis:6379`.
- Existing Redis tests either mock backend Redis behavior or start an isolated web Redis container.
- No single smoke check proves both backend and web can use the central Redis resource.

## Scope

1. Add a contract for the central Redis smoke assets.
2. Add a backend JUnit smoke test gated by `DISCORD_RUN_CENTRAL_REDIS_SMOKE=true`.
3. Add a web Vitest smoke test gated by `NUXT_RUN_CENTRAL_REDIS_SMOKE=true`.
4. Add a QA script that starts or reuses central `ms-redis`, runs both smoke tests, and reports one pass marker.

## Acceptance Criteria

- Contract fails before smoke assets exist and passes after implementation.
- Backend smoke performs a real Redis write/read against the central endpoint.
- Web smoke proves two CSP limiter instances share the same central Redis counter.
- QA script passes against the central `ms-redis` endpoint.
- T148-touched files pass `git diff --check`.
