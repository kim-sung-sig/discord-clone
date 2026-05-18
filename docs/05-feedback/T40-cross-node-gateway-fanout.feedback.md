# T40 Cross-node Gateway Fanout Feedback

작성일: 2026-05-17  
PDCA Phase: Act  
Slice: T40 Cross-node Gateway Fanout

## Decisions

- Keep Gateway fanout broker-neutral through `GatewayEventBus`.
- Use Redis Streams as the first production adapter under the `redis` profile.
- Keep delivery authorization in the node-local Gateway service, after bus consume and before WebSocket send.
- Sanitize payloads before bus publish, not only before WebSocket serialization.
- Use `busEventId` as the server-side duplicate suppression key.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| Gateway publish was single-process only | Added fanout bus port and shared in-memory implementation. |
| WebSocket listeners only flushed local publishes | `InMemoryGatewayService` now appends consumed bus events and notifies listeners. |
| Redelivered events could become duplicate local Gateway events | Added `eventsByBusEventId` dedupe. |
| Fanout payloads had no explicit secret stripping boundary | Added recursive payload sanitizer. |
| Redis production profile had no Gateway fanout adapter | Added `RedisGatewayEventBus` with Redis Streams publish and poll support. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T59 Redis Streams consumer-group hardening | P1 | Add consumer groups, pending-entry retry, lag metrics, retention config, and dead-letter handling. |
| T60 shared Gateway session registry and cross-node RESUME | P1 | Production cross-node resume needs shared session state and explicit sequence semantics. |
| T61 Redis/Testcontainers multi-node Gateway fanout test | P1 | Current Redis coverage is unit-level; live Redis delivery should be proven in CI. |
| T62 Gateway subscription reconciliation | P2 | Nodes should subscribe to channels that become visible after identify. |
| T63 Gateway fanout backpressure and DLQ policy | P2 | Slow consumers and malformed events need operational policy before high-volume realtime traffic. |

## Verification

- `.\\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayControllerTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS
- `.\\gradlew.bat :backend:modules:gateway:test --no-daemon`: PASS
