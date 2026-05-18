# T36 Real WebSocket Gateway Transport Feedback

작성일: 2026-05-17  
PDCA Phase: Act  
Slice: T36 Real WebSocket Gateway Transport

## Decisions

- Implement raw WebSocket transport at `/ws/gateway` instead of STOMP to keep the protocol close to the existing Gateway event model.
- Keep event authorization in `InMemoryGatewayService` and treat WebSocket as a transport adapter.
- Use existing REST publish paths as the event production source for tests.
- Close WebSocket sockets through `GatewaySessionMaintenance` when underlying Gateway sessions time out.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| Backend lacked WebSocket dependency. | Added `spring-boot-starter-websocket`. |
| WebSocket push needed to observe existing REST/application publishes. | Added listener support to `InMemoryGatewayService`. |
| Heartbeat maintenance closed only service sessions. | Added transport cleanup for closed Gateway sessions. |
| T36 tests needed real socket behavior, not MockMvc only. | Added random-port `StandardWebSocketClient` integration tests. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T37 frontend WebSocket Gateway client | P0 | Backend WebSocket transport exists, but Nuxt/Pinia still needs a production client path and UI connection states. |
| WebSocket SUBSCRIBE scope narrowing | P1 | T36 design includes `SUBSCRIBE`; current implementation delivers authorized session events without client-side scope narrowing. |
| WebSocket protocol error/rate-limit hardening | P1 | Current handler returns safe errors, but repeated malformed messages and identify bursts should have explicit socket-level close/rate-limit policy. |
| Cross-node WebSocket fanout | P1 | Current listener model is in-process; T40 should move delivery to Redis Streams or Redpanda. |
| Resume buffer contract extraction | P1 | Resume currently depends on `InMemoryGatewayService` event history; extracting a replay buffer port will make T40 cleaner. |

## Verification

- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS
- `.\\gradlew.bat test --no-daemon`: PASS
