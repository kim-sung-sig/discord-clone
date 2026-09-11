---
slug: T152-kafka-gateway-consumer-failure-policy
ticket: T152
phase: feedback
hub: "[[T152-kafka-gateway-consumer-failure-policy]]"
---
> 🧭 [[T152-kafka-gateway-consumer-failure-policy]] · PDCA: [[T152-kafka-gateway-consumer-failure-policy.plan]] → [[T152-kafka-gateway-consumer-failure-policy.design]] → [[T152-kafka-gateway-consumer-failure-policy.analysis]] → [[T152-kafka-gateway-consumer-failure-policy.report]] → **feedback**

# T152 Kafka Gateway Consumer Failure Policy Feedback

Date: 2026-05-21
Slice: T152 Kafka Gateway Consumer Failure Policy

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T177 Kafka Gateway DLQ retention, alert, and replay workflow | P2 | Dead-letter records now exist, but production operation needs retention, alert thresholds, and a reviewed drain/replay path. |

## Notes

- Retry should be revisited together with idempotency and replay controls so duplicate realtime events do not reach clients unexpectedly.
