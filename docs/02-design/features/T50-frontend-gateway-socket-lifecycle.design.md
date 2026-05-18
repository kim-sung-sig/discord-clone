# T50 Frontend Gateway Socket Lifecycle Design

Date: 2026-05-18
Slice: T50 Frontend Gateway Socket Lifecycle

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

`createGatewaySocketLifecycle` is framework-light so it can be tested without browser rendering and later wrapped by a Vue composable.

Inputs:

- `url`
- `accessToken`
- `sessionId`
- `lastSequence`
- `heartbeatIntervalMs`
- `webSocketFactory`
- `onDispatch`

The lifecycle owns a socket and interval. `connect()` creates the socket and wires callbacks. `disconnect()` closes the socket and clears heartbeat. `sendHeartbeat()` is explicit for tests and for future visibility hooks.

Incoming frames are parsed as JSON and passed through `normalizeGatewayDispatch`; only valid dispatches reach shell reconciliation.
