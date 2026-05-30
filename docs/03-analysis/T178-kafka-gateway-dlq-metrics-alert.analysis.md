# T178 Kafka Gateway DLQ Metrics And Alert Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T178 Kafka Gateway DLQ metrics and alert integration

## Findings

- The project already uses Micrometer counters for operational signals.
- T152 centralized DLQ publication in `publishDeadLetter`, giving a narrow place to record metrics.
- A reason-tagged counter gives external monitoring enough data without requiring raw DLQ reads.
- An in-process metrics snapshot keeps threshold behavior unit-testable.

## Risk Review

| Risk | Control |
| --- | --- |
| Secret leakage through metric tags | Use fixed reason strings only. |
| Alert message leaks payload content | Alert reason includes only count and threshold. |
| Monitoring misses reason distribution | Counter is tagged by reason and snapshot includes reason counts. |
| Counter drift after restart | Acceptable for this slice; external monitoring owns long-term aggregation. |

## Remaining Gaps

- Production alert routing rules still need to be deployed in the chosen monitoring platform.
