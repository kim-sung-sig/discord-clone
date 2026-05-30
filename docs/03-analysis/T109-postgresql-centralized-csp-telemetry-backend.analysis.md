# T109 PostgreSQL Centralized CSP Telemetry Backend Analysis

Date: 2026-05-19
Slice: T109 PostgreSQL Centralized CSP Telemetry Backend

## Analysis

SQLite solved single-node durability but did not support centralized telemetry across multiple Nuxt instances. Moving CSP dashboard telemetry to PostgreSQL gives every frontend server a shared persistence point and removes the Node experimental `node:sqlite` runtime warning from this path.

The store remains behind the existing `CspTelemetryStore` interface. Because Postgres clients are asynchronous, the handler and dashboard paths now support async store operations while keeping in-memory behavior available for local runs without a configured Postgres URL.

## Trade-Offs

- The Nuxt server now depends on a Postgres client package for centralized telemetry.
- Schema creation happens lazily on first telemetry operation rather than through a separate migration runner.
- Tests that hit real Postgres are gated to avoid requiring Docker for every local test run.

## Security Notes

- Raw CSP report payloads are still not stored.
- Stored URLs remain origin-only or safe literals.
- User agent is still stored only as a SHA-256 hash.
- The Postgres URL must be provided through environment configuration, not hard-coded production secrets.

## Residual Risk

Production should eventually move table creation into an explicit migration step and add operational monitoring for Postgres connection failures.
