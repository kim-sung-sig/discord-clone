# T02B Role/Permission Management Report

작성일: 2026-05-13  
PDCA Phase: Report  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-B role/member/overwrite hardening

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | T02-A foundation만으로는 역할 권한 변경, 멤버 역할 부여, 채널 overwrite를 API/화면에서 검증할 수 없었다. |
| Solution | role/member/overwrite REST API와 Nuxt role-permission panel을 추가하고, invalid payload와 overlay 회귀를 테스트로 고정했다. |
| Function UX Effect | shell 내부에서 Moderator 권한, 멤버 역할 할당, active channel overwrite 상태를 확인할 수 있다. |
| Core Value | 메시지/초대/보이스 기능이 이어서 사용할 권한 운영 API와 UI 검증 기반을 확보했다. |

## Implemented

- `GET /api/guilds/{guildId}/roles`
- `POST /api/guilds/{guildId}/roles`
- `PUT /api/guilds/{guildId}/roles/{roleId}/permissions`
- `PUT /api/guilds/{guildId}/members/{memberId}/roles/{roleId}`
- `PUT /api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}`
- JSON error response for unreadable/null request bodies.
- Role permission panel mounted inside the shell workspace.
- Store models for roles, members, and permission overwrites.

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

- Backend targeted: `BUILD SUCCESSFUL in 6s`
- Backend full: `BUILD SUCCESSFUL in 18s`; 25 actionable tasks executed
- Frontend component: 2 files passed, 5 tests passed
- Frontend e2e: 4 Playwright tests passed
- Frontend build: Nuxt production build completed
- Infra: Docker Compose config rendered successfully

## Next Slice

T02-C should address persistence and authorization integration before message write access depends on guild permission state:

- persist guild/role/channel/member/overwrite state
- enforce requester identity on role/permission mutation APIs
- add `MANAGE_ROLES` and `MANAGE_CHANNELS` authorization tests
- connect frontend shell/panel to API data
