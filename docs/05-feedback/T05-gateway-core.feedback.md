# T05 Gateway Core Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend review | New sessions could poll retained historical events. | Identify now initializes last delivered sequence at READY sequence. |
| Backend review | Timed-out sessions remained usable. | Closed sessions now reject heartbeat, poll, and resume while still allowing inspection. |
| Backend review | `/api/gateway/events` allowed any authenticated user to publish into any guild/channel. | Publish now requires guild membership and validates channel belongs to guild. |
| Frontend review | Gateway event guard accepted stale sequence numbers lower than current last sequence. | `recordGatewayEvent` now rejects `sequence <= lastSequence`; component regression test added. |

## Known Non-Blocking Risks

- Real WebSocket transport is deferred; T05 validates protocol semantics through a testable adapter.
- Cross-node gateway resume storage is deferred.
- Redis/Kafka fanout is deferred.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
