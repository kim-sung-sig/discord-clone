---
slug: T103-csp-rate-limit-telemetry-counter
ticket: T103
phase: analysis
hub: "[[T103-csp-rate-limit-telemetry-counter]]"
---
> 🧭 [[T103-csp-rate-limit-telemetry-counter]] · PDCA: [[T103-csp-rate-limit-telemetry-counter.plan]] → [[T103-csp-rate-limit-telemetry-counter.design]] → **analysis** → [[T103-csp-rate-limit-telemetry-counter.report]] → [[T103-csp-rate-limit-telemetry-counter.feedback]]

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

