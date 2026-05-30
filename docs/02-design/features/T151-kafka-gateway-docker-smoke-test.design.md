# T151 Kafka Gateway Docker Smoke Test Design

Date: 2026-05-20
Slice: T151 Kafka Gateway Docker Smoke Test

## Design

The smoke is opt-in because it requires Docker and a reachable Kafka-compatible broker.

## JUnit Smoke

- Test: `CentralKafkaGatewayEventBusSmokeTest`
- Gate: `DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE=true`
- Broker default: `SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:29092`

The test creates a unique smoke topic prefix, publishes through node-a `KafkaGatewayEventBus`, consumes the broker record with a real `KafkaConsumer`, forwards the payload into node-b `KafkaGatewayEventBus.handleMessage`, and asserts node-b listener delivery.

## QA Script

`qa/central-kafka-gateway-smoke.ps1` first checks whether a standalone `ms-kafka` container is already exposing `127.0.0.1:29092`. If not, it starts the Compose `ms-kafka` service and waits for the TCP port.

The Gradle test is run with `--rerun-tasks` so the smoke produces fresh evidence.
