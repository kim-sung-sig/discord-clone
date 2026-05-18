# T50 Frontend Gateway Socket Lifecycle Plan

Date: 2026-05-18
Slice: T50 Frontend Gateway Socket Lifecycle

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T50 closes the T37 P0 gap: the store can reconcile Gateway dispatches, but the browser still needs a socket lifecycle for connect, identify, heartbeat, resume, and dispatch routing.

## Implementation Plan

- Add gateway socket lifecycle helper in `gateway-client.ts`.
- Send `IDENTIFY` on first open.
- Send `RESUME` when prior session and last sequence exist.
- Send heartbeat frames with last sequence.
- Normalize incoming Gateway dispatches and route them to the provided handler.

## Acceptance Criteria

- Open socket sends identify or resume with token/session state.
- Heartbeat frame includes last sequence.
- Valid dispatch messages call the handler.
- Invalid dispatch messages are ignored.
