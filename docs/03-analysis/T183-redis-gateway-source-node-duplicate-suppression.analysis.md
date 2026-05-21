# T183 Redis Gateway Source-node Duplicate Suppression Analysis

Date: 2026-05-21

## Finding

`RedisGatewayEventBus.publish` notifies local listeners immediately after appending the stream record. Because T61 made consumer groups node-scoped for broadcast fanout, the publishing node can also read its own stream record later. Without an explicit source-node policy, the same node can deliver the same event twice to local listeners.

## Decision

Suppress only successfully decoded records whose `sourceNodeId` equals the current node id. This keeps corrupt records visible to the existing DLQ and preserves cross-node delivery.

## Verification Evidence

- RED: `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest.pollAcknowledgesOwnSourceRecordWithoutDuplicateLocalDelivery`
  - Failed first at `RedisGatewayEventBusTest.java:160` because the own-source record was delivered a second time.
- GREEN: same focused test passed after implementation.
- Regression: `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest`
- Real Redis: `powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1`
- Style: `.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest`
