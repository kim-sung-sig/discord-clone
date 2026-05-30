# T117 Redis Client Lifecycle Cleanup Plan

## Objective

Ensure Redis-backed CSP report rate-limit clients close cleanly when Nuxt/Nitro shuts down or restarts.

## Current State

- T102 added `NodeRedisCspReportRateLimitClient.close()`.
- `RedisCspReportRateLimiter` does not expose a close method.
- The default limiter is not connected to Nitro lifecycle hooks.

## Scope

1. Add an optional close contract to CSP report rate limiters.
2. Make `RedisCspReportRateLimiter.close()` delegate to the Redis client.
3. Reset the Node Redis client promise after close.
4. Add a Nitro plugin that closes the default CSP report limiter on Nitro close.

## Acceptance Criteria

- Redis limiter close calls the underlying Redis client close.
- In-memory limiter remains compatible with the optional close contract.
- Nitro close hook closes the default limiter when it is closeable.
- Focused tests, related web tests, full web tests, build, and whitespace checks pass.
