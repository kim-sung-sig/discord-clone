# T117 Redis Client Lifecycle Cleanup Analysis

## Implementation Notes

- Added a failing focused test before implementation.
- Extended the limiter and Redis client interfaces with optional close support.
- Implemented close on `RedisCspReportRateLimiter`.
- Added a Nitro lifecycle plugin for the default limiter.

## Risk Review

- Existing in-memory limiter behavior is unchanged.
- The close method is optional, so callers can use the same contract for Redis and non-Redis deployments.
- Redis limiter shutdown errors are not swallowed by the close method; Nitro will surface close failures in logs.

## Remaining Gaps

- Redis connection retry/backoff and lifecycle metrics are not exposed.
- There is no explicit unit test for the Nitro plugin hook registration; build verification covers plugin import and bundling.
