# T152 Kafka Gateway Consumer Failure Policy Report

Date: 2026-05-21
Slice: T152 Kafka Gateway Consumer Failure Policy

## Completed

- Added Kafka Gateway dead-letter topic publication for consumer failures.
- Added malformed message handling with `MALFORMED_MESSAGE`.
- Added invalid envelope handling with `INVALID_ENVELOPE`.
- Added listener failure handling with `LISTENER_FAILURE`.
- Added secret-safe DLQ payload metadata with message size and SHA-256 hash prefix.
- Added unit coverage proving malformed payloads and listener exceptions do not leak raw secrets or event payload content.

## Verification

- RED observed first:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` failed because malformed messages were not dead-lettered and listener failures escaped.
- GREEN after implementation:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest --tests com.example.discord.gateway.RedisGatewayEventBusTest --tests com.example.discord.gateway.CentralKafkaGatewayEventBusSmokeTest` passed.
  - `.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest` passed.

## Notes

- The policy is intentionally skip-and-dead-letter, not automatic retry, until a DLQ drain/replay runbook exists.
