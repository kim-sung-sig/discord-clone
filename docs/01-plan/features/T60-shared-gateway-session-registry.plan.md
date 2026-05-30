# T60 Shared Gateway Session Registry And Cross-node RESUME Plan

Date: 2026-05-21

## Goal

Allow Gateway `RESUME` to work after a client reconnects to a different Gateway node, as long as the replay buffer still contains the missed events.

## Scope

- Introduce a `GatewaySessionRegistry` port.
- Keep the default local/test implementation in memory.
- Add a Redis-backed registry for the `redis` profile.
- Verify deterministic cross-node resume with two logical Gateway nodes sharing one session registry.
- Ensure shared session metadata does not contain access tokens, refresh tokens, LiveKit tokens, passwords, or other secrets.

## Acceptance Criteria

- Node A can identify a Gateway session and node B can resume the same session through the shared registry.
- Missed authorized events are replayed on node B.
- Session `lastDeliveredSequence` is updated in the shared registry after resume.
- Existing Gateway HTTP/WebSocket tests remain green.
- Redis registry stores only minimal non-secret metadata.
