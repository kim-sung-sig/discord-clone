# T37 Frontend Realtime State Reconciliation Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T37 Frontend Realtime State Reconciliation

## Summary

T37 added frontend store-level realtime reconciliation for message state. The Nuxt shell can now correlate optimistic REST sends with Gateway echoes, reject stale/duplicate events, detect Gateway sequence gaps, and recover with a bounded channel message refetch.

## Delivered

- Normalized T36 WebSocket `EVENT` frames into domain Gateway dispatches.
- Added message mutation metadata: `status`, `clientEventId`, `requestId`, and optional `serverVersion`.
- Added store state for `latestGatewaySequence`, `processedEventIds`, `pendingMutations`, `failedMutations`, and `resyncRequired`.
- Added optimistic `sendBackendMessage` behavior with rollback on REST failure.
- Added Gateway handlers for `MESSAGE_CREATE`, `MESSAGE_UPDATE`, and `MESSAGE_DELETE`.
- Added bounded `resyncChannelMessages` action.
- Added contract tests for WebSocket frame normalization, duplicate suppression, rollback, sequence gap detection, stale update prevention, and bounded resync.

## Verification

- `npm test -- --run tests/components/shell-contracts.test.ts`: PASS
- `npm test -- --run`: PASS
- `npm run build`: PASS

## Coverage

- REST message create with `clientEventId`
- Gateway echo correlation by canonical id and `clientEventId`
- Failed optimistic message rollback
- Accessible backend error path via existing `apiError`
- Contiguous sequence tracking and gap detection
- Bounded message snapshot refetch
- Stale versioned update rejection

## Residual Risks

- Browser WebSocket lifecycle is not yet wired into a Nuxt composable.
- Voice/stage optimistic reconciliation remains a follow-up.
- No Playwright multi-browser realtime smoke yet.
- Server-side event payloads should eventually include durable entity version fields.

## Next Recommended Task

Proceed to existing T38 CSP Reporting and Style Policy Hardening to keep the planned T30-T49 sequence intact. A separate T50 candidate was registered for the frontend Gateway socket lifecycle: connect `/ws/gateway`, identify with access token, heartbeat with last sequence, resume after reconnect, and route received frames into `shell.applyGatewayDispatch`.
