# T40 Cross-node Gateway Fanout Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T40 Cross-node Gateway Fanout

## Summary

T40 moved Gateway event delivery behind a broker-neutral fanout bus. Gateway publish now emits sanitized bus events, each node consumes bus events into its local delivery buffer, and WebSocket clients are flushed through the existing delivery authorization path.

## Delivered

- Added `GatewayEventBus` fanout port.
- Added sanitized bus contracts: `GatewayBusPublishCommand`, `GatewayBusEvent`.
- Added `GatewayPayloadSanitizer` to remove tokens, secrets, signed URLs, and raw object keys.
- Added `InMemoryGatewayEventBus` for local/default runtime and CI-safe multi-node simulation.
- Added `RedisGatewayEventBus` under the `redis` profile with Redis Streams publish and polling support.
- Wired Spring default profile to in-memory bus and `redis` profile to Redis bus.
- Added `busEventId` to `GatewayEvent` and dedupe map in `InMemoryGatewayService`.
- Preserved delivery-time channel authorization.
- Preserved existing Gateway HTTP/WebSocket contracts.

## Verification

- `:backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest`: PASS
- `:backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest`: PASS
- `:backend:boot:test --tests com.example.discord.gateway.GatewayControllerTest`: PASS
- `:backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest`: PASS
- `:backend:boot:test`: PASS
- `:backend:modules:gateway:test`: PASS

## Coverage

- cross-node fanout simulation
- hidden-channel delivery filtering
- server-side bus redelivery dedupe
- payload sanitization before Redis publish
- Redis stream key selection for channel-scoped events
- Gateway controller regression
- WebSocket transport regression

## Residual Risks

- True production cross-node RESUME still needs shared Gateway session registry and sequence semantics.
- Redis Streams polling should move to consumer groups with pending-entry recovery before production scale.
- Current Redis adapter has unit-level publish coverage, not a live Redis multi-node integration harness.
- Channel subscriptions should reconcile when new channels become visible after a session identifies.

## Next Recommended Task

Proceed to T41 after registering T40 follow-ups. The next highest-value realtime hardening item is shared Gateway session registry and resume semantics, because fanout delivery is now separated from the single-JVM publish path.
