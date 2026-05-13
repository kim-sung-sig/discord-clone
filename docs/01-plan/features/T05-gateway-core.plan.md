# T05 Gateway Core Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Slice: T05 gateway core

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 메시지 lifecycle은 생겼지만 gateway 세션, heartbeat, event sequence, resume 정책이 없어 실시간 Discord UX를 검증할 수 없다. |
| Solution | in-memory gateway protocol core와 Nuxt gateway store/status panel을 TDD로 추가한다. |
| Function UX Effect | 사용자는 READY/heartbeat/resume/event 상태를 화면에서 확인하고, 테스트는 중복 이벤트와 권한 없는 fanout을 차단한다. |
| Core Value | 이후 실제 WebSocket transport, presence, typing, voice signaling이 동일한 gateway session model 위에 올라갈 수 있다. |

## Scope

- authenticated gateway identify
- READY payload
- heartbeat ACK and timeout detection
- monotonic event sequence
- reconnect/resume from last sequence
- message/channel/guild fanout skeleton
- unauthorized channel event subscription filtering
- Nuxt gateway store/status UI and tests

## Out Of Scope

- Production WebSocket scaling
- Redis pub/sub or Kafka fanout
- Binary voice gateway protocol
- Cross-node resume store
- Mobile push notification delivery

## Success Criteria

- heartbeat timeout test
- reconnect/resume integration test
- duplicate event sequence is not re-applied
- unauthorized channel events are not delivered
- Nuxt gateway store test covers READY, HEARTBEAT_ACK, RESUMED, and event append

## Failure Criteria

- session resume impossible
- same event can be applied twice
- user receives hidden channel event
- heartbeat timeout state is not observable
