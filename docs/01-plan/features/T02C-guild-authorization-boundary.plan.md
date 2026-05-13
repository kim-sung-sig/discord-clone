# T02C Guild Authorization Boundary Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-C authorization boundary

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T02-B의 role/permission mutation API는 인증된 requester 검증 없이 UUID만 알면 호출 가능하다. |
| Solution | Auth access token을 guild controller에 연결하고 owner 또는 권한 보유자만 guild/channel/role mutation을 수행하도록 gate를 추가한다. |
| Function UX Effect | 아직 UI는 static seed지만 backend API는 이후 메시지/초대/보이스에서 재사용 가능한 권한 경계를 갖는다. |
| Core Value | Discord clone의 보안 핵심인 guild 권한 모델을 API 레벨에서 강제해 다음 기능의 신뢰 기반을 만든다. |

## Scope

- Ignore local `.bkit/` runtime state.
- Add authenticated user resolver for Bearer access tokens.
- Require authentication for guild creation and guild mutation APIs.
- Use authenticated requester as guild owner on create.
- Allow guild owner to manage channels, roles, member role assignment, and overwrites.
- Add `MANAGE_ROLES` permission.
- Allow non-owner members with `MANAGE_ROLES` or `MANAGE_CHANNELS` to perform matching operations.
- Return `401` for missing/invalid token and `403` for authenticated but unauthorized requester.

## Out Of Scope

- Database persistence.
- Frontend API integration.
- Spring Security filter chain.
- Role hierarchy position comparisons.
- Audit log.

## Success Criteria

- Owner token can create guild and mutate guild/channel/role state.
- Missing token on mutation returns `401`.
- Non-owner without permission returns `403`.
- Member with `MANAGE_ROLES` can create/update/assign roles.
- Member with `MANAGE_CHANNELS` can create channels and update channel overwrites.
- Existing auth/logout behavior still passes.

