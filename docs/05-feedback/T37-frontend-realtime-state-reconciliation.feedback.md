# T37 Frontend Realtime State Reconciliation Feedback

작성일: 2026-05-17  
PDCA Phase: Act  
Slice: T37 Frontend Realtime State Reconciliation

## Decisions

- Keep reconciliation in the Pinia shell store instead of Vue components.
- Use `clientEventId` for local optimistic correlation and existing request id for REST traceability.
- Reject Gateway sequence gaps before applying state so the UI does not advance over missing events.
- Use bounded REST message refetch as the first resync mechanism.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| T36 WebSocket transport emits `EVENT` wrapper frames. | Frontend normalizer unwraps `eventType` into the domain event type. |
| REST success and Gateway echo can represent the same mutation. | Store now merges by canonical message id or `clientEventId`. |
| Failed REST sends need visible recovery state. | Store records failed mutations and removes optimistic message state. |
| Gap handling needed to be explicit. | Store sets `resyncRequired` and exposes bounded channel resync. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T50 frontend Gateway socket lifecycle composable | P0 | Store reconciliation exists, but the browser still needs the actual WebSocket connect/heartbeat/resume loop. |
| Message edit/delete optimistic mutation APIs | P1 | Gateway update/delete reconciliation exists, but frontend REST mutation actions for edit/delete are not yet optimistic. |
| Voice/stage optimistic reconciliation tests | P1 | T37 focused message state; voice/stage actions need the same duplicate/rollback coverage. |
| Entity version contract in backend Gateway payloads | P1 | Frontend supports `serverVersion`, but backend events should emit durable versions consistently. |
| Cross-tab Gateway connection coordination | P2 | Multiple tabs can each open a socket; a BroadcastChannel-backed leader strategy would reduce duplicate traffic. |

## Verification

- `npm test -- --run tests/components/shell-contracts.test.ts`: PASS
- `npm test -- --run`: PASS
- `npm run build`: PASS
