# T128 CSP Rate-Limit Subject Diagnostics Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T128 CSP Rate-Limit Subject Diagnostics

## Design

Add `cspRateLimitSubjectDiagnosticsFor(input)` beside the existing `rateLimitSubjectFor(input)`.

`rateLimitSubjectFor` still returns the normalized subject string for limiter behavior. The diagnostics helper reuses the same resolution path but returns only safe metadata:

| Field | Meaning |
| --- | --- |
| `source` | `remote-address`, `x-forwarded-for`, `x-real-ip`, or `unknown`. |
| `subjectHashPrefix` | First 12 hex characters of the normalized subject hash. |
| `trustedProxyConfigured` | Whether trusted proxy rules exist. |
| `trustedProxyMatched` | Whether the direct peer matched a trusted proxy rule. |
| `trustedProxyRuleCount` | Number of configured trusted proxy rules. |
| `forwardedForPresent` | Whether an `x-forwarded-for` header was present. |
| `realIpPresent` | Whether an `x-real-ip` header was present. |

## Dashboard Flow

1. `/api/security/csp-telemetry` authorizes through the existing dashboard guard.
2. It computes diagnostics from the dashboard request headers and configured trusted proxy rules.
3. `buildCspTelemetryDashboard` includes diagnostics under `rateLimit.subjectDiagnostics`.
4. `/security` renders a compact diagnostics panel.

## Security Review

The dashboard never receives raw IP addresses, raw forwarded headers, full subject hashes, or auth tokens. Vue template interpolation is used instead of raw HTML insertion.
