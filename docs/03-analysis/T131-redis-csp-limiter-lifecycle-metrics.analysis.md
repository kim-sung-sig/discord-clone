# T131 Redis CSP Limiter Lifecycle Metrics Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T131 Redis CSP Limiter Lifecycle Metrics

## Findings

- T117 added close lifecycle cleanup, but no operator-visible metrics.
- T125 exposed rate-limited totals, and T128 exposed subject diagnostics, but Redis health/churn remained invisible.
- The Redis limiter already fails closed on Redis errors; counting that path gives operators a direct signal when CSP reports are being denied because Redis is unavailable.
- The dashboard API is already guarded, so adding aggregate lifecycle metrics there fits the existing security boundary.

## Risk Review

| Risk | Control |
| --- | --- |
| Redis credential leakage | Do not include URL, password, keys, or raw errors in metrics. |
| Raw subject/IP leakage | Lifecycle metrics are independent of subject values. |
| Dashboard payload drift | Add tests for payload and UI contracts. |
| Runtime retry lockout after failed connect | Clear failed client promises so a later consume can retry. |

## Remaining Gaps

- Lifecycle metrics are process-local and reset on restart.
- Alert thresholds for repeated fail-closed or reconnect churn are deferred to T172.
