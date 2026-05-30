# T61 Redis Multi-node Gateway Fanout Smoke Analysis

Date: 2026-05-21

## Findings

| Finding | Result |
| --- | --- |
| Single Redis consumer group is wrong for Gateway fanout | A shared group load-balances entries between nodes, so only one node may receive a channel event. |
| Node-scoped consumer groups preserve fanout | Prefix plus node id lets each node consume the same retained stream entry independently. |
| Hidden channel filtering remains a service boundary | The real Redis service smoke confirms nodeB only receives the visible channel event. |
| Full cross-node RESUME still needs subscription reconciliation | A node that did not identify the session may not have registered stream subscriptions yet. T62 remains open. |

## RED Evidence

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
```

Failed first because the implementation still created and ACKed the group `discord-gateway` instead of
`discord-gateway:node-a`.

## GREEN Evidence

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.contract.ps1
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest
git diff --check
```

All passed after node-scoped consumer group behavior and the central Redis smoke integration were added.
