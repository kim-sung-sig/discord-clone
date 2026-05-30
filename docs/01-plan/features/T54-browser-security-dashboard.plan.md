# T54 Browser Security Dashboard Plan

Date: 2026-05-19
PDCA Phase: Plan
Slice: T54 Browser Security Dashboard

## Executive Summary

| Perspective | Content |
| --- | --- |
| Problem | CSP telemetry is collected and rate-limited, but operators cannot inspect recent browser security violations without reading server logs or test internals. |
| Solution | Add a small Nuxt operator dashboard and JSON API that expose sanitized CSP telemetry summary and recent reports from the existing store. |
| Function UX Effect | 운영자는 브라우저에서 CSP 위반 수, directive별 분포, 최근 sanitized report를 빠르게 확인할 수 있다. |
| Core Value | Security hardening work becomes observable and actionable instead of being hidden in process-local state. |

## Scope

- Add a CSP telemetry dashboard response model.
- Add an API endpoint for summary and recent sanitized CSP telemetry.
- Add a Nuxt page for browser security operators.
- Keep payloads sanitized and avoid raw URL/query/script sample exposure.
- Add tests for API response shape and page rendering.
- Document residual risks and follow-up tasks.

## Out of Scope

- Database-backed telemetry persistence.
- Authentication/RBAC beyond a minimal operator token guard.
- Alerting or notification delivery.
- Charts that require a new dependency.
- Multi-instance aggregation.

## Success Criteria

- API returns total count, directive summary, and bounded recent reports.
- API rejects unauthorized reads when an operator token is configured.
- Dashboard renders total, top directives, recent reports, and empty state.
- Dashboard does not expose raw secrets, query strings, or script samples.
- Existing CSP report ingestion tests continue to pass.

## Failure Criteria

- Dashboard exposes raw CSP report bodies or sensitive URL details.
- Report ingestion behavior changes or starts returning non-204 responses.
- API has no bounded limit.
- UI is inaccessible or unusable on narrow desktop/mobile widths.
- Tests only mock visual output without verifying sanitized telemetry content.
