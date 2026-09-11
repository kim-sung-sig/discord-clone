---
slug: T151-kafka-gateway-docker-smoke-test
ticket: T151
phase: plan
hub: "[[T151-kafka-gateway-docker-smoke-test]]"
---
> 🧭 [[T151-kafka-gateway-docker-smoke-test]] · PDCA: **plan** → [[T151-kafka-gateway-docker-smoke-test.design]] → [[T151-kafka-gateway-docker-smoke-test.analysis]] → [[T151-kafka-gateway-docker-smoke-test.report]] → [[T151-kafka-gateway-docker-smoke-test.feedback]]

# T151 Kafka Gateway Docker Smoke Test Plan

Date: 2026-05-20
Slice: T151 Kafka Gateway Docker Smoke Test

## Objective

Prove the Kafka Gateway event bus can send a Gateway event through the central Kafka broker and deliver it to a second node.

## Current State

- T146 added `KafkaGatewayEventBus` and unit coverage with a mocked `KafkaTemplate`.
- Docker Compose now exposes central `ms-kafka` on `127.0.0.1:29092`.
- No smoke test used the real broker path.

## Scope

1. Add a contract for the Kafka Gateway smoke assets.
2. Add an opt-in JUnit smoke test gated by `DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE=true`.
3. Add a QA script that starts or reuses central `ms-kafka`.
4. Verify a node-a publish can be consumed from Kafka and delivered into node-b listeners.

## Acceptance Criteria

- Contract fails before smoke assets exist and passes after implementation.
- Smoke script uses central `ms-kafka` and `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
- Real broker smoke passes against `127.0.0.1:29092`.
- T151-touched files pass `git diff --check`.
