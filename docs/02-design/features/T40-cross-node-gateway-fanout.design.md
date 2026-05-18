# T40 Cross-node Gateway Fanout Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T40 Cross-node Gateway Fanout

## Architecture Decision

Introduce a Gateway event bus port and a node-local delivery adapter. Application services publish one sanitized domain event into the bus. Each Gateway node consumes relevant events, applies delivery authorization for its connected sessions, assigns session-local sequences, and sends events over WebSocket.

Preferred first implementation: Redis Streams.

Reasoning:

- T35 already introduces Redis as required production runtime infrastructure.
- Redis Streams provide consumer groups and bounded replay without introducing a second broker if Redpanda is not already required for the current runtime.
- The port should remain broker-neutral so Redpanda can replace or supplement Redis when event volume requires it.

## Component Boundaries

| Component | Responsibility |
| --- | --- |
| `GatewayEventBus` | publish/consume sanitized cross-node events |
| `GatewayNodeRegistry` | track node id, heartbeat, and active subscription metadata |
| `GatewaySessionRegistry` | map connected sessions to user/guild/channel scopes on each node |
| `GatewayReplayBuffer` | keep bounded event history for resume |
| `GatewayDeliveryAuthorizer` | decide if a session can receive a bus event |
| WebSocket transport | serialize authorized events and manage socket lifecycle |

## Event Flow

```text
REST mutation
  -> application event
  -> sanitize payload
  -> GatewayEventBus.publish(stream key by guild/channel)
  -> each node consumer receives event
  -> node finds candidate sessions
  -> delivery-time authorization check
  -> assign session-local sequence
  -> append replay buffer
  -> send WebSocket EVENT
```

## Stream Key Strategy

Initial Redis Streams:

```text
gateway:guild:{guildId}
gateway:channel:{channelId}
gateway:user:{userId}
gateway:global
```

Rules:

- Use channel stream for channel-scoped message/voice/stage/soundboard events.
- Use guild stream for guild membership/channel/role events.
- Use user stream for private/session/account events.
- Use global only for low-volume operational events.

## Payload Safety

Fanout payloads must be sanitized before publish:

- no access tokens,
- no refresh tokens,
- no LiveKit/media tokens,
- no signed upload/download URLs,
- no raw storage object keys,
- no passwords or secrets,
- no full request headers.

Use opaque ids and require clients to fetch privileged details through authorized REST endpoints.

## Registry and Heartbeat

Node registry:

```text
gateway:node:{nodeId} -> heartbeat timestamp, version, startedAt
```

Session registry remains node-local for T40 unless cross-node targeted delivery requires shared lookup. Shared session metadata should include only minimal non-secret routing data.

Node heartbeat expiry should not delete durable events; it only removes stale delivery candidates.

## Replay and Resume

T40 should replace T36 in-memory-only replay with broker-backed or Redis-backed replay:

- Session-local sequence remains the frontend contract.
- Bus event id is stored alongside session sequence.
- Resume can recover after reconnecting to a different node if the bus event remains within retention.
- If retention is insufficient, client receives `resyncRequired`.

## Duplicate Suppression

Deduplication keys:

```text
{eventType}:{aggregateId}:{serverVersion or busEventId}
```

Server-side:

- avoid sending the same bus event twice to the same active session when consumer redelivery occurs.

Client-side:

- T37 duplicate suppression remains the final UI guard.

## QA Strategy

- Unit tests for payload sanitizer.
- Unit tests for delivery authorizer with hidden-channel scenarios.
- Integration test with two logical Gateway node instances sharing Redis Streams.
- Resume test reconnecting to a different node within retention.
- Redelivery test proving duplicate bus delivery does not duplicate WebSocket-visible state.

## Risks

- Redis Streams retention settings can drop replay data too early; expose retention as config.
- Shared registry data must avoid leaking sensitive session details.
- Multi-node tests can become slow; keep a deterministic simulation path for CI.
- Redpanda may still be needed for higher-throughput event history; keep the bus port broker-neutral.
