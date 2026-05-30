# T146 Kafka Gateway Event Bus Adapter Report

Date: 2026-05-20
Slice: T146 Kafka Gateway Event Bus Adapter

## Completed

- Added Spring Kafka dependency to the backend boot app.
- Added Kafka-backed Gateway event bus adapter.
- Added sanitized event envelope publishing.
- Added remote message consumption with same-node duplicate suppression.
- Made Kafka bus exclusive over Redis and in-memory bus profiles.

## Verification

- RED observed first:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` failed because Spring Kafka and `KafkaGatewayEventBus` did not exist.
- GREEN after implementation:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` passed.
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest --tests com.example.discord.gateway.RedisGatewayEventBusTest` passed.
  - `./gradlew.bat :backend:boot:test` passed.
  - `git diff --check` passed for T146-touched files.

## Notes

- Gradle still emits the known deprecation warning and JVM class sharing warning; tests exit successfully.
