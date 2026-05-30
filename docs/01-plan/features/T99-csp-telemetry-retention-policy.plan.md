# T99 CSP Telemetry Retention Policy Plan

## Objective

Bound CSP telemetry storage by age and count so the browser security dashboard remains operationally useful without unbounded local memory or SQLite growth.

## Current State

- In-memory telemetry has a count cap through `maxEntries`.
- SQLite telemetry persists accepted CSP reports indefinitely.
- No environment variable controls retention for either store.

## Scope

1. Add a shared retention policy shape for CSP telemetry stores.
2. Apply retention on each accepted telemetry record.
3. Support max-entry retention for both in-memory and SQLite stores.
4. Support max-age retention for both in-memory and SQLite stores.
5. Wire retention defaults and environment variables into `createDefaultCspTelemetryStore`.
6. Document the policy and verification.

## Acceptance Criteria

- In-memory telemetry drops entries older than the configured age.
- SQLite telemetry drops rows older than the configured age.
- SQLite telemetry keeps only the newest configured number of rows.
- Defaults keep existing local behavior bounded without requiring configuration.
- Focused tests, full web tests, build, and whitespace checks pass.

