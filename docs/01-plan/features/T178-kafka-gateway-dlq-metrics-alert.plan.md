# T178 Kafka Gateway DLQ Metrics And Alert Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T178 Kafka Gateway DLQ metrics and alert integration

## Executive Summary

| View | Content |
| --- | --- |
| Problem | The DLQ workflow defined alert thresholds, but the runtime did not expose a metric or threshold evaluation for DLQ records. |
| Solution | Add reason-tagged Micrometer counters and a secret-safe in-process metrics snapshot with threshold alert state. |
| Operator Effect | Production monitoring can alert on DLQ activity without reading raw Kafka payloads. |
| Core Value | Kafka Gateway DLQ records become observable and actionable through standard backend metrics. |

## Scope

- Increment `discord.gateway.kafka.dlq.records` with a `reason` tag.
- Track total and reason-level DLQ counts in the Kafka Gateway event bus.
- Evaluate alert active state using `discord.kafka.gateway-dlq-alert-threshold`.
- Keep alert reason text free of raw payloads, tokens, signed URLs, and exception messages.
- Update the DLQ runbook with the metric name.

## Out of Scope

- Prometheus/Grafana/PagerDuty rule deployment.
- DLQ replay UI or drain automation.
- Persisting DLQ metrics across process restarts.

## Success Criteria

- RED test fails before metrics exist.
- Focused Kafka Gateway tests pass after implementation.
- Related Gateway tests and checkstyle pass.
- Runbook contract requires the metric name.
