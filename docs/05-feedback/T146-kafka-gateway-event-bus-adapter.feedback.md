# T146 Kafka Gateway Event Bus Adapter Feedback

Date: 2026-05-20
Slice: T146 Kafka Gateway Event Bus Adapter

## Improvement Tasks Captured

### T151 Kafka Gateway Docker Smoke Test

Add a Docker-backed smoke or integration test against `ms-kafka` that verifies two backend nodes can exchange Gateway events through the Kafka adapter.

### T152 Kafka Gateway Consumer Failure Policy

Define retry, dead-letter, and malformed-message handling for Kafka Gateway events before production use.
