# T37 Frontend Realtime State Reconciliation Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T37 Frontend Realtime State Reconciliation

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `npm test -- --run tests/components/shell-contracts.test.ts` | PASS | Gateway frame normalization, optimistic REST reconciliation, rollback, gap detection, and bounded resync tests passed. |
| `npm test -- --run` | PASS | Full web Vitest component/contract suite passed: 47 tests. |
| `npm run build` | PASS | Nuxt production build completed. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| REST success plus Gateway echo does not duplicate messages | PASS | `reconciles REST message success with the matching Gateway echo without duplicating state`. |
| Failed optimistic message is removed or marked failed | PASS | `rolls back failed optimistic message mutations and keeps an accessible error`. |
| Out-of-order stale updates do not move state backward | PASS | Versioned `MESSAGE_UPDATE` with lower `serverVersion` leaves message body unchanged. |
| Sequence gap sets `resyncRequired` and supports bounded refetch | PASS | Gap at sequence 45 is rejected; `resyncChannelMessages(..., 25)` clears `resyncRequired`. |
| Replayed/duplicate events do not re-add processed state | PASS | Existing stale/duplicate sequence tests plus `processedEventIds` guard. |
| Frontend contract tests cover reconciliation behavior | PASS | Added targeted Vitest coverage in `shell-contracts.test.ts`. |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| WebSocket `EVENT` frame normalized as `EVENT` instead of domain event | T36 WebSocket transport wraps domain type in `eventType` | `normalizeGatewayDispatch` now maps `type: EVENT` + `eventType` to the domain type. |
| REST response and Gateway echo could create duplicate messages | Store lacked `clientEventId`/pending mutation correlation | Added optimistic message metadata and canonical merge by message id or `clientEventId`. |
| Failed REST sends left no structured mutation history | Store mutation path wrote only `apiError` | Added `pendingMutations` and `failedMutations`; failed message optimistic state is rolled back. |
| Sequence gaps were accepted as normal event progression | Store only rejected `sequence <= lastSequence` | Added contiguous sequence tracking and `resyncRequired`. |

## Implementation Notes

- Reconciliation is centralized in `apps/web/stores/shell.ts`, keeping Vue components free of realtime merge logic.
- Message optimistic creates now include `clientEventId`, `requestId`, and `status`.
- `processedEventIds` is bounded to the last 256 event ids; sequence remains the primary stale/duplicate guard.
- `MESSAGE_CREATE`, `MESSAGE_UPDATE`, and `MESSAGE_DELETE` Gateway dispatches now mutate message state.
- `resyncChannelMessages` performs a bounded REST refetch using the existing message list endpoint.

## Residual Risks

- A production WebSocket lifecycle composable is still needed to connect browser sockets, send heartbeats, resume, and call `applyGatewayDispatch`.
- Voice and stage optimistic reconciliation are not yet covered at the same depth as messages.
- Server payloads still lack a durable `serverVersion`; frontend uses optional version fields when present and sequence fallback otherwise.
- Cross-tab coordination is not implemented; multiple browser tabs could duplicate socket connections.
