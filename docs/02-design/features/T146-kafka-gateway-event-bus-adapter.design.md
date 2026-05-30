# T146 Kafka Gateway Event Bus Adapter Design

Date: 2026-05-20
Slice: T146 Kafka Gateway Event Bus Adapter

## Profile Selection

- `kafka`: uses `KafkaGatewayEventBus`.
- `redis & !kafka`: uses `RedisGatewayEventBus`.
- `!redis & !kafka`: uses `InMemoryGatewayEventBus`.

Kafka wins when `redis,kafka` are both active because T144 made Kafka the intended central fanout broker.

## Topic Contract

Gateway events are published to:

`{discord.kafka.topic-prefix}.gateway.events`

Default topic:

`discord.gateway.events`

The Kafka key is the guild id, preserving per-guild ordering where the broker partitioning supports it.

## Envelope

Kafka messages include:

- source node id
- event id
- type
- guild id
- optional channel id
- sanitized payload
- created timestamp as ISO-8601 string

The consumer ignores messages from the same node to avoid duplicate local delivery after `publish()`.
