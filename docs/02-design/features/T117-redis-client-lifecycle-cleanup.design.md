# T117 Redis Client Lifecycle Cleanup Design

## Close Contract

`CspReportRateLimiter` exposes optional `close?(): void | Promise<void>`.

- In-memory limiter does not need to implement it.
- Redis limiter implements it by delegating to its Redis client.

## Node Redis Client

`NodeRedisCspReportRateLimitClient.close()`:

- Does nothing if no client was opened.
- Quits the Redis connection when it is open.
- Clears the cached client promise after close so later use can reconnect.

## Nitro Integration

`server/plugins/csp-rate-limiter-lifecycle.ts` registers a Nitro `close` hook and calls `defaultCspReportRateLimiter.close?.()`.
