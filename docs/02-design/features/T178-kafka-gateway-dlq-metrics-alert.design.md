---
slug: T178-kafka-gateway-dlq-metrics-alert
ticket: T178
phase: design
hub: "[[T178-kafka-gateway-dlq-metrics-alert]]"
---
> 🧭 [[T178-kafka-gateway-dlq-metrics-alert]] · PDCA: [[T178-kafka-gateway-dlq-metrics-alert.plan]] → **design** → [[T178-kafka-gateway-dlq-metrics-alert.analysis]] → [[T178-kafka-gateway-dlq-metrics-alert.report]] → [[T178-kafka-gateway-dlq-metrics-alert.feedback]]

# T178 Kafka Gateway DLQ Metrics And Alert Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T178 Kafka Gateway DLQ metrics and alert integration

## Design

`KafkaGatewayEventBus` records DLQ metrics whenever it publishes a dead-letter record.

| Signal | Shape |
| --- | --- |
| Micrometer counter | `discord.gateway.kafka.dlq.records{reason="<reason>"}` |
| Snapshot total | `deadLetterMetrics().total()` |
| Snapshot reason map | `deadLetterMetrics().byReason()` |
| Alert | active when total count is greater than or equal to configured threshold |

Default threshold remains `1` from `application-kafka.yml` and `.env.example`.

## Security Review

- Metric tags use controlled reason values only.
- Alert text includes only count and threshold.
- No raw Kafka message, payload value, token, signed URL, or exception text is stored in metrics or alert state.
