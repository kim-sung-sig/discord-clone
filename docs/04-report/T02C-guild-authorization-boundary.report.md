# T02C Guild Authorization Boundary Report

작성일: 2026-05-13  
PDCA Phase: Report  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-C authorization boundary

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | T02-B mutation API는 권한 없는 사용자가 UUID만 알면 role/channel permission을 변경할 수 있었다. |
| Solution | bearer token resolver와 owner/manage permission gate를 controller에 연결했다. |
| Function UX Effect | API 호출자는 guild owner 또는 적절한 관리 권한을 가진 member여야 mutation을 수행할 수 있다. |
| Core Value | 메시지, 초대, 보이스 기능이 사용할 guild 권한 경계를 backend에서 강제하는 기반이 생겼다. |

## Implemented

- `AuthenticatedUserResolver`
- `MANAGE_ROLES` permission
- owner-based guild creation
- `401` for missing bearer token
- pre-body-binding bearer validation for guild mutation methods
- `403` for authenticated requester without guild manage permission
- owner bypass for guild mutation
- delegated `MANAGE_ROLES` role mutation
- delegated `MANAGE_CHANNELS` channel mutation
- `.bkit/` ignore rule
- non-owner role permission ceiling to block delegated `ADMINISTRATOR` escalation
- direct tests for invalid token, missing mutation token, delegated overwrite, and role escalation denial

## Verification Evidence

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

Results:

- Backend targeted: `BUILD SUCCESSFUL in 10s`
- Backend full: `BUILD SUCCESSFUL in 21s`; 25 actionable tasks executed
- Frontend component: 2 files passed, 5 tests passed
- Frontend e2e: 4 Playwright tests passed
- Frontend build: Nuxt production build completed
- Infra: Docker Compose config rendered successfully

## Next Slice

T02-D should replace in-memory guild/auth state with persistence-backed repository boundaries:

- schema for guild/member/role/channel/overwrite
- repository interfaces and adapters
- transaction semantics for role assignment and overwrite replacement
- migration/test data harness
