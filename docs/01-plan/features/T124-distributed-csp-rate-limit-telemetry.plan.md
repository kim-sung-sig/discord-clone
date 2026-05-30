# T124 Distributed CSP Rate-limit Telemetry Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T124 Distributed CSP Rate-limit Telemetry

## Executive Summary

| View | Content |
| --- | --- |
| Problem | CSP rate-limit telemetry counted limited reports in a process-local in-memory store, so multiple Nuxt instances could not share operator visibility. |
| Solution | Add a Postgres-backed rate-limit telemetry store and select it from the existing central CSP telemetry database configuration. |
| Operator Effect | `/security` can show limited CSP report totals aggregated across Nuxt instances. |
| Core Value | Abuse-control telemetry becomes centralized like accepted CSP telemetry. |

## Scope

- Add a Postgres implementation for CSP rate-limit telemetry.
- Keep subject data hashed before persistence.
- Make report handlers support async rate-limit telemetry stores.
- Make dashboard building support async rate-limit telemetry summaries.
- Reuse `NUXT_CSP_TELEMETRY_POSTGRES_URL` by default, with an optional dedicated `NUXT_CSP_RATE_LIMIT_TELEMETRY_POSTGRES_URL`.
- Add focused tests and gated Postgres coverage.

## Out of Scope

- UI changes for detailed rate-limit charts.
- Per-subject diagnostics.
- Alert persistence.
- Explicit Flyway migration files.

## Success Criteria

- In-memory behavior remains compatible.
- Async/Postgres rate-limit telemetry is awaited before limited responses return.
- Postgres summary aggregates limited counts centrally.
- No raw rate-limit subject is stored.
- Existing dashboard payload still exposes `rateLimit.limitedTotal`.
