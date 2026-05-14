# T23 Frontend Real API Integration Stabilization Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T23 Frontend Real API Integration Stabilization

## Verification Summary

| Area | Command | Result |
| --- | --- | --- |
| Login and shell unit regressions | `npm run test -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts tests/components/shell-contracts.test.ts` | PASS, 3 files / 38 tests |
| Local frontend E2E regressions | `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts` | PASS, 14 tests |
| Real backend frontend E2E | `REAL_BACKEND_E2E=1 REAL_BACKEND_BASE_URL=http://127.0.0.1:8080 npm run e2e -- tests/e2e/real-backend.spec.ts` | PASS, 1 test |
| Frontend production build | `npm run build` | PASS, Nuxt build complete |
| Backend local frontend CORS | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Frontend full unit suite | `npm run test -- --run` | PASS, 4 files / 39 tests |
| Backend full suite | `.\gradlew.bat test` | PASS, BUILD SUCCESSFUL |

## Success Criteria Check

- Login uses backend `/api/auth/login`: PASS through auth store, login form, and Playwright login route assertions.
- Token remains memory-only: PASS; Playwright asserts the returned token is absent from localStorage, sessionStorage, and cookies.
- Real-backend smoke executes UI login, guild creation, channel creation, message send, voice join, and stage start through backend APIs: PASS via guarded `real-backend.spec.ts`.
- UI mutates from backend responses only after successful calls: PASS through shell store unit tests and real-backend smoke assertions.
- API errors render accessibly: PASS through login error tests and shell `role="alert"` API error region.
- Mock/local tests remain separated from real-backend smoke: PASS; real backend test is guarded by `REAL_BACKEND_E2E=1`.

## Design Match

- Nuxt runtime config now exposes `public.apiBaseUrl`, defaulting to `http://127.0.0.1:8080`.
- Login keeps the access token in Pinia memory and does not introduce browser persistence.
- The shell store keeps existing deterministic local state while adding explicit REST-backed action boundaries.
- The app shell exposes a dedicated real-backend smoke control only when an access token is available.
- Spring Boot now allows local Nuxt development origins for API preflight requests, which is required for browser-to-backend E2E.

## Findings And Fixes

- Initial real-backend Playwright run failed at UI login with `Unable to reach the Discord API. Try again.`.
- Root cause: browser CORS preflight from `http://127.0.0.1:3000` to `http://127.0.0.1:8080` was blocked by the backend hardening filter.
- Fix: added explicit local development CORS handling for `http://127.0.0.1:3000` and `http://localhost:3000`, plus a preflight regression test.
- Diagnostic improvement: real-backend E2E now asserts the signup API response before attempting UI login.

## Residual Risks

- The real-backend smoke depends on a running Spring Boot server and local PostgreSQL profile; it is intentionally opt-in.
- CORS is currently scoped to local development origins only; production origin configuration remains a later deployment/security task.
- Voice and stage assertions validate backend API integration and skeleton provider responses, not real media transport.
