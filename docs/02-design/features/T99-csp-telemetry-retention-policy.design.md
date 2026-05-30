# T99 CSP Telemetry Retention Policy Design

## Policy

`CspTelemetryRetentionPolicy` contains:

- `maxEntries`: maximum number of stored normalized reports.
- `maxAgeMs`: optional maximum record age in milliseconds.

## Defaults

- `maxEntries`: `1000`
- `maxAgeMs`: unset

This preserves the current in-memory count behavior and makes SQLite bounded by the same count default.

## Environment Variables

- `NUXT_CSP_TELEMETRY_RETENTION_MAX_ENTRIES`
- `NUXT_CSP_TELEMETRY_RETENTION_MAX_AGE_DAYS`

Invalid or empty values fall back to defaults.

## Cleanup Timing

Cleanup runs synchronously inside `record(report, receivedAt)` after a report is accepted and persisted. This keeps cleanup tied to write activity and avoids background timers in the Nuxt server runtime.

## SQLite Cleanup

SQLite retention executes two bounded deletes:

1. Delete rows older than `receivedAt - maxAgeMs`.
2. Delete rows outside the newest `maxEntries` ordered by `received_at DESC, id DESC`.

