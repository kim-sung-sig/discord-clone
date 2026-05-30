# T136 CSP Telemetry Postgres Health Metric Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T136 CSP Telemetry Postgres Health Metric

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Operators could see CSP telemetry counts, but not whether durable Postgres storage was healthy or recording write failures. |
| Solution | Add a storage health payload to the CSP telemetry dashboard and render storage status/write failures in `/security`. |
| Operator Effect | Operators can distinguish normal Postgres-backed telemetry from degraded storage during incidents. |
| Core Value | Security telemetry durability becomes observable, not assumed. |

## Scope

- Add a CSP telemetry storage health contract.
- Track Postgres write failure count and last write failure metadata.
- Include storage health in `/api/security/csp-telemetry` dashboard payload.
- Render storage status and write failure count in `/security`.

## Out of Scope

- External metrics export.
- Redis limiter lifecycle health.
- Alert acknowledgement workflow.

## Success Criteria

- Dashboard payload includes `health.storage`.
- `/security` shows storage status and write failures.
- Health does not expose database URLs or passwords.
- Web tests, Postgres integration tests, and build pass.
