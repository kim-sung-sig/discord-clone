# T135 Explicit CSP Telemetry Postgres Migration Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T135 Explicit CSP Telemetry Postgres Migration

## Executive Summary

| View | Content |
| --- | --- |
| Problem | CSP telemetry Postgres tables were created lazily by Nuxt stores, leaving schema ownership implicit. |
| Solution | Add a reviewed SQL migration and runbook for CSP telemetry, rate-limit telemetry, retention metrics, and alert transitions. |
| Operator Effect | Operators can apply and verify CSP telemetry schema before enabling durable telemetry. |
| Core Value | Central security telemetry storage becomes an explicit deployment artifact instead of an application side effect. |

## Scope

- Add `apps/web/server/database/migrations/001_csp_telemetry_postgres.sql`.
- Add `docs/runbooks/csp-telemetry-postgres-migration.md`.
- Cover `csp_telemetry`, `csp_telemetry_retention_metrics`, `csp_rate_limit_telemetry`, and `csp_alert_transitions`.
- Add a contract script for migration/runbook required content.

## Out of Scope

- Removing defensive lazy `CREATE TABLE IF NOT EXISTS` from runtime stores.
- Telemetry database health metrics.
- SQLite legacy cleanup.

## Success Criteria

- Contract script fails before migration assets exist.
- Contract script passes after migration/runbook creation.
- SQL applies successfully to local central Postgres.
- Gated Postgres telemetry integration tests pass.
