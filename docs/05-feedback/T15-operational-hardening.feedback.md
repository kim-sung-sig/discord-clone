# T15 Operational Hardening Feedback

작성일: 2026-05-14  
PDCA Phase: Act  
Slice: T15 Operational Hardening/E2E Stabilization

## Feedback Items

| ID | Type | Detail | Action |
| --- | --- | --- | --- |
| T15-FB-001 | Limitation | `X-Request-Id` is not connected to structured logs or distributed tracing. | Add logging MDC/OpenTelemetry in a future observability task. |
| T15-FB-002 | Limitation | API CSP does not configure Nuxt HTML response CSP. | Add deployment/header policy when production serving topology is defined. |
| T15-FB-003 | Limitation | No rate limiting or abuse protection exists. | Add rate-limit policy after Redis/persistence boundary is finalized. |
| T15-FB-004 | Quality | Existing Gradle/Nuxt warnings remain. | Track separately; not blocking T15 because all verification commands pass. |

## PDCA Act Decision

- No T15 rework loop required.
- Treat future work as new scoped tasks rather than expanding this hardening baseline.
