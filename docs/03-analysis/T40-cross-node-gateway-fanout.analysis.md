# T40 Cross-node Gateway Fanout Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T40 Cross-node Gateway Fanout

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `.\\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest --no-daemon` | PASS | Cross-node in-memory fanout, hidden-channel filtering, and redelivery dedupe tests passed after RED failure. |
| `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest --no-daemon` | PASS | Redis Streams publish writes sanitized payloads to channel stream. |
| `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayControllerTest --no-daemon` | PASS | Existing HTTP Gateway contract remains compatible. |
| `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest --no-daemon` | PASS | WebSocket READY/EVENT/HEARTBEAT/RESUME behavior remains compatible. |
| `.\\gradlew.bat :backend:boot:test --no-daemon` | PASS | Backend boot regression suite passed. |
| `.\\gradlew.bat :backend:modules:gateway:test --no-daemon` | PASS | Gateway module regression suite passed. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Two users connected to different Gateway nodes receive the same authorized channel event | PASS | `sharedBusFansOutEventsToSessionsOnOtherGatewayNodes` uses two service instances over one bus. |
| Hidden channel users do not receive cross-node fanout events | PASS | `crossNodeFanoutStillFiltersHiddenChannelEventsAtDeliveryTime` keeps delivery-time permission checks. |
| Replay buffer and sequence support reconnect/resume across node boundaries within window | PARTIAL | Bus events are retained per node after consumption, but session state is still node-local in production. |
| Bus redelivery does not duplicate frontend-visible state | PASS | `busRedeliveryDoesNotAppendDuplicateGatewayEvents` dedupes by `busEventId`. |
| Fanout payloads contain no tokens, secrets, signed URLs, or raw object keys | PASS | `GatewayPayloadSanitizer` removes sensitive keys/values before bus publish. |
| Multi-node test or deterministic simulation passes in CI-compatible form | PASS | Module-level deterministic simulation runs without Redis/Testcontainers. |

## Implementation Notes

- Added `GatewayEventBus` as the broker-neutral fanout port.
- Added `GatewayBusPublishCommand` and `GatewayBusEvent` as sanitized bus payload contracts.
- Added `InMemoryGatewayEventBus` for deterministic multi-node simulation and default local runtime.
- Added `RedisGatewayEventBus` under the `redis` profile using Redis Streams keys:
  - `gateway:channel:{channelId}`
  - `gateway:guild:{guildId}`
  - `gateway:global`
- Refactored `InMemoryGatewayService.publish()` to publish through the bus, consume bus events, append local Gateway events, and notify WebSocket listeners.
- Added `busEventId` to `GatewayEvent` for server-side duplicate suppression.
- Kept authorization at delivery time through existing guild/channel permission checks.

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| Production session registry remains node-local | RESUME to a different node cannot be guaranteed unless that node already has compatible session state | T60 shared Gateway session registry |
| Redis Streams consumer uses simple offset polling, not consumer groups | Works as a minimal adapter but lacks robust pending-entry recovery and lag visibility | T59 Redis Streams consumer group hardening |
| No live Redis multi-node integration test yet | Redis adapter publish contract is tested with mocks; full cross-process delivery still needs harness coverage | T61 Redis/Testcontainers multi-node fanout test |
| Channel subscription list is captured from currently visible channels | Newly created channels after identify need subscription reconciliation | T62 Gateway subscription reconciliation |

## Match Rate

Estimated design match: 82%.

The core fanout boundary, authorization guard, duplicate suppression, payload safety, and deterministic simulation are implemented. The remaining gap is the production-grade resume/session registry and Redis consumer operational hardening.
