---
slug: T126-csp-alert-persistence
ticket: T126
phase: analysis
hub: "[[T126-csp-alert-persistence]]"
---
> 🧭 [[T126-csp-alert-persistence]] · PDCA: [[T126-csp-alert-persistence.plan]] → [[T126-csp-alert-persistence.design]] → **analysis** → [[T126-csp-alert-persistence.report]] → [[T126-csp-alert-persistence.feedback]]

# T126 CSP Alert Persistence Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T126 CSP Alert Persistence

## Findings

| Finding | Result |
| --- | --- |
| Alert state was request-local | Added alert transition storage and dashboard history. |
| Durable telemetry already had Postgres patterns | Reused the existing lazy Postgres store pattern for `csp_alert_transitions`. |
| Transition data can stay aggregate-only | No raw CSP payload, subject, or IP data is needed to review active/clear state. |

## Security Review

The persistence boundary stores only aggregate alert state and sanitized threshold reasons. This preserves the existing privacy posture while making operational alert state reviewable after the detection request.

## Residual Risk

- Lazy table creation should become explicit migration ownership; T135 now includes `csp_alert_transitions`.
- Operator acknowledgement remains T127.
