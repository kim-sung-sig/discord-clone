# T146 Kafka Gateway Event Bus Adapter Plan

Date: 2026-05-20
Slice: T146 Kafka Gateway Event Bus Adapter

## Objective

Use the prepared central Kafka broker for Gateway event fanout when the `kafka` Spring profile is active.

## Current State

- Local central resource profile now includes `ms-kafka` on `127.0.0.1:29092`.
- Gateway currently has in-memory and Redis event bus implementations.
- `redis,kafka` profile combinations could create more than one `GatewayEventBus` unless profiles are made exclusive.

## Scope

1. Add Spring Kafka dependency.
2. Add a `KafkaGatewayEventBus` profile adapter.
3. Publish sanitized Gateway events to a Kafka topic.
4. Consume remote Kafka events and notify local listeners.
5. Avoid duplicate local delivery from the publishing node.
6. Make Kafka bus exclusive over Redis/in-memory buses when `kafka` is active.

## Acceptance Criteria

- Kafka publish writes sanitized payloads to `discord.gateway.events`.
- Remote Kafka messages notify Gateway listeners.
- Same-node Kafka messages are ignored after local publish delivery.
- `kafka` profile owns the Gateway event bus when enabled.
- Focused gateway tests, backend tests, and diff checks pass.
