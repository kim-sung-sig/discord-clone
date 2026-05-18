# T35 Redis-backed Runtime Controls Analysis

작성일: 2026-05-16  
PDCA Phase: Check  
Slice: T35 Redis-backed Runtime Controls

## Implementation Status

T35 implementation is verified. Redis-backed runtime control adapters are profile-gated, production startup rejects missing Redis profile/config, and full backend Gradle tests pass.

## Implemented

- Added `spring-boot-starter-data-redis` to backend boot.
- Added `RedisRateLimitStore` under the `redis` profile.
- Kept `InMemoryRateLimitStore` under non-`redis` profiles.
- Renamed misleading `InMemoryRedisPresenceTtlStore` to `InMemoryPresenceTtlStore`.
- Added `RedisPresenceTtlStore` under the `redis` profile.
- Updated `PresenceConfiguration` to inject `PresenceTtlStore` by profile.
- Added `application-redis.yml` and `.env.example` Redis settings.
- Updated production validation to require `production,postgres,redis` and a non-local Redis host.
- Added focused tests for Redis rate-limit behavior, Redis presence encoding/degradation, and production Redis profile validation.

## Verification Attempted

| Command | Result | Notes |
| --- | --- | --- |
| `.\\gradlew.bat :backend:boot:compileJava --no-daemon` | PASS | Backend boot compiled after fixing presence record accessor names. |
| `.\\gradlew.bat :backend:boot:test --tests com.example.discord.ops.RedisRateLimitStoreTest --tests com.example.discord.presence.RedisPresenceTtlStoreTest --tests com.example.discord.ops.ProductionSecretValidationTest --no-daemon` | PASS | Focused T35 tests passed. |
| `.\\gradlew.bat :backend:boot:test --no-daemon` | PASS | Boot/controller regression tests passed after removing the extra `Clock` bean that conflicted with `authClock`. |
| `.\\gradlew.bat test --no-daemon` | PASS | Full Gradle test suite passed. |

## Manual Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| `redis` profile has Redis-backed rate limit adapter | PASS | `RedisRateLimitStore` is `@Profile("redis")`; focused tests cover fixed-window counters and fail-closed behavior. |
| Default profile keeps in-memory rate limit | PASS | `InMemoryRateLimitStore` is `@Profile("!redis")`; boot regression tests pass without Redis profile. |
| `redis` profile has Redis-backed presence TTL adapter | PASS | `RedisPresenceTtlStore` is `@Profile("redis")`; focused tests cover encoding, key listing, and failure degradation. |
| Default profile keeps in-memory presence TTL | PASS | `PresenceConfiguration` creates `InMemoryPresenceTtlStore` under `!redis`; boot regression tests pass without Redis profile. |
| Production requires Redis | PASS | `ProductionSecretValidationTest` covers missing Redis profile and non-local Redis host validation. |
| Redis failure policy is explicit | PASS | Rate limit fails closed; presence degrades to absent/offline. |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| `RedisPresenceTtlStore` compile failed on `observedAt()` and `startedAt()` | Existing `UserPresence` and `TypingIndicator` records use `updatedAt()` | Changed encoder to use `updatedAt()`. |
| `:backend:boot:test` failed with `NoUniqueBeanDefinitionException` for `Clock` | T35 added a second `Clock` bean, conflicting with existing `authClock` injection | Removed the `presenceClock` bean and created presence clocks inside `PresenceConfiguration` factory methods. |

## Residual Risks

- Redis tests use mocked `StringRedisTemplate`; a Docker/Testcontainers-backed Redis smoke remains a follow-up candidate.
- `RedisPresenceTtlStore.keys(prefix)` uses pattern key lookup for current small-scope typing lists; high-volume production Gateway/presence work should replace it with SCAN or explicit registry sets.
