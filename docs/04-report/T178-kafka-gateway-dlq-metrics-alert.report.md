# T178 Kafka Gateway DLQ Metrics And Alert Report

Date: 2026-05-21
Slice: T178 Kafka Gateway DLQ metrics and alert integration

## Completed

- Added Micrometer counter `discord.gateway.kafka.dlq.records` tagged by DLQ reason.
- Added in-process DLQ metrics snapshot for total, reason counts, and threshold alert state.
- Wired alert threshold through `discord.kafka.gateway-dlq-alert-threshold`.
- Updated tests to verify metrics, alert state, and secret-safe alert text.
- Updated the Kafka Gateway DLQ runbook and contract with the metric name.

## Verification

- RED observed first:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` failed at compile time because the metrics constructor and `deadLetterMetrics()` did not exist.
  - `pwsh qa/kafka-gateway-dlq-runbook.contract.ps1` failed after adding the metric requirement because the runbook did not document it yet.
- GREEN after implementation:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest --tests com.example.discord.gateway.RedisGatewayEventBusTest --tests com.example.discord.gateway.CentralKafkaGatewayEventBusSmokeTest` passed.
  - `.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest` passed.
  - `pwsh qa/kafka-gateway-dlq-runbook.contract.ps1` passed.
  - `pwsh qa/central-runtime-resources.contract.ps1` passed.

## Notes

- External alert routing is now a monitoring deployment concern rather than an application code gap.
