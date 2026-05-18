# T36 Real WebSocket Gateway Transport Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T36 Real WebSocket Gateway Transport

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T05/T18은 Gateway semantics와 event broadcast를 만들었지만, 현재 QA는 polling/skeleton 경계가 강하고 실제 WebSocket transport의 reconnect/resume 품질을 충분히 보장하지 않는다. |
| Solution | WebSocket identify/auth, heartbeat, sequence, resume, channel authorization, two-browser realtime smoke를 Gateway의 production transport 기준으로 승격한다. |
| Function UX Effect | 한 브라우저에서 보낸 메시지, voice/stage 상태 변경이 다른 브라우저에 실시간으로 도착하고 reconnect 이후에도 중복/누락 없이 복구된다. |
| Core Value | Discord-like 제품의 핵심인 realtime delivery를 REST/polling 보조 경로가 아니라 검증된 WebSocket 계약으로 만든다. |

## Scope

- Add real WebSocket Gateway endpoint for browser clients.
- Implement identify/auth handshake with access token validation.
- Implement heartbeat/heartbeat ack and timeout close behavior.
- Add gateway sequence numbers to outbound events.
- Add bounded resume support using last acknowledged sequence.
- Enforce channel/guild authorization on event delivery.
- Keep existing Gateway poll/test utilities as compatibility helpers where useful.
- Add frontend WebSocket client path behind the existing gateway store boundary.
- Add two-browser Playwright realtime smoke for message, voice, and stage event propagation.

## Out of Scope

- Cross-node fanout. That belongs to T40.
- Persistent replay buffer beyond bounded in-process or Redis-backed candidate buffer.
- Full Discord Gateway protocol compatibility.
- Voice RTP/media plane. That belongs to T41.
- Bot Gateway support.

## Success Criteria

- A browser client can identify over WebSocket and receive READY or equivalent initial state.
- Heartbeat timeout closes stale sessions deterministically.
- Message create in one browser produces a WebSocket event in another authorized browser.
- Voice/stage/soundboard control-plane events are delivered through WebSocket without leaking media tokens or secrets.
- Reconnect/resume after a short disconnect does not duplicate or drop already acknowledged events within the supported buffer.
- Hidden channel users do not receive channel-scoped events.
- Playwright two-browser realtime smoke passes locally and through the real-backend QA harness where feasible.

## Failure Criteria

- WebSocket accepts unauthenticated or expired-token identify requests.
- Hidden channel events are delivered to unauthorized sessions.
- Reconnect causes duplicate messages or stale voice/stage state.
- Heartbeat failures leave sessions alive indefinitely.
- Gateway event payloads include access tokens, LiveKit tokens, signed URLs, or object keys.
