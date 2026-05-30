# T54 Browser Security Dashboard Design

Date: 2026-05-19
PDCA Phase: Design
Slice: T54 Browser Security Dashboard

## Architecture Decision

Expose the existing in-memory CSP telemetry store through a narrow read model and a small Nuxt operator page. The dashboard must remain read-only, sanitized, dependency-light, and safe to ship before database-backed telemetry exists.

## Components

| Component | Responsibility |
| --- | --- |
| `csp-telemetry-dashboard.ts` | Build bounded sanitized dashboard DTOs from `CspTelemetryStore`. |
| `/api/security/csp-telemetry` | Return summary and recent reports; enforce optional operator token. |
| `/security` page | Render browser security telemetry with loading, error, empty, and populated states. |
| `security-headers.test.ts` | Contract coverage for dashboard DTO and operator guard. |
| component/page test | Verify rendered dashboard content and sanitized report values. |

## API Contract

```json
{
  "summary": {
    "total": 3,
    "byEffectiveDirective": {
      "style-src": 2,
      "script-src": 1
    },
    "topDirectives": [
      { "directive": "style-src", "count": 2 }
    ]
  },
  "recent": [
    {
      "requestId": "req-csp-1",
      "receivedAt": "2026-05-19T00:00:00.000Z",
      "effectiveDirective": "style-src",
      "violatedDirective": "style-src",
      "blockedUriOrigin": "inline",
      "documentUriOrigin": "https://app.discord-clone.local",
      "disposition": "report"
    }
  ]
}
```

## Operator Guard

If `NUXT_SECURITY_DASHBOARD_TOKEN` or runtime config equivalent is configured, requests must include the same value in `x-operator-token`. If no token is configured, the local development dashboard remains readable.

This is intentionally minimal for T54. Full admin RBAC belongs to a later auth integration task.

## UI Design

The `/security` page uses a dense operational layout:

- summary strip for total reports and directive count,
- directive table/list sorted by count,
- recent report list with request id, time, directive, disposition, document origin, and blocked origin,
- empty state when no telemetry exists,
- alert state for failed API reads.

No raw report bodies, query strings, script samples, tokens, or user-agent values are displayed.

## Testing

- RED test for dashboard DTO sorting, limiting, and sanitization.
- RED test for operator token access guard.
- RED page test for populated and empty dashboard states.
- Existing CSP report tests must remain green.
- `npm test -w apps/web` and `npm run build -w apps/web` are the completion gate.

## Risks

- In-memory telemetry resets on process restart.
- Multiple Nuxt instances still require aggregation.
- Minimal token guard is not a replacement for admin RBAC.
- Dashboard data has no time-series chart or alert threshold yet.
