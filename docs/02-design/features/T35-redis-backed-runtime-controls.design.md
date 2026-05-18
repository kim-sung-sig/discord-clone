# T35 Redis-backed Runtime Controls Design

작성일: 2026-05-16  
PDCA Phase: Design  
Slice: T35 Redis-backed Runtime Controls

## Architecture Decision

Use Spring profile based adapter selection:

- Default profile: in-memory `RateLimitStore` and in-memory `PresenceTtlStore`.
- `redis` profile: Redis-backed `RateLimitStore` and Redis-backed `PresenceTtlStore`.
- `production` profile: must include both `postgres` and `redis`.

This keeps existing unit and local tests fast while giving production a shared runtime control plane.

## Runtime Adapter Boundary

| Runtime area | Port | Default adapter | Redis adapter | Failure policy |
| --- | --- | --- | --- | --- |
| API rate limit | `RateLimitStore` | `InMemoryRateLimitStore` | `RedisRateLimitStore` | Fail closed for protected endpoints |
| Presence/typing TTL | `PresenceTtlStore` | `InMemoryPresenceTtlStore` | `RedisPresenceTtlStore` | Degrade to offline/empty and ignore writes |

## Redis Key Strategy

Rate limit keys:

```text
rl:{policyId}:{subjectHash}:{windowStartMillis}
```

Presence keys:

```text
presence:user:{userId}
typing:channel:{channelId}:{userId}
```

Rate-limit subjects are already hashed before reaching the store. Redis adapters must not store bearer tokens, passwords, raw IP addresses, or raw request bodies.

## Rate Limit Algorithm

For each request:

1. Compute fixed window start from `Instant now` and policy window.
2. Increment the Redis key.
3. Apply the window TTL when the key is first created.
4. Allow when count is less than or equal to policy limit.
5. Return `Retry-After` from remaining window time.

If Redis throws during consume, return a denied decision with the policy limit and retry window. This is intentionally fail-closed because the endpoints are auth/write/gateway abuse controls.

## Presence TTL Encoding

`RedisPresenceTtlStore` stores compact string values:

```text
presence|{userId}|{status}|{observedAt}
typing|{channelId}|{userId}|{startedAt}
```

Unknown values are ignored and read as absent. Redis failures degrade presence to absent, which the service maps to offline.

## Configuration Contract

Local Redis profile:

```text
SPRING_PROFILES_ACTIVE=postgres,redis
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
```

Production profile:

```text
SPRING_PROFILES_ACTIVE=production,postgres,redis
SPRING_DATA_REDIS_HOST=<non-local redis host>
SPRING_DATA_REDIS_PORT=6379
```

Production validation rejects missing `redis` profile and local Redis defaults.

## QA Strategy

- Unit test Redis rate limit fixed-window count and fail-closed behavior.
- Unit test Redis presence TTL encoding and failure degradation.
- ApplicationContextRunner test verifies default vs `redis` adapter selection.
- Production config test verifies `production` requires `redis`.
- Existing full backend suite must remain green.

## Follow-up Candidates

- T40 should replace Redis `keys` style scans with fanout/session registry patterns appropriate for high-volume Gateway state.
- A future deployment task should add Redis Cluster/Sentinel configuration and operational dashboards.
