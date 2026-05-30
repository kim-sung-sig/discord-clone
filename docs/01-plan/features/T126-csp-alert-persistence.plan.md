# T126 CSP Alert Persistence Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T126 CSP Alert Persistence

## Executive Summary

| View | Content |
| --- | --- |
| Problem | CSP alert state was computed per dashboard request, so operators could not review when alerts became active or cleared. |
| Solution | Persist alert state transitions and include recent transition history in the security dashboard. |
| Operator Effect | Operators can inspect alert activation and clear history after the request that detected it. |
| Core Value | CSP alerting becomes reviewable operational evidence instead of transient response state. |

## Scope

- Add in-memory and Postgres CSP alert transition stores.
- Record dashboard alert transitions when the dashboard payload is built.
- Add recent alert history to the dashboard API payload.
- Render alert history in `/security`.
- Verify Postgres persistence behind the existing gated integration test.

## Out of Scope

- Alert acknowledgement or suppression.
- Alert export and retention policy.
- Persisting raw CSP payloads or rate-limit subjects.

## Success Criteria

- Repeated identical alert states do not create duplicate transition rows.
- Active and cleared transitions are reviewable through the dashboard payload/UI.
- Postgres-backed transition persistence passes.
- No raw report, IP, or subject data is stored in alert transitions.
