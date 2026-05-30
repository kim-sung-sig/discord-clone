# T102 Redis-backed CSP Report Limiter Plan

## Objective

Coordinate CSP report rate limiting across multiple Nuxt instances by adding a Redis-backed limiter while preserving the existing in-memory limiter for local development.

## Current State

- CSP report rate limiting is process-local through `InMemoryCspReportRateLimiter`.
- The CSP report route calls the synchronous `handleCspReportPayload`.
- The web workspace does not yet have a Redis client dependency.

## Scope

1. Extend the rate limiter contract to allow async decisions.
2. Add an async CSP report handler for server routes.
3. Implement a Redis-backed fixed-window CSP report limiter.
4. Add a Redis-backed default factory controlled by environment variables.
5. Keep the in-memory limiter as the default when Redis is not configured.
6. Preserve fail-closed behavior when Redis is configured but unavailable.

## Acceptance Criteria

- Two limiter instances sharing Redis state coordinate the same subject limit.
- Redis keys do not expose raw subject values.
- Redis failures deny the report instead of bypassing rate limits.
- Existing sync handler tests continue to pass.
- Focused tests, full web tests, build, and whitespace checks pass.

