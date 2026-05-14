# T23 Frontend Real API Integration Stabilization Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T23 Frontend Real API Integration Stabilization

## Completed

- Added Nuxt public API base URL runtime config.
- Connected the login form and auth store to backend `/api/auth/login`.
- Preserved browser access token policy as memory-only Pinia state.
- Added REST-backed shell actions for guild, text/voice channel, message, voice join, and stage start.
- Added accessible shell API error rendering.
- Added a login workspace link so E2E can move from login to the app shell after successful backend login.
- Added guarded real-backend Playwright smoke covering signup fixture, UI login, guild/channel/message, voice, and stage.
- Added local development CORS handling in the Spring Boot hardening filter so Nuxt can call the API from the browser.

## Commits

- `10ca29c docs: plan T23 frontend real api integration`
- `e410359 docs: add T23 implementation plan`
- `0215cd4 feat: connect login to backend api`
- `159ddf4 feat: add frontend real backend shell actions`
- `f879203 feat: allow local frontend api cors`
- `88cb5e8 test: add frontend real backend smoke`

## QA Evidence

- `npm run test -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts tests/components/shell-contracts.test.ts`: PASS, 38 tests
- `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts`: PASS, 14 tests
- `REAL_BACKEND_E2E=1 REAL_BACKEND_BASE_URL=http://127.0.0.1:8080 npm run e2e -- tests/e2e/real-backend.spec.ts`: PASS, 1 test
- `npm run build`: PASS, Nuxt build complete
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks`: PASS, BUILD SUCCESSFUL
- `npm run test -- --run`: PASS, 39 tests
- `.\gradlew.bat test`: PASS, BUILD SUCCESSFUL
- `SPRING_PROFILES_ACTIVE=postgres` backend startup: PASS, Flyway validated 4 migrations and Tomcat started on port 8080

## Outcome

T23 meets its success criteria. The frontend now has a verified real-backend path for authentication and a representative app-shell workflow while preserving deterministic local shell tests. Browser token persistence remains absent, and backend CORS is narrowly opened for local Nuxt development.

## Next Task Candidate

Proceed to the recommended next item: `T17 Observability/Structured Logging`.
