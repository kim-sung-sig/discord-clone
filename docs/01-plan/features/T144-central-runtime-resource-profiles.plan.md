# T144 Central Runtime Resource Profiles Plan

Date: 2026-05-19
Slice: T144 Central Runtime Resource Profiles

## Loop Output

Plan reviewed > implementation plan prepared > implementation in progress > review in progress > review complete > threshold decision > next plan or improvement loop

## Plan Review

The user provided the active local Docker resource map: `postgres-source` on `15432`, `ms-redis` on `16379`, and `ms-kafka` on `29092`. Existing runtime resources already had Postgres and Redis profiles, but Redis still defaulted to `6379`, Flyway settings were hard-coded in Java, Kafka had no Spring resource profile, and `.env.example` still documented in-memory CSP rate limiting as the default.

## Implementation Plan

Major topics:

1. Resource contract
   - Add a QA contract proving central Postgres/Flyway/Redis/Kafka settings are present.

2. Spring resources
   - Keep Postgres default on `127.0.0.1:15432`.
   - Move Flyway locations and baseline flags into `application-postgres.yml`.
   - Update Redis default host port to `16379`.
   - Add `application-kafka.yml` for the prepared Kafka broker on `29092`.

3. Runtime examples
   - Update `.env.example` to prefer `postgres,redis,kafka`.
   - Route Nuxt CSP rate limiting to Redis by default.

4. Documentation
   - Add a central runtime resource reference.
   - Register follow-up tasks for removing non-test in-memory adapters and adding Kafka-backed fanout.

## Out of Scope

- Removing every in-memory adapter in this slice.
- Implementing Kafka event producers/consumers.
- Replacing Docker Compose with the Docker Desktop resource group.

## Acceptance Criteria

- Central runtime resource contract passes.
- Postgres/Flyway Spring bootstrap test passes.
- Resource files and `.env.example` describe the provided Docker endpoints.
- Follow-up tasks capture remaining in-memory replacement work.
