---
slug: T129-ephemeral-operator-token-flow
ticket: T129
phase: feedback
hub: "[[T129-ephemeral-operator-token-flow]]"
---
> 🧭 [[T129-ephemeral-operator-token-flow]] · PDCA: [[T129-ephemeral-operator-token-flow.plan]] → [[T129-ephemeral-operator-token-flow.design]] → [[T129-ephemeral-operator-token-flow.analysis]] → [[T129-ephemeral-operator-token-flow.report]] → **feedback**

# T129 Ephemeral Operator Token Flow Feedback

Date: 2026-05-21
Slice: T129 Ephemeral Operator Token Flow

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T169 Durable operator token store | P2 | The default token store is in-memory; production multi-node deployments should persist issued token hashes and audit rows centrally. |
| T170 Operator token audit review UI | P3 | Audit entries exist in the store contract but are not yet reviewable from the dashboard. |

## Notes

- T129 closes the immediate long-lived direct operator token gap.
