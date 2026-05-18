# T36 Real WebSocket Gateway Transport Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T36 Real WebSocket Gateway Transport

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest --no-daemon` | PASS | WebSocket identify, event delivery, hidden-channel filtering, heartbeat ACK, resume replay, heartbeat timeout close tests passed. |
| `.\\gradlew.bat :backend:boot:test --no-daemon` | PASS | Backend boot/controller regression tests passed. |
| `.\\gradlew.bat test --no-daemon` | PASS | Full Gradle test suite passed. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Browser client can identify over WebSocket and receive READY | PASS | `websocketIdentifyReceivesReadyAndPublishedEvents` asserts `READY`, `sessionId`, and `userId`. |
| Heartbeat ACK works | PASS | `websocketHeartbeatAcknowledgesIdentifiedSession` asserts `HEARTBEAT_ACK` after identified heartbeat. |
| Heartbeat timeout closes stale sockets | PASS | `websocketHeartbeatTimeoutClosesStaleSocket` drives `GatewaySessionMaintenance` and observes client close. |
| Published message events reach authorized WebSocket clients | PASS | REST publish emits `EVENT` over `/ws/gateway`. |
| Hidden-channel events are not delivered | PASS | Hidden event is skipped; visible event is delivered to the same socket. |
| Short disconnect resume replays missed events | PASS | `RESUME` replays event published while the socket was closed. |
| Existing REST Gateway compatibility remains | PASS | Existing boot Gateway/controller tests pass. |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| RED compile failed because WebSocket classes were absent | Backend boot did not depend on Spring WebSocket | Added `spring-boot-starter-websocket`. |
| Test compile failed because `StandardWebSocketClient.execute` expected a String endpoint | Test passed a `URI` | Changed endpoint argument to string URL. |
| Heartbeat timeout test failed because service sessions closed but sockets stayed open | `GatewaySessionMaintenance` did not notify WebSocket transport | Added `GatewayWebSocketHandler.closeClosedGatewaySessions()` and called it from maintenance. |

## Implementation Notes

- `/ws/gateway` is registered as a raw WebSocket endpoint.
- The transport is an adapter over `InMemoryGatewayService`.
- `InMemoryGatewayService` now supports event listeners so transport can push newly published events.
- WebSocket messages currently support `IDENTIFY`, `HEARTBEAT`, and `RESUME`.
- Delivery authorization remains in `InMemoryGatewayService.poll`, so REST polling and WebSocket push share the same channel visibility checks.

## Residual Risks

- `SUBSCRIBE` from the design is not implemented yet; current delivery uses session-level authorized events.
- Frontend WebSocket client/Pinia integration and Playwright two-browser browser-level smoke remain future work.
- Event listener callbacks currently run in-process; T40 must replace this with cross-node fanout.
- Resume replay is bounded by in-memory process state only.
- WebSocket payload format is intentionally minimal and not Discord Gateway compatible.
