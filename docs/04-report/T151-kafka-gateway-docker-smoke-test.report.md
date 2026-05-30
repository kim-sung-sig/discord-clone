# T151 Kafka Gateway Docker Smoke Test Report

Date: 2026-05-20
Slice: T151 Kafka Gateway Docker Smoke Test

## Completed

- Added a central Kafka Gateway smoke contract.
- Added an opt-in broker-backed Gateway smoke test.
- Added a QA script that reuses existing central `ms-kafka` or starts Compose `ms-kafka`.
- Verified node-a publish through Kafka and node-b listener delivery.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.contract.ps1` failed because the smoke script was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.contract.ps1` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.CentralKafkaGatewayEventBusSmokeTest` passed with the env gate disabled.
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.ps1` passed with Gradle tasks executed and `CENTRAL_KAFKA_GATEWAY_SMOKE_PASS`.

## Notes

- The smoke currently validates broker transport through a real producer and consumer, then invokes node-b message handling directly.
- This keeps the check narrow and avoids starting a full Spring application context.
