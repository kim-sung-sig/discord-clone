---
slug: T114-csp-telemetry-retention-metrics
ticket: T114
phase: design
hub: "[[T114-csp-telemetry-retention-metrics]]"
---
> 🧭 [[T114-csp-telemetry-retention-metrics]] · PDCA: [[T114-csp-telemetry-retention-metrics.plan]] → **design** → [[T114-csp-telemetry-retention-metrics.analysis]] → [[T114-csp-telemetry-retention-metrics.report]] → [[T114-csp-telemetry-retention-metrics.feedback]]

# T114 CSP Telemetry Retention Metrics Design

## Store Contract

`CspTelemetryStore.retentionMetrics()` returns:

- `discardedTotal`
- `discardedByAge`
- `discardedByMaxEntries`

The values are aggregate counters and contain no CSP report payload data.

## In-memory Store

The in-memory store maintains process-local counters beside retained entries.

## SQLite Store

The SQLite store persists counters in `csp_telemetry_retention_metrics` so discard totals survive store recreation.

## Dashboard Contract

`CspTelemetryDashboard.retention` carries the aggregate metrics.

The `/security` page renders `retention.discardedTotal` as a summary card.
