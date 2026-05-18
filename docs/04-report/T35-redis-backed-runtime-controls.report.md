# T35 Redis-backed Runtime Controls Report

작성일: 2026-05-16  
PDCA Phase: Report  
Slice: T35 Redis-backed Runtime Controls

## Summary

T35 moved runtime controls from process-local-only behavior to profile-selectable Redis adapters. The default local/test path keeps in-memory stores, while the `redis` profile wires Redis-backed rate limiting and presence TTL. Production config validation now requires both `postgres` and `redis` profiles so production cannot accidentally run with local-memory runtime controls.

## Delivered

- Added `spring-boot-starter-data-redis`.
- Added `application-redis.yml`.
- Added `RedisRateLimitStore` under the `redis` profile.
- Kept `InMemoryRateLimitStore` under non-`redis` profiles.
- Renamed misleading `InMemoryRedisPresenceTtlStore` to `InMemoryPresenceTtlStore`.
- Added `RedisPresenceTtlStore` under the `redis` profile.
- Updated `PresenceConfiguration` to select `PresenceTtlStore` by profile.
- Updated production secret/config validation to require `redis` and non-local Redis host.
- Updated `.env.example` with Redis local settings and profile guidance.
- Added Redis rate-limit, Redis presence TTL, and production validation tests.
- Updated legacy T08 docs that referenced the old in-memory Redis naming.

## Verification

- `.\\gradlew.bat :backend:boot:compileJava --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.ops.RedisRateLimitStoreTest --tests com.example.discord.presence.RedisPresenceTtlStoreTest --tests com.example.discord.ops.ProductionSecretValidationTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS
- `.\\gradlew.bat test --no-daemon`: PASS

## Issues Fixed During Check

- Fixed `RedisPresenceTtlStore` to use existing `updatedAt()` record accessors.
- Removed an extra `Clock` bean from `PresenceConfiguration` after it caused `NoUniqueBeanDefinitionException` across boot tests.

## Residual Risks

- Redis adapter tests currently mock `StringRedisTemplate`; a Docker/Testcontainers-backed Redis smoke should be promoted once CI/runtime policy allows it.
- `RedisPresenceTtlStore.keys(prefix)` is acceptable for current small typing/presence scope, but high-volume Gateway/presence work should replace it with SCAN or explicit registry sets.

## Next Recommended Task

Proceed to T36 Real WebSocket Gateway Transport. T35 gives production profile a shared Redis runtime-control foundation, which T36 can build on for authenticated WebSocket identify, heartbeat, and resume behavior.
