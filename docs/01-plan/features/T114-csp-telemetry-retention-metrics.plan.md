# T114 CSP Telemetry Retention Metrics Plan

## Objective

Expose how many CSP telemetry records retention has discarded so operators can distinguish low current totals from retention pruning.

## Current State

- T99 enforces count and age retention for CSP telemetry stores.
- Dashboard summary shows current retained telemetry totals.
- Operators cannot see how many records were discarded by retention.

## Scope

1. Track retention discards by age.
2. Track retention discards by max-entry pruning.
3. Add retention metrics to the dashboard payload.
4. Render total discarded records in `/security`.
5. Keep metrics sanitized and aggregate-only.

## Acceptance Criteria

- In-memory store reports age and max-entry discard counts.
- SQLite store reports the same retention metric contract.
- Dashboard payload includes aggregate retention metrics.
- `/security` renders total records discarded by retention.
- Focused tests, related web tests, full web tests, build, and whitespace checks pass.
