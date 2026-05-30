# T178 Kafka Gateway DLQ Metrics And Alert Feedback

Date: 2026-05-21
Slice: T178 Kafka Gateway DLQ metrics and alert integration

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T179 Kafka DLQ monitoring rule deployment | P3 | Runtime metrics exist, but Prometheus/Grafana/PagerDuty rule deployment is environment-specific and still needs production ownership. |

## Notes

- Keep alert rules aligned with the runbook threshold and avoid adding high-cardinality labels.
