# T61 Redis Multi-node Gateway Fanout Smoke Design

Date: 2026-05-21

## Design

Redis Streams consumer groups distribute each entry to one consumer inside a group. Gateway fanout needs every node to
see the same event, so the runtime now builds the effective group as:

```text
<consumer-group-prefix>:<node-id>
```

Example:

```text
discord-gateway:node-a
discord-gateway:node-b
```

Each gateway node still uses its node id as the Redis consumer name inside its own group.

## Smoke Coverage

`CentralRedisGatewayFanoutSmokeTest` is gated by:

```text
DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE=true
```

It verifies:

- two Redis Gateway event bus instances both receive the same channel stream event;
- two Gateway services using Redis fanout only deliver visible channel events to the session user;
- the smoke uses a test-only group prefix, `discord-gateway-smoke`.

`qa/central-redis-smoke.ps1` now runs this test next to the existing backend Redis connectivity and web CSP Redis smoke.

## Security

The smoke writes only synthetic UUIDs and synthetic message content. It does not log Redis passwords, tokens, signed URLs,
or raw session credentials. Redis CLI readiness continues to pass the password through `REDISCLI_AUTH`.

## Residual

Cross-node RESUME with a session created on a different gateway node still needs subscription reconciliation on reconnect.
That remains tracked as T62.
