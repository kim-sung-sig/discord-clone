# T02C Guild Authorization Boundary Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-C authorization boundary

## Verification Matrix

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Backend targeted RED/GREEN | `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest` | Pass | RED failed before auth gate, permission ceiling, and interceptor; final targeted run `BUILD SUCCESSFUL in 10s`. |
| Backend fresh full gate | `.\gradlew.bat test --rerun-tasks` | Pass | `BUILD SUCCESSFUL in 21s`; 25 actionable tasks executed. |
| Frontend component regression | `npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts` | Pass | 2 test files passed, 5 tests passed. |
| Frontend e2e regression | `npm run e2e -w apps/web` | Pass | 4 Playwright tests passed. |
| Frontend build regression | `npm run build -w apps/web` | Pass | Nuxt production build completed with known upstream sourcemap/deprecation warnings. |
| Docker compose config | `docker compose -f infra/docker/docker-compose.yml config` | Pass | Compose rendered `name: discord-clone`. |

## RED Evidence

- `POST /api/guilds` without bearer token initially returned success instead of `401`.
- Non-owner role mutation initially returned success instead of `403`.
- `MANAGE_ROLES` did not exist in the permission model.
- Member add endpoint did not exist for testable guild membership setup.

## Implemented Scope

- Added `.bkit/` to `.gitignore` for local runtime state.
- Added `AuthenticatedUserResolver` for bearer access-token resolution.
- Guild creation now requires bearer auth and uses authenticated user as owner.
- Channel mutation requires owner or `MANAGE_CHANNELS`.
- Role mutation and member role assignment require owner or `MANAGE_ROLES`.
- Added `MANAGE_ROLES` permission bit.
- Added in-memory membership add endpoint for owner/member-management flows.
- Added permission ceiling so non-owner `MANAGE_ROLES` delegates cannot grant `ADMINISTRATOR` or assign roles above their own permission set.
- Added guild mutation authentication interceptor so missing/invalid bearer tokens are rejected before protected request bodies are deserialized.

## Review Findings And Closure

| Finding | Resolution |
| --- | --- |
| `MANAGE_ROLES` delegate could mint or assign `ADMINISTRATOR`. | Added permission ceiling checks for role creation, permission replacement, and role assignment. |
| Missing/invalid token coverage was thin. | Added invalid bearer and missing-token mutation tests. |
| Delegated channel overwrite authorization was not directly tested. | Added delegated `MANAGE_CHANNELS` overwrite success assertion. |
| Controller body validation happened before auth in method body. | Reordered controller methods to resolve authorization before request-body validation when the method is entered. |
| Spring could reject malformed JSON before controller auth checks. | Added `GuildMutationAuthenticationInterceptor` for POST/PUT/PATCH/DELETE `/api/guilds/**`. |
| Member add endpoint was missing from the design matrix. | Added `PUT /api/guilds/{guildId}/members/{memberId}` to design rules. |

## Gap List

- No database-backed persistence yet.
- Full Spring Security filter chain is not introduced yet; a scoped MVC interceptor protects guild mutations.
- Role hierarchy position checks are still not implemented.
- API error bodies for `401`/`403` use Spring defaults.
- Frontend remains static and is not API-connected.
