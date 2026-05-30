# T102 Redis-backed CSP Report Limiter Design

## Interface

`CspReportRateLimiter.consume()` may return either:

- `CspReportRateLimitDecision`
- `Promise<CspReportRateLimitDecision>`

The existing synchronous `handleCspReportPayload()` remains for tests and in-memory callers. A new `handleCspReportPayloadAsync()` awaits async limiters and is used by Nuxt server routes.

## Redis Algorithm

Redis uses a fixed-window counter:

1. Normalize and hash the subject.
2. Compute `windowStart = floor(now / windowMs) * windowMs`.
3. Increment `csp-report-rate-limit:{hash}:{windowStart}`.
4. Set key expiry on the first increment.
5. Allow while `count <= maxReports`.

The Redis operation is executed with a Lua script so increment and expiry are atomic.

## Configuration

- `NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL`
- `NUXT_CSP_REPORT_RATE_LIMIT_MAX_REPORTS`
- `NUXT_CSP_REPORT_RATE_LIMIT_WINDOW_MS`

No Redis URL means in-memory limiter.

## Failure Policy

Redis-backed limiter fails closed:

- `allowed: false`
- `remaining: 0`
- `resetAt: now + windowMs`

