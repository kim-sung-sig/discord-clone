# T103 CSP Rate-limit Telemetry Counter Analysis

## Result

CSP reports dropped by rate limiting are now counted and exposed through the browser security dashboard payload.

## Behavior

- Rate-limited CSP reports increment `limitedTotal`.
- Accepted reports do not increment the rate-limit counter.
- The counter stores only a hashed subject, received timestamp, and reset timestamp.
- Dashboard payload includes:

```json
{
  "rateLimit": {
    "limitedTotal": 1
  }
}
```

## Privacy

The in-memory telemetry store hashes the rate-limit subject with SHA-256 and never stores raw IPs, user agents, or report bodies.

## Limitation

The first implementation is process-local and in-memory. This is useful for local visibility, but multi-instance production visibility should aggregate through centralized telemetry or Redis counters.

