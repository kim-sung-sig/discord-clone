# T02B Role/Permission Management Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-B role/member/overwrite hardening

## Verification Matrix

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Backend targeted RED/GREEN | `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest` | Pass | RED failed on missing/invalid role-overwrite behavior; final targeted run `BUILD SUCCESSFUL`. |
| Backend fresh full gate | `.\gradlew.bat test --rerun-tasks` | Pass | `BUILD SUCCESSFUL in 18s`; 25 actionable tasks executed. |
| Frontend component gate | `npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts` | Pass | 2 test files passed, 5 tests passed. |
| Frontend e2e gate | `npm run e2e -w apps/web` | Pass | 4 Playwright tests passed. |
| Frontend build gate | `npm run build -w apps/web` | Pass | Nuxt production build completed with known upstream sourcemap/deprecation warnings. |
| Docker compose config | `docker compose -f infra/docker/docker-compose.yml config` | Pass | Compose rendered `name: discord-clone`. |

## RED Evidence

- Backend REST test failed before role/overwrite endpoints existed.
- Backend review regression test failed for JSON `null` request body returning empty default 400 body instead of project error response.
- Frontend component test failed because `role-permission-panel` was mounted outside `workspace`.

## Implemented Scope

- Added role list/create API.
- Added role permission replacement API.
- Added member role assignment API.
- Added channel role overwrite replacement API.
- Added invalid body and null permission payload handling with JSON error response.
- Added Nuxt role permission panel with role, member assignment, and active channel overwrite state.
- Moved role permission panel into the shell workspace grid to avoid fixed overlay overlap.

## Review Findings And Closure

| Finding | Resolution |
| --- | --- |
| Role/permission mutation endpoints have no authorization gate. | Documented as out-of-scope for T02-B; must be handled when auth context and guild permission checks are wired into controllers. |
| Null JSON request bodies could return default 400 without JSON error shape. | Added request guard and `HttpMessageNotReadableException` handler. |
| Overwrite `allow`/`deny` null payloads were not tested. | Added REST regression test for null overwrite permission list. |
| Role panel used fixed overlay and could cover shell content. | Mounted panel inside `pages/app.vue` workspace grid and added component/e2e assertions for workspace containment. |
| Overwrite `deny: null` was handled but not directly asserted. | Added direct `deny: null` regression assertion. |

## Gap List

- Mutating role/permission APIs are not yet authorization-protected.
- State remains in-memory and process-local.
- Frontend panel uses static seed state and is not API-connected.
- Role hierarchy and role position checks are not implemented.
- Nuxt build still reports upstream sourcemap/deprecation warnings.
- Gradle still reports future Gradle 9 deprecation warnings.
