# T63 Redis Gateway DLQ Policy Analysis

Date: 2026-05-21

## Findings

| Finding | Result |
| --- | --- |
| Malformed Redis records were only counted and ACKed | Added DLQ metadata records with `MALFORMED_RECORD`. |
| Listener failures could escape polling | Listener exceptions are isolated, dead-lettered, and polling continues. |
| DLQ storage needed a bound | DLQ stream uses the same trim policy as Gateway streams. |
| Raw payloads must not enter operator artifacts | Tests assert DLQ metadata excludes secret-like payload and exception content. |

## RED Evidence

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
```

Failed first because `RedisGatewayEventBus.deadLetterMetrics()` did not exist. A later RED check failed because the DLQ
stream was not trimmed.

## GREEN Evidence

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest
powershell -ExecutionPolicy Bypass -File qa\redis-gateway-dlq-runbook.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\ci-workflow.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest
git diff --check
```

Passed. `git diff --check` reported CRLF conversion warnings only.
