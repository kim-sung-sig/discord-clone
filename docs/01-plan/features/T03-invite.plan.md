# T03 Invite Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Slice: T03 invite

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | guild membership이 수동 setup endpoint에만 의존하면 Discord의 핵심 유입 경로인 invite accept/preview/max-use 정책을 검증할 수 없다. |
| Solution | in-memory invite domain/API와 Nuxt invite modal을 TDD로 추가한다. |
| Function UX Effect | 사용자는 invite preview와 accept flow를 화면/테스트에서 확인할 수 있다. |
| Core Value | 이후 friend/message/voice 기능이 신뢰 가능한 membership join flow 위에서 동작한다. |

## Scope

- invite create/delete/preview/accept API
- max age expiration
- max uses enforcement
- accept idempotency for same member
- temporary membership flag
- role grant skeleton
- invite modal component test and e2e visibility

## Out Of Scope

- Database persistence
- Email/deep-link delivery
- Vanity invite URLs
- Audit log
- Full frontend-backend API integration

## Success Criteria

- expired invite rejects accept
- max uses rejects additional distinct users and is race-safe in service test
- accepting invite creates membership exactly once for the same user
- deleted invite cannot be previewed or accepted
- unauthorized invite create is rejected
- invite modal component and e2e tests pass

