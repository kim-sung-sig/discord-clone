# T01 Identity/User Analysis

작성일: 2026-05-13  
PDCA Phase: Check

## Verification Matrix

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Identity module RED | `.\gradlew.bat :backend:modules:identity:test` | Pass | Failed before implementation because `EmailAddress`, `PasswordHasher`, `AccessTokenService`, `RefreshSession`, and `LoginFailureTracker` did not exist. |
| User module RED | `.\gradlew.bat :backend:modules:user:test` | Pass | Failed before implementation because `Username` and `UserProfile` did not exist. |
| Identity module GREEN | `.\gradlew.bat :backend:modules:identity:test` | Pass | `BUILD SUCCESSFUL`; 8 identity tests passed after dependency fix. |
| User module GREEN | `.\gradlew.bat :backend:modules:user:test` | Pass | `BUILD SUCCESSFUL`; user profile/username tests passed. |
| Backend full gate | `.\gradlew.bat test` | Pass | `BUILD SUCCESSFUL in 8s`; 19 actionable tasks, 4 executed, 15 up-to-date. |
| Frontend component regression | `npm run test -w apps/web -- --run tests/components/app-shell.test.ts` | Pass | Vitest reported 1 file passed and 1 test passed. |
| Frontend e2e regression | `npm run e2e -w apps/web` | Pass | Playwright Chromium reported 1 test passed in 7.2s. |
| Frontend build regression | `npm run build -w apps/web` | Pass | Nuxt 4.4.5 production build completed. |
| Docker compose config | `docker compose -f infra/docker/docker-compose.yml config` | Pass | Compose rendered `name: discord-clone`. |
| Auth REST API RED | `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest` | Pass | Initially failed because `/api/auth/signup` returned 404 before Auth API implementation. |
| Auth REST API GREEN | `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest` | Pass | Signup, login, bearer `/users/@me`, logout, lockout, duplicate signup, invalid request, unknown-email lockout tests passed. |
| Review fix backend fresh gate | `.\gradlew.bat test --rerun-tasks` | Pass | `BUILD SUCCESSFUL in 17s`; 19 actionable tasks executed. |
| Review fix frontend component gate | `npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts` | Pass | 2 test files passed, 3 tests passed. |
| Review fix frontend e2e gate | `npm run e2e -w apps/web` | Pass | 3 Playwright tests passed, including unknown route and login storage checks. |

## RED Evidence

- Identity tests initially failed with 24 compile errors for missing production types.
- User tests initially failed with 6 compile errors for missing production types.

## Feedback Resolved

- `BCryptPasswordEncoder` required `commons-logging` at runtime. Added `commons-logging:commons-logging:1.3.4` as identity module runtime dependency and re-ran identity tests.
- Backend review findings resolved: duplicate signup conflict, logout token revocation, thread-safe lockout tracker, unknown-email lockout counting, invalid request mapping, refresh rotation guards, and property-driven access token secret.
- Frontend review findings resolved: app routing now uses `<NuxtPage />`, app shell moved to root page, login tests reset Pinia state, e2e verifies token is not persisted, and disabled login controls expose a loading status message.

## Gap List

- T01 now includes backend REST auth API and frontend placeholder login UI/e2e.
- Persistence, refresh-cookie transport, durable revocation, and real backend-connected Nuxt login remain later slices.
- Gradle still reports future Gradle 9 deprecation warnings; non-blocking build maintenance item.
- Nuxt build still reports upstream sourcemap/deprecation warnings; non-blocking frontend maintenance item.
