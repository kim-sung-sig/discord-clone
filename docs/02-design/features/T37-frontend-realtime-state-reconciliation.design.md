# T37 Frontend Realtime State Reconciliation Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T37 Frontend Realtime State Reconciliation

## Architecture Decision

Keep realtime reconciliation inside shared frontend store boundaries, not inside individual Vue components. Components render normalized state and connection/error state; Pinia actions own mutation identity, optimistic state, Gateway event application, and resync triggers.

## State Model

Core state additions:

| Field | Owner | Purpose |
| --- | --- | --- |
| `latestGatewaySequence` | Gateway store | Highest contiguous processed sequence |
| `processedEventIds` | Gateway/message stores | Bounded duplicate suppression |
| `pendingMutations` | Feature stores | Optimistic writes waiting for REST/Gateway confirmation |
| `failedMutations` | Feature stores | User-visible failed optimistic writes |
| `resyncRequired` | Gateway store | Sequence gap or failed resume indicator |

Mutation identity:

```text
clientEventId = web-shell:{timestamp}:{random}
requestId = web-shell:{action}:{random}
gatewayEventId = {eventType}:{entityId}:{serverVersion or sequence}
```

The backend remains the authority. Client ids only correlate pending UI state with REST responses and Gateway echoes.

## Event Application Rules

1. Reject any event with sequence less than or equal to the last processed contiguous sequence unless it is explicitly part of a resume replay gap fill.
2. If sequence is greater than expected by more than one, set `resyncRequired`.
3. If event id is already in `processedEventIds`, ignore it.
4. If event correlates with a pending mutation, mark the mutation confirmed and merge server state.
5. If event conflicts with optimistic state, server state wins and the optimistic entry is cleared.
6. If REST mutation fails before Gateway confirmation, rollback optimistic state and record an accessible error.

## Feature Rules

### Messages

- Optimistic message appears with `status: "sending"` and `clientEventId`.
- REST success updates the message with server `messageId` when available.
- Gateway echo confirms the canonical message and clears pending state.
- REST failure marks message `failed` or removes it depending on action type.
- Delete/edit events use server version or event sequence to prevent stale rollback.

### Voice and Stage

- Join/leave/speaker actions enter pending control state.
- Gateway event confirms canonical state.
- Failed REST action clears pending state and restores previous known state.
- Stage speaker/audience transitions should never be inferred only from optimistic UI.

## Resync Strategy

When `resyncRequired` is set:

- Stop applying non-critical optimistic state transitions.
- Show compact connection/resync state in the app shell.
- Refetch current guild/channel/message/voice state through REST.
- Clear processed-event buffer only after refetch succeeds.
- Resume normal event processing from the latest server acknowledged sequence.

## Test Strategy

- Store unit tests for duplicate Gateway echo after REST success.
- Store unit tests for REST failure rollback.
- Store unit tests for out-of-order and gap events.
- Component tests for failed message and resync state accessibility.
- Playwright targeted smoke for two quick messages and reconnect/resume.

## Risks

- Too much reconciliation in components will create platform drift for T28~T30 surfaces.
- Bounded duplicate buffers can evict old ids; sequence handling must remain the primary guard.
- Server event payloads may lack version fields; sequence must be used until versioning is available.
