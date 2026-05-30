# T130 CSP Retention Metrics Breakdown UI Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T130 CSP Retention Metrics Breakdown UI

## Findings

- T114 already added `discardedByAge` and `discardedByMaxEntries` to the store and dashboard payload.
- `/security` only rendered `discardedTotal`, so operators could not identify which retention pressure was active.
- The change can stay frontend-only because the API contract already carries the required breakdown.

## Risk Review

| Risk | Control |
| --- | --- |
| Leaking discarded CSP report details | Render aggregate counters only. |
| Breaking older dashboard payloads | Use optional chaining and `0` fallback for each count. |
| Mobile summary card overflow | Use a compact two-row definition list with small stable text. |
| Regression in operator security UI | Keep existing token and guard behavior untouched. |

## Remaining Gaps

- T107 can later add trends after the retention breakdown is visible.
- Retention policy tuning is still operational; this slice only improves visibility.
