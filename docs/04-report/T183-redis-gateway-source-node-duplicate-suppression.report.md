# T183 Redis Gateway Source-node Duplicate Suppression Report

Date: 2026-05-21

## Result

Completed. Redis Gateway now suppresses same-node duplicate listener delivery for successfully decoded stream records whose `sourceNodeId` matches the current node id. The record is still acknowledged, so pending recovery does not loop on already locally delivered publishes.

## Changed Files

- `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java`
- `backend/boot/src/test/java/com/example/discord/gateway/RedisGatewayEventBusTest.java`

## Verification

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest.pollAcknowledgesOwnSourceRecordWithoutDuplicateLocalDelivery
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest
```

All checks passed after the RED failure was observed.

## Security Review

No raw payload or secret material was added to logs, metrics, docs, or DLQ records. Suppression happens after decode, so malformed records still flow to the secret-safe DLQ.
