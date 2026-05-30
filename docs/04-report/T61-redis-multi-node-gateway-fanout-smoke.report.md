# T61 Redis Multi-node Gateway Fanout Smoke Report

Date: 2026-05-21

## Result

Completed.

## Changes

- Added `CentralRedisGatewayFanoutSmokeTest`.
- Updated `qa/central-redis-smoke.ps1` to run the Gateway fanout smoke under
  `DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE=true`.
- Updated `qa/central-redis-smoke.contract.ps1` to require the new smoke coverage.
- Changed `RedisGatewayEventBus` to build effective consumer groups as `<prefix>:<node-id>`.
- Updated T59 docs to clarify that the configured consumer group value is a prefix.

## Verification

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.contract.ps1
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest
git diff --check
```

Passed. `git diff --check` reported CRLF conversion warnings only.

## Security Review

The smoke uses synthetic data and keeps Redis password handling in `REDISCLI_AUTH`. Metrics remain aggregate-only and do
not expose stream payloads, tokens, request headers, LiveKit JWTs, or signed URLs.
