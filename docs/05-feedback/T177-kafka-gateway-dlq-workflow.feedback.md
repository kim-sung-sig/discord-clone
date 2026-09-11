---
slug: T177-kafka-gateway-dlq-workflow
ticket: T177
phase: feedback
hub: "[[T177-kafka-gateway-dlq-workflow]]"
---
> 🧭 [[T177-kafka-gateway-dlq-workflow]] · PDCA: [[T177-kafka-gateway-dlq-workflow.plan]] → [[T177-kafka-gateway-dlq-workflow.design]] → [[T177-kafka-gateway-dlq-workflow.analysis]] → [[T177-kafka-gateway-dlq-workflow.report]] → **feedback**

# T177 Kafka Gateway DLQ Workflow Feedback

Date: 2026-05-21
Slice: T177 Kafka Gateway DLQ retention, alert, and replay workflow

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T178 Kafka Gateway DLQ metrics and alert integration | P2 | The runbook defines thresholds, but production still needs automated DLQ count collection and alert routing. |

## Notes

- Replay tooling should wait until event idempotency and operator approval boundaries are clearer.
