# T02B Role/Permission Management Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-B role/member/overwrite hardening

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T02-A는 guild/channel/permission foundation만 제공하며 role CRUD, member role assignment, overwrite API, 관리 UI가 없어 실제 권한 운영 시나리오를 검증할 수 없다. |
| Solution | in-memory service를 유지하되 role 생성/권한 갱신/멤버 역할 부여/채널 role overwrite API와 Nuxt role-permission panel을 TDD로 추가한다. |
| Function UX Effect | 사용자는 guild shell에서 역할, 채널 권한, 멤버 역할 상태를 확인할 수 있고 테스트는 채널 권한 변경 효과를 검증한다. |
| Core Value | 이후 메시지/초대/보이스 기능이 동일한 권한 기반 위에서 read/write/join 접근 제어를 재사용할 수 있다. |

## Scope

- Backend role listing and creation API.
- Backend role permission update API.
- Backend member role assignment API.
- Backend channel role overwrite API.
- Backend visibility regression proving overwrite denies hide channels.
- Frontend role/permission state model and management panel.
- Component/e2e assertions for role and overwrite UI state.

## Out Of Scope

- Database persistence.
- Role hierarchy position editing.
- Authenticated current-user authorization guard.
- Member/user directory API.
- Category ordering UI.

## Success Criteria

- Permission truth table retains administrator bypass and overwrite precedence regressions.
- REST tests prove role creation, permission update, member assignment, overwrite deny, and visibility filtering.
- Nuxt component test proves role panel renders role, permission, member assignment, and channel overwrite state.
- Playwright e2e proves the role management panel is visible in the app shell.
- Full backend tests, frontend component tests, frontend e2e, frontend build, and compose config pass.

## Failure Criteria

- Role deny cannot hide a channel from visible-channel API.
- API accepts missing permission/role/member/channel identifiers.
- UI claims role/permission state that is not represented in store data.
- Component or e2e tests only assert static compile success.

