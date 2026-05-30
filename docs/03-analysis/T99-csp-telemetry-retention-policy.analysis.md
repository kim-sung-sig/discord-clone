# T99 CSP Telemetry Retention Policy Analysis

## Result

CSP telemetry now has a shared retention policy across in-memory and SQLite stores.

## Behavior

- `maxEntries` caps the newest retained telemetry records.
- `maxAgeMs` removes records older than the configured retention window.
- Cleanup runs after each accepted telemetry `record()`.
- `createDefaultCspTelemetryStore()` reads retention configuration from environment variables.

## Configuration

- `NUXT_CSP_TELEMETRY_RETENTION_MAX_ENTRIES`
- `NUXT_CSP_TELEMETRY_RETENTION_MAX_AGE_DAYS`

## Findings

- The previous in-memory count cap was preserved with a default of `1000`.
- SQLite is now count-bounded by the same default instead of growing indefinitely.
- Age retention is optional; leaving it empty preserves existing local inspection behavior while still keeping count bounded.

