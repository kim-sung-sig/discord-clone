# T114 CSP Telemetry Retention Metrics Analysis

## Implementation Notes

- Added tests for dashboard payload retention metrics and UI rendering before implementation.
- Added `CspTelemetryRetentionMetrics` to the CSP telemetry store contract.
- Counted both age-based and max-entry discards.
- Added a SQLite metrics table with upsert increments.
- Added a third dashboard summary card for discarded records.

## Risk Review

- Metrics are aggregate-only and do not expose document URLs, blocked URLs, user agents, or samples.
- Existing dashboard consumers remain tolerant on the UI side because the card uses optional access.
- Store implementations now share a required metrics method, which keeps dashboard aggregation simple.

## Remaining Gaps

- There is no UI breakdown for age vs max-entry discard causes.
- There is no operator action to reset or export retention counters.
