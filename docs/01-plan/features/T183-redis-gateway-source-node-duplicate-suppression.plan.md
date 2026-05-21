# T183 Redis Gateway Source-node Duplicate Suppression Plan

Date: 2026-05-21

## Goal

Prevent same-node duplicate Gateway listener delivery when a node publishes locally, immediately notifies local listeners, and later reads its own Redis stream record.

## Scope

- Use existing Redis stream `sourceNodeId` metadata.
- Keep cross-node fanout broadcast behavior from T61/T62 intact.
- ACK valid own-source records after decoding, but do not re-notify listeners.
- Preserve malformed record DLQ handling before duplicate suppression.

## TDD Plan

1. Add a failing unit test proving a local publish is delivered once even if the same `sourceNodeId` record is read from Redis.
2. Implement the minimum source-node check in `RedisGatewayEventBus`.
3. Run focused Redis Gateway unit tests, real central Redis smoke, and Checkstyle.
