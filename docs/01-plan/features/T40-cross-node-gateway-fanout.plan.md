# T40 Cross-node Gateway Fanout Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T40 Cross-node Gateway Fanout

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T36 WebSocket transport can work on one backend node, but production Gateway delivery must work when users are connected to different nodes. |
| Solution | Add a distributed fanout boundary using Redis Streams or Redpanda, plus node subscription registry, replay buffer, authorization checks, and duplicate suppression. |
| Function UX Effect | 사용자가 어떤 Gateway node에 붙어 있어도 메시지/voice/stage 이벤트가 동일하게 도착한다. |
| Core Value | realtime을 단일 JVM 기능에서 horizontally scalable Gateway control plane으로 확장한다. |

## Scope

- Define cross-node Gateway event bus abstraction.
- Choose Redis Streams or Redpanda for initial fanout implementation.
- Add gateway node subscription registry.
- Add replay buffer strategy shared with WebSocket resume.
- Preserve delivery-time authorization for guild/channel scoped events.
- Add duplicate suppression across bus redelivery.
- Add multi-node integration or harness simulation test.
- Record operational limits and follow-up scale work.

## Out of Scope

- Global multi-region fanout.
- Full Kafka/Redpanda production cluster tuning.
- Bot Gateway sharding.
- Voice RTP/media transport.
- Presence cluster dashboarding beyond events required for fanout.

## Success Criteria

- Two users connected to different Gateway nodes receive the same authorized channel event.
- Hidden channel users do not receive cross-node fanout events.
- Replay buffer and sequence support reconnect/resume across node boundaries within the supported window.
- Bus redelivery does not duplicate frontend-visible state.
- Fanout payloads contain no tokens, secrets, signed URLs, or raw object keys.
- Multi-node test or deterministic simulation passes in CI-compatible form.

## Failure Criteria

- Events pass on a single JVM but are lost between nodes.
- Cross-node fanout bypasses channel/guild permission checks.
- Redelivered bus events produce duplicate messages or voice/stage state.
- Fanout payload leaks media secrets, access tokens, or storage object keys.
- Resume only works when reconnecting to the same node.
