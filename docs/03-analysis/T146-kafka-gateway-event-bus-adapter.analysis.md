# T146 Kafka Gateway Event Bus Adapter Analysis

Date: 2026-05-20
Slice: T146 Kafka Gateway Event Bus Adapter

## Implementation Notes

- Added failing Kafka gateway tests before implementation.
- Added `spring-kafka` dependency.
- Added `KafkaGatewayEventBus` with `@Profile("kafka")`.
- Updated Redis and in-memory bus profiles so Kafka is the single bus when enabled.
- Serialized timestamps as strings to avoid depending on custom ObjectMapper modules in the adapter contract.

## Feature Impact

- Gateway fanout can now move toward central Kafka-backed cross-node delivery.
- Existing local in-memory fanout remains available without `redis` or `kafka`.
- Existing Redis fanout remains available when `redis` is active and `kafka` is not.

## Remaining Gaps

- No Docker-backed Kafka integration test exists yet.
- Kafka listener error/DLQ/backpressure policy remains unimplemented.
- Gateway session resume remains separate from event bus delivery.
