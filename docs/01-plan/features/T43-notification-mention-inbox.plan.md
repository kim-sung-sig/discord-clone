# T43 Notification & Mention Inbox Plan

작성일: 2026-05-18  
PDCA Phase: Plan  
Slice: T43 Notification & Mention Inbox

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 메시지, DM, 서버 이벤트가 늘어났지만 사용자가 읽지 않은 mention/DM/server 알림을 한 곳에서 확인하고 read marker로 정리하는 inbox 경계가 없다. |
| Solution | notification inbox domain skeleton을 추가하고 mention/DM/server unread item, preference skeleton, read-state 연동 계약을 구현한다. |
| Function UX Effect | 사용자는 자신에게 온 mention/DM/server 알림을 일관된 count와 inbox에서 볼 수 있고, 채널을 읽으면 관련 unread가 정리된다. |
| Core Value | hidden channel mention 유출을 막으면서 향후 PWA/browser notification과 notification center UI를 붙일 수 있는 backend contract를 만든다. |

## Scope

- Add backend notification module.
- Add notification inbox item model for mention, DM, and server events.
- Add preference skeleton for mention/DM/server notification categories.
- Add read marker integration method that marks channel notifications read up to a sequence.
- Add hidden-channel safety contract: only visible recipients can receive mention items.
- Add deterministic unit tests for inbox ordering, unread counts, preferences, and hidden mention filtering.
- Record follow-up tasks for REST API, frontend inbox UI, PWA/browser notification adapter, and persistence.

## Out of Scope

- Push notification provider integration.
- Browser notification permission UI.
- Persistent PostgreSQL notification table.
- Full moderation/audit notification center.
- Mobile native push token registration.

## Success Criteria

- Mention notification appears for visible mentioned recipients.
- Hidden-channel mentioned users do not receive inbox items.
- DM/server notification categories contribute to unread count.
- Disabled preference category does not create new inbox items.
- `markChannelRead` reduces unread count for items at or before the read sequence.
- Notification tests pass without external services.

## Failure Criteria

- Hidden channel mention appears in notification inbox.
- Reading a channel does not reduce mention/unread count.
- Preferences exist only in docs and do not affect item creation.
- Notification item order is nondeterministic.
