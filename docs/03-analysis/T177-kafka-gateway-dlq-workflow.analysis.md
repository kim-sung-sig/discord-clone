# T177 Kafka Gateway DLQ Workflow Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T177 Kafka Gateway DLQ retention, alert, and replay workflow

## Findings

- T152 introduced secret-safe dead-letter records, but no operator process referenced the new topic.
- The existing Kafka profile exposed topic prefix and bootstrap configuration, but no DLQ policy defaults.
- A replay service would be premature because replay safety depends on idempotency and payload reconstruction rules.
- A runbook contract is sufficient for this slice and keeps the workflow from regressing.

## Risk Review

| Risk | Control |
| --- | --- |
| Operators copy raw Kafka payloads | Runbook explicitly prohibits copying raw payloads and secrets. |
| Unsafe replay duplicates client-visible events | Replay requires ticket approval, defect review, and idempotency/safety confirmation. |
| DLQ records accumulate silently | Alert threshold is greater than 0 in production. |
| Retention becomes an unbounded archive | Default retention is 168 hours with hold exceptions requiring owner and expiry. |

## Remaining Gaps

- Automated DLQ count metrics, alert integration, and replay tooling are still future work.
