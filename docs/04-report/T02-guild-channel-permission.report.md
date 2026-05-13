# T02 Guild/Channel/Permission Report

작성일: 2026-05-13  
PDCA Phase: Report  
Slice: T02-A foundation

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 서버/채널/권한 기반이 없으면 이후 메시지, 초대, Gateway 이벤트의 접근 제어를 검증할 수 없다. |
| Solution | guild/channel modules와 permission calculator를 추가하고, visible channel API와 frontend shell state를 테스트로 고정했다. |
| Function UX Effect | Nuxt shell이 guild, text channel, voice channel, active channel을 명시적으로 렌더링한다. |
| Core Value | 권한 계산과 채널 가시성 회귀를 backend/frontend 테스트로 잡을 수 있는 기반을 확보했다. |

## Implemented

- `ChannelType`
- `Guild`, `GuildMember`, `Role`, `Channel`
- `InMemoryGuildService`
- `PermissionOverwrite`
- `RolePermission`
- `EffectivePermissionCalculator`
- `POST /api/guilds`
- `POST /api/guilds/{guildId}/channels`
- `GET /api/guilds/{guildId}/channels/visible?memberId=...`
- Nuxt shell guild/channel state model
- App shell component/e2e assertions for guild/channel structure
- Review hardening for overwrite precedence, invalid channel request handling, architecture import guard, accessible channel navigation, channel-scoped messages, stable test ids, and hydration-safe interactions

## Verification Evidence

```powershell
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

Results:

- Backend: `BUILD SUCCESSFUL in 27s`; 25 actionable tasks executed
- Frontend component: 2 files passed, 4 tests passed
- Frontend e2e: 4 Playwright tests passed
- Frontend build: Nuxt production build completed
- Infra: Docker Compose config rendered successfully

## Review Closure

| Finding | Resolution |
| --- | --- |
| `@everyone` allow overrode role deny in same-channel overwrite evaluation. | Permission calculator now applies `@everyone` overwrite first, then member role denies/allows. |
| Missing channel `type` could surface as an internal error. | Controller validates null `type` and maps invalid requests to 400. |
| Module architecture rule did not catch boot adapter imports reliably. | Added source-level architecture guard for controller/configuration/advice imports under backend modules. |
| Channel entries were not keyboard/click accessible. | Channel entries are buttons with focus styling and disabled hydration state. |
| Chat viewport rendered global seed messages regardless of active channel. | Store now exposes `activeMessages` filtered by selected channel. |
| Channel selectors used mutable name-based test ids. | Test ids now use stable channel ids. |
| E2E channel switching could click before hydration completed. | Sidebar exposes hydration status and disables buttons until mounted. |

## Next Slice

T02-B should harden guild/channel/permission:

- persistence schema/entities
- role CRUD API
- member role assignment API
- permission overwrite API
- frontend API integration for guild/channel list
- channel ordering/category hierarchy
