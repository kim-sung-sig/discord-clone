# T36 Real WebSocket Gateway Transport Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T36 Real WebSocket Gateway Transport

## Summary

T36 added a real backend WebSocket Gateway transport at `/ws/gateway`. Clients can identify with an access token, receive `READY`, send heartbeat and receive `HEARTBEAT_ACK`, receive authorized published events without polling, and resume a short disconnected session to replay missed events.

## Delivered

- Added `spring-boot-starter-websocket`.
- Added `/ws/gateway` WebSocket registration.
- Added `GatewayWebSocketHandler` for `IDENTIFY`, `HEARTBEAT`, and `RESUME`.
- Added Gateway event listener support so published events can push to active WebSocket clients.
- Extended heartbeat maintenance to close stale WebSocket sockets after closing stale Gateway sessions.
- Made Gateway heartbeat timeout configurable through `discord.gateway.heartbeat-timeout-ms`.
- Added real WebSocket integration tests using `StandardWebSocketClient`.

## Verification

- `.\\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayWebSocketIntegrationTest --no-daemon`: PASS
- `.\\gradlew.bat :backend:boot:test --no-daemon`: PASS
- `.\\gradlew.bat test --no-daemon`: PASS

## Coverage

- WebSocket `IDENTIFY -> READY`
- WebSocket `HEARTBEAT -> HEARTBEAT_ACK`
- REST publish to WebSocket `EVENT`
- hidden-channel non-delivery over WebSocket
- short disconnect `RESUME` replay
- heartbeat timeout closes stale sockets

## Residual Risks

- Frontend WebSocket client and Playwright two-browser browser-level smoke are not implemented in this slice.
- `SUBSCRIBE` scope narrowing is not implemented yet.
- Cross-node fanout remains in T40 scope.
- Resume replay remains in-process and should be extracted before distributed Gateway work.

## Next Recommended Task

Proceed to T37 Frontend Realtime State Reconciliation. The backend transport now exists; the next risk is frontend store behavior when REST writes and WebSocket echo events arrive in different orders or duplicate each other.
