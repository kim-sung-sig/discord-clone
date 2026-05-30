# T53 CSP Report Rate Limiter Design

Date: 2026-05-18
Slice: T53 CSP Report Rate Limiter

## Architecture

The CSP report endpoint remains a low-response-surface endpoint: every accepted, rejected, or limited report returns `204`. Rate limiting is implemented as a handler dependency so unit tests can inject deterministic limits and clocks.

## Components

- `CspReportRateLimiter`
  - Interface with `consume({ subject, at })`.
  - Returns `allowed`, `limit`, `remaining`, and `resetAt`.

- `InMemoryCspReportRateLimiter`
  - Fixed-window process-local limiter.
  - Bounded by `maxSubjects` to avoid unbounded subject growth.
  - Defaults to 60 reports per 60 seconds per subject.

- `handleCspReportPayload`
  - Checks the limiter before content-type/body validation and JSON normalization.
  - Uses the same timestamp for limiter and telemetry storage.

- CSP report routes
  - Use `x-forwarded-for` first, then `x-real-ip`, then `unknown`.
  - Share the same default limiter for enforce and report-only endpoints.

## Data Flow

1. Route reads the raw body.
2. Route derives `rateLimitSubject`.
3. Handler consumes rate limit budget.
4. If limited, handler returns `accepted: false`, `reason: rate limited`, `statusCode: 204`.
5. If allowed, existing validation, normalization, and telemetry storage continue.

## Error Handling

Limited reports do not return `429` because browser CSP reporting endpoints should avoid noisy retry/reflection behavior. The result is observable through tests and can later be connected to telemetry counters.

## Testing

The focused unit test injects a two-report window and a deterministic clock. It proves the third report is limited, telemetry remains at two entries, and a report after the window is accepted.
