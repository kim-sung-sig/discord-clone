# T60 Shared Gateway Session Registry And Cross-node RESUME Analysis

Date: 2026-05-21

## RED Evidence

- `.\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest` failed at compile time because `InMemoryGatewaySessionRegistry` did not exist.
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewaySessionRegistryTest` failed at compile time because `RedisGatewaySessionRegistry` did not exist.
- After adding the registry, the cross-node resume test initially failed because node-local event sequence `1` collided with the shared session's `lastDeliveredSequence=1`.

## GREEN Evidence

- Added sequence floor synchronization from shared registry state.
- Module Gateway tests passed.
- Redis registry tests passed.
- Gateway Controller/WebSocket integration tests passed.
- Gateway checkstyle passed.

## Security Review

The Redis registry test asserts stored JSON excludes token/password markers. The implementation writes only ids, guild scopes, timestamps, closed state, and last delivered sequence.

## Remaining Risk

Redis registry entries are not yet expired or pruned. Timed-out sessions are marked closed, but long-term cleanup should be an explicit follow-up.
