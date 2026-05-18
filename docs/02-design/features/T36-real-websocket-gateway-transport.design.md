# T36 Real WebSocket Gateway Transport Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T36 Real WebSocket Gateway Transport

## Architecture Decision

Add a real WebSocket transport as an adapter over the existing Gateway application boundary. Domain events and delivery authorization stay server-side; the transport owns socket lifecycle, identify, heartbeat, sequence ack, and resume.

The design should keep three boundaries separate:

- Event production: REST/application services publish gateway events.
- Delivery authorization: Gateway service filters events by guild/channel visibility and session identity.
- Transport: WebSocket sessions serialize authorized events and manage lifecycle.

## WebSocket Message Contract

Client to server:

| Type | Purpose | Required Fields |
| --- | --- | --- |
| `IDENTIFY` | authenticate socket session | `accessToken`, optional `lastSequence` |
| `HEARTBEAT` | keep session alive | `lastSequence` |
| `RESUME` | reconnect with previous session | `sessionId`, `accessToken`, `lastSequence` |
| `SUBSCRIBE` | request guild/channel event scope | `guildId`, optional `channelId` |

Server to client:

| Type | Purpose | Fields |
| --- | --- | --- |
| `READY` | identify success | `sessionId`, `userId`, `sequence` |
| `HEARTBEAT_ACK` | heartbeat success | `sequence`, `serverTime` |
| `EVENT` | authorized Gateway event | `sequence`, `eventType`, `payload` |
| `RESUMED` | resume success | `sessionId`, `replayedCount`, `sequence` |
| `ERROR` | safe protocol error | `code`, `message`, optional `retryAfterMs` |

## Sequence and Resume

Sequence rules:

- Every outbound `EVENT` has a monotonically increasing sequence per Gateway session.
- Client sends last processed sequence through `HEARTBEAT` and `RESUME`.
- Server tracks last acknowledged sequence per session.
- Resume replays events with sequence greater than client `lastSequence` when still in buffer.
- If the buffer cannot satisfy resume, server sends a safe error requiring a fresh identify.

Initial buffer design:

- Use bounded in-memory replay buffer per session for T36.
- Keep the interface compatible with T40 so cross-node replay can move to Redis Streams or Redpanda.
- Never buffer payloads containing secrets, signed URLs, or media tokens.

## Authorization Model

Delivery checks happen at send time, not only subscribe time.

Rules:

- User must still be a guild member for guild-scoped events.
- User must be able to view the channel for channel-scoped events.
- Voice/stage/soundboard events follow the related channel visibility and permission policy.
- Subscription requests can narrow scope, but cannot grant access.
- Permission changes should affect subsequent delivery decisions.

## Heartbeat and Session Lifecycle

Default policy:

- Server expects heartbeat within a fixed interval plus tolerance.
- Missing heartbeat closes the socket and marks the session disconnected.
- Resume is allowed only within the configured resume window.
- Logout/session revoke should invalidate active WebSocket sessions for the user/session.

Failure behavior:

- Malformed protocol messages return `ERROR` and may close the socket after repeated violations.
- Authentication failures close the socket without leaking whether a user exists.
- Rate limit integration uses T35 gateway identify throttle.

## Frontend Integration

Add or extend a Gateway client wrapper used by Pinia stores:

- connects after authenticated app shell mount,
- sends `IDENTIFY`,
- tracks latest processed sequence,
- sends heartbeat,
- dispatches `EVENT` messages into existing store actions,
- exposes connection states: `connecting`, `ready`, `reconnecting`, `resyncRequired`, `offline`.

T37 will own deeper duplicate suppression and optimistic update reconciliation. T36 should still avoid obvious duplicate replay in the basic happy path.

## QA Strategy

- Backend unit tests for protocol validation, heartbeat timeout, sequence ack, and resume buffer behavior.
- Backend integration tests for hidden-channel non-delivery over WebSocket.
- Frontend unit tests for Gateway client state transitions.
- Playwright two-browser smoke:
  - browser A and browser B login,
  - both enter the same guild/channel,
  - browser A sends a message,
  - browser B receives it without page refresh,
  - browser A joins/leaves voice or changes stage state,
  - browser B sees the event,
  - hidden/unauthorized channel events remain absent.

## Risks

- WebSocket tests can be flaky if readiness is based on sleeps; use explicit READY/event wait conditions.
- Resume behavior is only as strong as the T36 buffer; T40 must replace it for cross-node support.
- Frontend state reconciliation is intentionally limited here; T37 should harden duplicate/order handling.
- Long-lived sockets must not bypass session revocation or rate limiting.
