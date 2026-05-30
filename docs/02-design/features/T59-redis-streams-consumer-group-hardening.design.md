# T59 Redis Streams Consumer-group Hardening Design

Date: 2026-05-21

## Design

`RedisGatewayEventBus` now uses a configured consumer group prefix and node id as the consumer name.

The effective Redis consumer group is:

```text
<discord.gateway.redis-stream-consumer-group>:<discord.gateway.node-id>
```

This keeps Redis Streams from load-balancing a single event across gateway nodes. Each gateway node owns a separate
consumer group and can therefore receive the same retained stream event for broadcast fanout.

Poll order per subscribed stream:

1. Ensure the consumer group exists from `0-0`.
2. Read pending entries for the current consumer using `0-0`.
3. Read new entries using `lastConsumed`.
4. Decode and notify listeners.
5. ACK every processed or discarded record.

## Metrics

The adapter exposes package-private testable counters:

- `processedTotal`
- `ackedTotal`
- `failedDecodeTotal`
- `readFailureTotal`
- `trimmedTotal`

These counters are aggregate only and do not include payload data.

## Configuration

```text
discord.gateway.redis-stream-consumer-group
discord.gateway.redis-stream-max-length
DISCORD_GATEWAY_REDIS_STREAM_CONSUMER_GROUP
DISCORD_GATEWAY_REDIS_STREAM_MAX_LENGTH
```

`discord.gateway.redis-stream-consumer-group` is a prefix. The runtime appends `:<node-id>` to create the effective
consumer group name.

## Security

Payload sanitization remains the publish boundary. Metrics and consumer group metadata must not include access tokens, refresh tokens, LiveKit JWTs, signed URLs, or request headers.
