# T135 Explicit CSP Telemetry Postgres Migration Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T135 Explicit CSP Telemetry Postgres Migration

## Migration

Path: `apps/web/server/database/migrations/001_csp_telemetry_postgres.sql`

Tables:

| Table | Purpose |
| --- | --- |
| `csp_telemetry` | Sanitized accepted CSP report telemetry. |
| `csp_telemetry_retention_metrics` | Aggregate retention discard counters. |
| `csp_rate_limit_telemetry` | Sanitized/hash-only CSP rate-limit telemetry. |
| `csp_alert_transitions` | Persisted active/cleared CSP alert state transitions. |

Indexes:

- `idx_csp_telemetry_received_at`
- `idx_csp_telemetry_effective_directive`
- `idx_csp_rate_limit_telemetry_received_at`
- `idx_csp_alert_transitions_observed_at`

## Runbook

Path: `docs/runbooks/csp-telemetry-postgres-migration.md`

The runbook covers pre-checks, `psql` apply commands, Docker fallback, table/index verification, gated test verification, and rollback guidance.

## Security Review

- The schema stores sanitized CSP origins/directives and user-agent hashes, not raw report bodies.
- Rate-limit telemetry stores `subject_hash`, not raw IP or subject values.
- Alert transitions store aggregate reasons, not raw CSP reports.
- The runbook recommends secret handling through environment variables or secret manager context.
