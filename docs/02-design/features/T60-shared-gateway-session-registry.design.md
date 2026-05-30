# T60 Shared Gateway Session Registry And Cross-node RESUME Design

Date: 2026-05-21

## Design

`InMemoryGatewayService` now depends on a `GatewaySessionRegistry` port instead of owning an internal session map directly.

Implementations:

- `InMemoryGatewaySessionRegistry`: default local/test registry.
- `RedisGatewaySessionRegistry`: `redis` profile registry storing JSON session metadata in a Redis hash.

## Session Metadata

The shared registry stores:

- `sessionId`
- `userId`
- `guildIds`
- `lastAcknowledgedAt`
- `closed`
- `lastDeliveredSequence`

It does not store access tokens, refresh tokens, LiveKit tokens, signed URLs, request headers, or passwords.

## Sequence Handling

When a node reads shared session state created on another node, its local sequence counter must not collide with the session's last delivered sequence. Before creating control events or appending bus events, the service raises its sequence floor above the highest known registry sequence.

## Redis Key

The Redis hash key is configurable:

```text
discord.gateway.session-registry-key
DISCORD_GATEWAY_SESSION_REGISTRY_KEY
```
