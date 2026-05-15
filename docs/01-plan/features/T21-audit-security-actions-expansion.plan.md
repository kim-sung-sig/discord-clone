# T21 Audit/Security Actions Expansion Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T21 Audit/Security Actions Expansion

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | audit log는 AutoMod/premium 일부에만 존재하고, admin 검색/필터와 suspicious behavior alert 경계가 없다. |
| Solution | audit action을 role/message/invite/stage까지 확장하고, audit search query와 activity/security alert skeleton을 추가한다. |
| Function UX Effect | 관리자는 권한 변경, 초대 삭제, 메시지 moderation, stage moderation, AutoMod 이벤트를 조건별로 검색할 수 있다. |
| Core Value | privileged mutation은 추적 가능해야 하며, alert는 안전하지 않은 mutation 이후가 아니라 정책 판단 시점에 생성되어야 한다. |

## Scope

- Audit coverage for:
  - role assignment
  - invite delete
  - message delete/pin/unpin moderation
  - stage speaker/audience moderation
  - existing AutoMod and premium actions
- Admin audit search/filter API:
  - action
  - actorId
  - targetId
- Activity/Security Actions skeleton:
  - security alert record
  - alert query API
  - AutoMod block creates alert before mutation persistence

## Out of Scope

- SIEM integration.
- Risk scoring ML or fraud analytics.
- Persistent audit store/JDBC implementation.
- Full audit coverage for every remaining write endpoint in one pass.

## Success Criteria

- Privileged writes produce audit entries.
- AutoMod, role assignment, invite delete, message moderation, and stage moderation are searchable.
- Security action tests prove alert generation without false persistence mutation.

## Failure Criteria

- Admin action can mutate state without audit trace.
- Audit entries omit actor/target/action/time.
- Alerts are generated after unsafe mutation instead of before/around policy decision.
