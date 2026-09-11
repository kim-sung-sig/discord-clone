---
slug: T159-compose-health-diagnostic-failure-smoke
ticket: T159
phase: feedback
hub: "[[T159-compose-health-diagnostic-failure-smoke]]"
---
> 🧭 [[T159-compose-health-diagnostic-failure-smoke]] · PDCA: [[T159-compose-health-diagnostic-failure-smoke.plan]] → [[T159-compose-health-diagnostic-failure-smoke.design]] → [[T159-compose-health-diagnostic-failure-smoke.analysis]] → [[T159-compose-health-diagnostic-failure-smoke.report]] → **feedback**

# T159 Compose Health Diagnostic Failure Smoke Feedback

Date: 2026-05-20
Slice: T159 Compose Health Diagnostic Failure Smoke

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T161 Redis CLI Secret Handling In QA Health Checks | P2 | Normal compose health verification prints the Redis CLI warning for `-a` password usage; QA scripts should avoid command-line secret patterns where practical. |

## Notes

- T159 closes the controlled failure proof for central Compose diagnostics.
