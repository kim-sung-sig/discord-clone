# T131 Redis CSP Limiter Lifecycle Metrics Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T131 Redis CSP Limiter Lifecycle Metrics

## Design

Extend `CspReportRateLimiter` with an optional `lifecycleMetrics()` method. In-memory limiters can return a minimal memory backend payload. Redis limiters return:

| Field | Meaning |
| --- | --- |
| `backend` | `redis` or `memory`. |
| `failClosedDecisions` | Limiter decisions denied because Redis could not consume a counter. |
| `redis.connectAttempts` | Node Redis connection attempts. |
| `redis.connectSuccesses` | Successful connection attempts. |
| `redis.connectFailures` | Connection promise failures. |
| `redis.errorEvents` | Redis client error events. |
| `redis.reconnectEvents` | Redis client reconnecting events. |
| `redis.closeCalls` | Explicit close calls. |

Recent event timestamps are included only as ISO strings, never as error messages or connection details.

## Data Flow

1. CSP report routes keep using `defaultCspReportRateLimiter`.
2. Redis limiter increments `failClosedDecisions` when Redis throws and the limiter denies the report.
3. Node Redis client records lifecycle counters around connect, error, reconnecting, and close events.
4. `/api/security/csp-telemetry` passes the default limiter into `buildCspTelemetryDashboard`.
5. `/security` renders `rateLimit.lifecycle` if present.

## Security Review

- Metrics are numeric counters and timestamps only.
- Redis URL, password, keys, raw IP, and raw subject values are not included.
- Redis error messages are not stored in metrics.
- Vue interpolation is used for display.
