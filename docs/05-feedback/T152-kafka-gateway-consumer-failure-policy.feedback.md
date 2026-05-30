# T152 Kafka Gateway Consumer Failure Policy Feedback

Date: 2026-05-21
Slice: T152 Kafka Gateway Consumer Failure Policy

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T177 Kafka Gateway DLQ retention, alert, and replay workflow | P2 | Dead-letter records now exist, but production operation needs retention, alert thresholds, and a reviewed drain/replay path. |

## Notes

- Retry should be revisited together with idempotency and replay controls so duplicate realtime events do not reach clients unexpectedly.
