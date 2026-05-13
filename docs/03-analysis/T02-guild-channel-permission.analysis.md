# T02 Guild/Channel/Permission Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Slice: T02-A foundation

## Verification Matrix

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Backend fresh full gate | `.\gradlew.bat test --rerun-tasks` | Pass | `BUILD SUCCESSFUL in 27s`; 25 actionable tasks executed. |
| Frontend component gate | `npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts` | Pass | 2 test files passed, 4 tests passed. |
| Frontend e2e gate | `npm run e2e -w apps/web` | Pass | 4 Playwright tests passed. |
| Frontend build gate | `npm run build -w apps/web` | Pass | Nuxt production build completed. |
| Docker compose config | `docker compose -f infra/docker/docker-compose.yml config` | Pass | Compose rendered `name: discord-clone`. |

## RED Evidence

- Permission module tests initially failed on missing `EffectivePermissionCalculator`, `RolePermission`, and `PermissionOverwrite`.
- Guild module tests initially failed on missing guild/channel service and domain classes.
- Boot REST tests initially failed because guild REST endpoints did not exist.
- Frontend shell test initially failed because explicit guild/channel test ids did not exist.

## Implemented Scope

- Added `channel` and `guild` backend modules.
- Extended permission module with role permissions, channel overwrites, and effective permission calculator.
- Added in-memory guild service.
- Added REST endpoints for guild create, channel create, and visible channel list.
- Updated Nuxt shell store to explicit guild/channel model.
- Updated shell component/e2e tests.

## Review Fixes

- Fixed permission overwrite precedence so role-level deny overrides `@everyone` allow for the same channel.
- Added bad-request validation for channel creation payloads missing `type`.
- Added supplemental architecture source scan to prevent boot adapter imports from backend modules.
- Converted channel rows to keyboard-accessible buttons with focus and disabled states.
- Scoped rendered messages to the active channel instead of showing global messages.
- Switched channel test ids from mutable names to stable channel ids.
- Added hydration guard for interactive channel switching to avoid Playwright click races before Nuxt is mounted.

## Gap List

- Storage is in-memory and process-local.
- Permission overwrites are role-level only.
- Frontend shell is still static seed state and not API-connected.
- Nuxt build still reports upstream sourcemap/deprecation warnings.
- Gradle still reports future Gradle 9 deprecation warnings.
