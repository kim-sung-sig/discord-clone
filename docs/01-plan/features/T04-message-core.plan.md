# T04 Message Core Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Slice: T04 message core

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 현재 채팅 화면은 정적 seed만 렌더링하며, 서버에는 메시지 작성/조회/수정/삭제 정책이 없어 Discord 핵심 UX를 검증할 수 없다. |
| Solution | in-memory message module/API와 Nuxt chat composer/message metadata UI를 TDD로 추가한다. |
| Function UX Effect | 사용자는 채널별 메시지, 멘션, 핀, 수정/삭제 상태, 작성 CTA를 화면과 테스트에서 확인할 수 있다. |
| Core Value | T05 Gateway와 이후 attachment/reaction/friend/DM 기능이 일관된 message domain 위에서 확장된다. |

## Scope

- message create/update/delete
- cursor pagination with stable ordering
- mention extraction for `@username` and `<@uuid>` tokens
- pin/unpin state
- edit history snapshots
- basic content search
- channel read/write permission gates
- chat viewport composer, pinned/edit/deleted metadata, component/e2e coverage

## Out Of Scope

- Database persistence
- Gateway fanout and sequence numbers
- Attachments, reactions, emoji, stickers
- DM/group DM message routing
- Full rich markdown rendering

## Success Criteria

- cursor pagination has no duplicate or missing messages
- users without `VIEW_CHANNEL` cannot list/search messages
- users without `SEND_MESSAGES` cannot create messages
- message author can edit/delete own messages
- `MANAGE_MESSAGES` can delete/pin others' messages
- mentions are extracted deterministically
- deleted messages remain as tombstones, not hard-hidden by default
- chat viewport component and e2e tests cover composer, edited, pinned, deleted, and mention UI

## Failure Criteria

- offset pagination only
- deleted message policy missing
- message mutation bypasses guild/channel permissions
- frontend composer mutates state without tests
- T05 Gateway cannot reuse message events because message model lacks lifecycle metadata
