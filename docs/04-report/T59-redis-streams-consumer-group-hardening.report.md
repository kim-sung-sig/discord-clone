# T59 Redis Streams Consumer-group Hardening Report

Date: 2026-05-21

## Result

Completed.

## Changes

- `RedisGatewayEventBus` now uses Redis consumer groups.
- Follow-up T61 corrected the effective group to `<configured-prefix>:<node-id>` so Redis Streams fanout is broadcast
  across gateway nodes instead of load-balanced across nodes in one group.
- Pending records are read before new records.
- Records are acknowledged after decode handling.
- Streams are trimmed after publish using `discord.gateway.redis-stream-max-length`.
- Aggregate stream metrics were added for processing, ACK, decode failure, read failure, and trim counts.
- Redis stream consumer group and retention settings were added to `application-redis.yml` and `.env.example`.

## Verification

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
```

Passed.
