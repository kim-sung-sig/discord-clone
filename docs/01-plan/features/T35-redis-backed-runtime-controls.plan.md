# T35 Redis-backed Runtime Controls Plan

작성일: 2026-05-16  
PDCA Phase: Plan  
Slice: T35 Redis-backed Runtime Controls

## Problem

Rate limiting and presence TTL behavior still rely on process-local memory by default. That is acceptable for unit tests and local slices, but production traffic across multiple backend nodes needs shared runtime state. Without a Redis-backed path, login/message/gateway throttles can be bypassed per node and presence/typing TTL state can drift between instances.

## Scope

- Add Redis-backed `RateLimitStore` for auth login, invite accept, message create, and gateway identify limits.
- Add Redis-backed `PresenceTtlStore` for user presence and typing TTL state.
- Keep in-memory adapters for default local/unit-test profile.
- Make `redis` profile select Redis adapters.
- Make `production` profile require `redis` in addition to `postgres`.
- Document Redis failure policy by runtime area.
- Add focused tests for adapter selection, Redis key/TTL behavior, and production profile validation.

## Out of Scope

- Cross-node Gateway fanout. That belongs to T40.
- Real WebSocket transport. That belongs to T36.
- Durable read-marker persistence. Current T35 scope is TTL runtime controls only.
- Redis Cluster/Sentinel deployment manifests.

## Success Criteria

- `redis` profile wires Redis-backed rate limit and presence TTL adapters.
- non-`redis` profiles keep existing in-memory adapters.
- production startup fails unless `redis` and `postgres` profiles are active.
- Redis rate limit counters are keyed by policy, subject, and fixed window.
- Presence and typing TTL values expire through Redis TTL semantics.
- Redis unavailable policy is explicit: rate-limited write/auth/gateway endpoints fail closed; presence/typing degrade to offline/empty.

## Failure Criteria

- production profile can start with in-memory rate limit or presence TTL stores.
- Redis keys contain raw access tokens, passwords, or un-hashed IP addresses.
- Redis outage silently allows auth/message/gateway abuse bursts.
- Presence Redis failures crash unrelated API requests instead of degrading.
