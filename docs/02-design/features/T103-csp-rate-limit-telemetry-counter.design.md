# T103 CSP Rate-limit Telemetry Counter Design

## Store

Add `CspRateLimitTelemetryStore` with:

- `recordLimited(input)`
- `summary()`

The in-memory implementation stores only:

- received timestamp
- SHA-256 subject hash
- reset timestamp

It does not store raw IPs, user agents, or report bodies.

## Handler Integration

`handleCspReportPayload` and `handleCspReportPayloadAsync` record a limited event when the rate limiter denies a request.

## Dashboard Integration

`buildCspTelemetryDashboard` accepts an optional rate-limit telemetry store and includes:

```json
{
  "rateLimit": {
    "limitedTotal": 0
  }
}
```

## Route Wiring

Both CSP report routes pass the default rate-limit telemetry store to the handler. The dashboard route passes the same store to dashboard construction.

