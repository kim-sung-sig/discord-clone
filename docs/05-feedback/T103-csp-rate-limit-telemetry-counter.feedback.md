# T103 CSP Rate-limit Telemetry Counter Feedback

## Improvement Tasks Captured

### T124 Distributed CSP Rate-limit Telemetry

Aggregate rate-limit telemetry across Nuxt instances. The current T103 counter is process-local, while T102 Redis limiter decisions are already distributed.

### T125 CSP Rate-limit Dashboard UI

Render `rateLimit.limitedTotal` visibly in `/security`, including empty and non-zero states.

