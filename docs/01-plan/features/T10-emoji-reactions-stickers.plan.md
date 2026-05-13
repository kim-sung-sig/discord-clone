# T10 Emoji/Reactions/Stickers Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T10 Emoji/Reactions/Stickers

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 메시지는 존재하지만 emoji CRUD, reaction 집계, sticker skeleton, expression 생성 권한이 없어 Discord의 감정 표현 UX를 검증할 수 없다. |
| Solution | 신규 `expression` backend module로 custom emoji/sticker/reaction 도메인을 만들고, `MANAGE_EXPRESSIONS` 권한 및 Nuxt reaction bar UI를 추가한다. |
| Function UX Effect | 사용자는 메시지에 reaction을 추가/제거하고 count를 확인하며 custom emoji/sticker metadata를 볼 수 있다. |
| Core Value | 이후 thread/forum, moderation, audit log에서 재사용할 expression permission/reaction idempotency 기반을 만든다. |

## Scope

- Emoji CRUD skeleton: create/list/delete custom emoji metadata scoped to guild.
- Reaction add/remove/list: idempotent duplicate add, deterministic count, user membership set.
- Sticker skeleton: create/list sticker metadata, no binary sticker processing.
- Expression permissions: custom emoji/sticker creation requires `MANAGE_EXPRESSIONS` or guild owner/admin.
- Nuxt UI: reaction buttons/counts on messages and expression catalog panel/skeleton.

## Out of Scope

- Real emoji image upload processing; T09 storage can be integrated later.
- Animated emoji/sticker transcoding.
- Cross-node reaction race handling through database locks; in-memory service must still be synchronized/idempotent.

## Success Criteria

- Duplicate reaction idempotency test passes.
- Custom emoji permission test passes.
- Reaction UI component test passes.
- Full backend/frontend gates pass.

## Failure Criteria

- User without `MANAGE_EXPRESSIONS` can create custom emoji/sticker.
- Duplicate reaction increments count twice.
- Remove reaction by non-reactor corrupts count.
- Frontend reaction UI is static and not backed by store actions.

## Delivery Strategy

1. Backend permission/domain TDD: add `MANAGE_EXPRESSIONS`, expression module, idempotent reaction aggregate.
2. Boot expression REST adapter TDD.
3. Nuxt reaction UI TDD.
4. Full verification and PDCA documentation.
