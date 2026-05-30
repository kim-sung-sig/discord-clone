# T63 Redis Gateway DLQ Policy Design

Date: 2026-05-21

## Design

`RedisGatewayEventBus` writes Redis Gateway failure metadata to:

```text
gateway:dead-letter
```

The stream name and alert threshold are configurable:

```text
discord.gateway.redis-dlq-stream
discord.gateway.redis-dlq-alert-threshold
DISCORD_GATEWAY_REDIS_DLQ_STREAM
DISCORD_GATEWAY_REDIS_DLQ_ALERT_THRESHOLD
```

The DLQ stream is trimmed through the existing `discord.gateway.redis-stream-max-length` policy to avoid unbounded
growth.

## Reasons

- `MALFORMED_RECORD`: the Redis record could not decode into a valid `GatewayBusEvent`.
- `LISTENER_FAILURE`: a local listener threw while processing a decoded Redis Gateway event.

## Security

DLQ records store metadata only. The raw Redis record, payload body, listener exception message, access tokens, refresh
tokens, LiveKit JWTs, signed URLs, request headers, cookies, and private message content are excluded. Operators inspect
`messageSha256Prefix` plus counts and service logs instead of copying raw payloads.
