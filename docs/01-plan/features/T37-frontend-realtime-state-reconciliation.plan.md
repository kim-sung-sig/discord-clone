# T37 Frontend Realtime State Reconciliation Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T37 Frontend Realtime State Reconciliation

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T36에서 실제 WebSocket transport가 들어오면 REST mutation 응답, optimistic UI, Gateway echo event가 같은 상태를 여러 번 적용할 수 있다. |
| Solution | request id/client event id, sequence, entity version을 기준으로 Pinia store의 duplicate suppression, rollback, resync 상태를 명확히 만든다. |
| Function UX Effect | 사용자가 메시지를 빠르게 보내거나 reconnect되어도 메시지/voice/stage 상태가 중복되거나 순서가 뒤집히지 않는다. |
| Core Value | realtime UX를 "이벤트를 받는다" 수준에서 "상태가 항상 일관된다" 수준으로 올린다. |

## Scope

- Define frontend event identity contract: `requestId`, `clientEventId`, `gatewaySequence`, `entityId`.
- Add optimistic mutation state for message create/edit/delete and voice/stage actions.
- Suppress duplicate Gateway echo events after successful REST mutations.
- Roll back failed optimistic mutations with accessible error state.
- Detect sequence gaps and expose `resyncRequired` UI/store state.
- Add Pinia store tests for duplicate, out-of-order, rollback, and reconnect scenarios.
- Add Playwright regression for rapid message send and reconnect resume happy path.

## Out of Scope

- Backend WebSocket protocol implementation. That belongs to T36.
- Cross-node event ordering. That belongs to T40.
- Offline-first durable client queue.
- Conflict-free replicated data types.
- Native mobile-specific reconciliation beyond shared contract design.

## Success Criteria

- Message create REST success followed by Gateway echo does not duplicate messages.
- Failed optimistic message create is removed or marked failed with an accessible retry/error affordance.
- Out-of-order Gateway events do not move messages or voice/stage state backward.
- Sequence gap sets `resyncRequired` and triggers a bounded refetch path.
- Reconnect/resume does not re-add already processed events.
- Component/Pinia tests and targeted Playwright regression pass.

## Failure Criteria

- Rapid message send produces duplicate or reordered messages.
- Gateway reconnect replays already visible events as new state.
- Failed optimistic writes stay visible as successful state.
- Sequence gaps are silently ignored.
- UI shows success while backend rejected the action.
