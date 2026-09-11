---
slug: T167-csp-alert-incident-lifecycle-history
ticket: T167
phase: feedback
hub: "[[T167-csp-alert-incident-lifecycle-history]]"
---
> 🧭 [[T167-csp-alert-incident-lifecycle-history]] · PDCA: [[T167-csp-alert-incident-lifecycle-history.plan]] → [[T167-csp-alert-incident-lifecycle-history.design]] → [[T167-csp-alert-incident-lifecycle-history.analysis]] → [[T167-csp-alert-incident-lifecycle-history.report]] → **feedback**

# T167 CSP Alert Incident Lifecycle History Feedback

Date: 2026-05-22
Slice: T167 CSP Alert Incident Lifecycle History

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T168 Privacy-reviewed subject distribution summary | P3 | Operators may need aggregate subject distribution, but it requires privacy review before exposing more identifiers. |
| T170 Operator token audit review UI | P3 | Operator token audit rows exist but are not yet reviewable from the dashboard. |

## Notes

- T167 keeps CSP incident lifecycle events separate from raw telemetry and threshold state transitions.
- Future assignment/status workflows can extend the event store without changing the current acknowledgement state contract.
