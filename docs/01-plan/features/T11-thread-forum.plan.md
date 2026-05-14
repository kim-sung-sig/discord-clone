# T11 Thread/Forum Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T11 Thread/Forum

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 채널/메시지 기능은 있지만 thread/forum이 없어 대화 분기, archive, forum tag/guideline UX를 검증할 수 없다. |
| Solution | 신규 `thread` backend module에 public/private thread, archive/reopen, forum post/tag requirement를 구현하고 Nuxt shell에 forum/thread panel을 추가한다. |
| Function UX Effect | 사용자는 forum post/thread를 보고 tag requirement, archived 상태, reopen/write 가능 여부를 화면과 테스트에서 확인할 수 있다. |
| Core Value | T12 AutoMod/Audit 및 이후 moderation/reporting에서 참조할 thread lifecycle과 parent permission inheritance 기반을 만든다. |

## Scope

- Public/private thread skeleton scoped to parent guild channel.
- Parent channel permission inheritance: view/send checks depend on parent channel permissions.
- Auto archive: stale thread is archived with deterministic injected clock.
- Archive/reopen lifecycle: archived thread blocks writes until reopened by allowed user.
- Forum post/tag/guidelines/layout skeleton: forum channel has required tags and guidelines, posts require at least one allowed tag.
- Nuxt UI: forum panel with tags/guidelines, thread list, archived badge, reopen action.

## Out of Scope

- Real message persistence inside threads; thread write is a policy/receipt skeleton.
- Notification fanout and thread subscriptions.
- Full forum sorting/pinning/search.

## Success Criteria

- Thread permission inheritance test passes.
- Archive/reopen test passes.
- Forum tag requirement test passes.
- Forum UI e2e passes.
- Full backend/frontend gates pass.

## Failure Criteria

- Parent channel permissions are ignored.
- Archived thread accepts writes.
- Forum post can be created without required tag.
- Frontend forum UI is static and not store-action backed.

## Delivery Strategy

1. Backend thread domain TDD: thread creation, archive/reopen, forum tag validation.
2. Boot thread REST adapter TDD with parent permission inheritance through guild service.
3. Nuxt forum/thread UI TDD.
4. Full verification and PDCA documentation.
