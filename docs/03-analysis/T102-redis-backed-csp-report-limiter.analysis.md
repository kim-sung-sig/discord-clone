# T102 Redis-backed CSP Report Limiter Analysis

## Result

CSP report rate limiting now supports a Redis-backed fixed-window limiter while preserving the in-memory default for local development.

## Behavior

- `CspReportRateLimiter.consume()` can now return a synchronous decision or an async decision.
- `handleCspReportPayload()` remains synchronous for existing tests and in-memory callers.
- `handleCspReportPayloadAsync()` awaits async limiters and is used by both CSP report routes.
- Redis limiter hashes subjects before using them in keys.
- Redis limiter fails closed when Redis cannot consume a counter.

## Configuration

- `NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL`
- `NUXT_CSP_REPORT_RATE_LIMIT_MAX_REPORTS`
- `NUXT_CSP_REPORT_RATE_LIMIT_WINDOW_MS`

## Dependency Note

The web workspace now depends on `redis`. After installation, `npm audit` reported one existing high severity transitive `devalue` advisory. `npm audit fix -w apps/web` updated the lockfile and `npm audit -w apps/web --audit-level=high` now reports zero vulnerabilities.

