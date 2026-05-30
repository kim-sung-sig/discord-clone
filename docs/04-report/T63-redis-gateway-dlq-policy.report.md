# T63 Redis Gateway DLQ Policy Report

Date: 2026-05-21

## Result

Completed.

## Changes

- Added Redis Gateway DLQ stream metadata writes for malformed records and listener failures.
- Added `RedisGatewayDeadLetterMetrics` with total, reason counts, and threshold alert state.
- Added `DISCORD_GATEWAY_REDIS_DLQ_STREAM` and `DISCORD_GATEWAY_REDIS_DLQ_ALERT_THRESHOLD`.
- Added `docs/runbooks/redis-gateway-dlq-runbook.md`.
- Added `qa/redis-gateway-dlq-runbook.contract.ps1` and wired it into CI workflow contracts.
- Kept central Redis smoke passing with the T61 fanout coverage.

## Verification

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest --tests com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
powershell -ExecutionPolicy Bypass -File qa\redis-gateway-dlq-runbook.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\ci-workflow.contract.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest
git diff --check
```

Passed. `git diff --check` reported CRLF conversion warnings only.

## Security Review

DLQ records are metadata-only and use `messageSha256Prefix` instead of raw stream values. The runbook explicitly forbids
copying raw Redis stream payloads, access tokens, signed URLs, Authorization headers, cookies, and private message bodies
into tickets or replay material.
