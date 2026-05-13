# T08 Presence/Typing/Read State Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T08 Presence/Typing/Read State

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | Gateway와 social/DM 기반은 생겼지만 online/idle/dnd/offline, typing TTL, read marker, unread count가 없어 실시간 커뮤니케이션 UX가 불완전하다. |
| Solution | 신규 `presence` module에 Redis-compatible TTL store port, typing expiry, read-state/unread 계산을 구현하고 Nuxt shell에 presence badge/typing/unread UI를 추가한다. |
| Function UX Effect | 사용자는 멤버 상태, typing indicator, 읽음 기준, unread badge를 화면과 테스트로 확인할 수 있다. |
| Core Value | T09 이후 attachment/message 확장과 Gateway fanout에서 재사용할 상태 TTL/read model 기반을 마련한다. |

## Scope

- Presence statuses: `ONLINE`, `IDLE`, `DND`, `OFFLINE`.
- Redis-compatible TTL semantics: heartbeat/update writes presence with expiry; expired keys read as offline.
- Typing event: channel/user typing indicator expires automatically.
- Read marker: per user/channel last read message sequence.
- Unread count: deterministic count of messages after read marker, excluding authored messages.
- Nuxt UI: status badges, typing line, unread badges in channel/DM lists.

## Out of Scope

- Real Redis Java client dependency; use a replaceable `PresenceTtlStore` port and in-memory Redis-compatible test adapter now.
- Gateway broadcast fanout; T08 stores/calculates state and surfaces it in UI.
- Cross-device read sync persistence; in-memory model only.

## Success Criteria

- TTL presence test proves status expires to offline.
- Typing expiry test proves stale typing indicators disappear.
- Unread count deterministic test covers own messages and read marker movement.
- UI component test covers status badge, typing indicator, and unread badge.
- Full gates pass.

## Delivery Strategy

1. Backend `presence` domain TDD: TTL store, typing, read marker, unread policy.
2. Boot REST adapter TDD for status/typing/read endpoints.
3. Nuxt shell TDD for badges and indicators.
4. Full verification and PDCA documentation.
