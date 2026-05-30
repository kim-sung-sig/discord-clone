# T110 Node SQLite Runtime Compatibility Gate Plan

Date: 2026-05-21

## Goal

Lock the CSP telemetry SQLite path as legacy-only after the Postgres migration and prevent accidental reintroduction of `node:sqlite` or `NUXT_CSP_TELEMETRY_SQLITE_PATH` into active runtime configuration.

## Scope

- Strengthen the SQLite legacy cleanup contract.
- Document the Node runtime compatibility boundary.
- Verify active CSP telemetry code stays Postgres-or-memory only.
- Re-rank the residual queue after deciding whether T115 still needs implementation.

## Non-Goals

- Reintroducing SQLite telemetry.
- Adding a SQLite maintenance CLI.
- Importing old SQLite telemetry into Postgres.

## Acceptance Criteria

- The contract fails if the runbook omits the Node compatibility boundary.
- The contract fails if active CSP telemetry code references `node:sqlite`, `SqliteCspTelemetryStore`, or `NUXT_CSP_TELEMETRY_SQLITE_PATH`.
- `.env.example` does not advertise the legacy SQLite path.
- The queue records T110 completion and T115 disposition.
