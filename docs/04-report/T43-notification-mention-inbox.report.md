# T43 Notification & Mention Inbox Report

작성일: 2026-05-18  
PDCA Phase: Report  
Slice: T43 Notification & Mention Inbox

## Summary

T43 added the first backend notification inbox domain slice. It supports mention, DM, and server notification items, category preferences, hidden-channel mention filtering, unread counts, and read-marker style mutation by channel sequence.

## Delivered

- Added `backend:modules:notification`.
- Added `InMemoryNotificationInboxService`.
- Added notification item, kind, preferences, and mention command contracts.
- Added deterministic unit tests for:
  - visible mention creation,
  - hidden mention filtering,
  - preference suppression,
  - DM/server unread count,
  - newest-first ordering,
  - read-marker clearing up to sequence.
- Added T43 Plan/Design/Analysis/Report/Feedback documents.

## Loop Cycle Output

```text
계획 검토 됨
> 구현 계획 수립 됨 (backend notification domain skeleton, hidden mention filter, read-state contract)
> 구현 진행 함
> 리뷰 진행
> 리뷰 검토 (6개 지표 26/30)
> 기준점 통과
> 다음 계획 진행 가능
```

## Verification

- `.\\gradlew.bat --no-daemon :backend:modules:notification:test --tests com.example.discord.notification.InMemoryNotificationInboxServiceTest`: RED then PASS
- `.\\gradlew.bat --no-daemon :backend:modules:notification:test :backend:boot:compileJava`: PASS

## Residual Risks

- No REST API or frontend inbox UI yet.
- No PostgreSQL persistence yet.
- No PWA/browser notification adapter yet.
- Mention extraction still happens upstream; notification module receives parsed mentioned ids.

## Next Recommended Task

Continue T43 with notification REST API and web notification center integration, or proceed to T44 if product breadth is preferred over deepening notification.
