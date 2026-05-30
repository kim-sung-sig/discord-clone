# T54 Browser Security Dashboard Feedback

Date: 2026-05-19
PDCA Phase: Act
Slice: T54 Browser Security Dashboard

## Decisions

- Keep T54 read-only and dependency-light.
- Use the existing CSP telemetry store instead of introducing persistence in the same slice.
- Expose only sanitized dashboard DTO fields.
- Use a minimal optional operator token guard for the API.
- Avoid charts until telemetry persistence and aggregation are stronger.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| Operators had no UI for recent CSP telemetry. | Added `/security` dashboard. |
| CSP telemetry had no read endpoint. | Added `/api/security/csp-telemetry`. |
| Dashboard could accidentally expose raw report data. | Added DTO builder with sanitized fields only and tests against secret/script sample leakage. |
| Dashboard reads needed at least minimal protection. | Added optional `x-operator-token` guard tied to `NUXT_SECURITY_DASHBOARD_TOKEN`. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T105 admin RBAC for security dashboard | P1 | Optional token guard is not enough for multi-user admin operations. |
| T106 database-backed dashboard telemetry | P1 | Dashboard is limited by process-local in-memory telemetry. |
| T107 CSP telemetry trend chart | P2 | Operators need historical direction after durable storage exists. |
| T108 CSP alert threshold dashboard banner | P2 | Spikes should be visible in the dashboard and later route to alerts. |

## Verification

- `npm test -w apps/web -- security-headers.test.ts security-dashboard.test.ts`: PASS
- `npm run build -w apps/web`: PASS
- `npm test -w apps/web`: PASS

## Loop Decision

T54 scored 27/30 and passed the threshold. Continue to the next task, or prioritize T105/T106 if the security dashboard must become production-admin grade before new product features.
