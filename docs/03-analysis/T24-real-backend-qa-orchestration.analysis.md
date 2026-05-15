# T24 Real Backend QA Orchestration Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T24 Real Backend QA Orchestration

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1` | PASS | `REAL_BACKEND_E2E_CONTRACT_PASS` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'` | PASS | API smoke PASS and real-backend Playwright 1 test passed; artifacts under `qa/artifacts/real-backend-e2e/20260515-134822/` |
| `npm run test -- --run` in `apps/web` | PASS | 5 files passed; 40 tests passed |
| `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts` with `NUXT_DEV_PORT=3018` | PASS | 14 Playwright tests passed |
| `npm run build` in `apps/web` | PASS | Nuxt production build completed; known T22 warnings remain |
| `./gradlew.bat test` | PASS | backend and module tests completed successfully |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| Initial harness runtime failed at login input disabled | Nuxt HTML CSP blocked inline payload script, preventing client mount | CSP now allows Nuxt inline payload script and dev websocket ports |
| Login e2e hit a 404 page | Playwright reused an unrelated process on localhost:3000 from another workspace | Playwright config now supports `NUXT_DEV_PORT` and `PLAYWRIGHT_BASE_URL`; harness uses 3010 |
| Real-backend login showed `Unable to reach the Discord API` | Backend CORS allowed only local Nuxt 3000, while harness runs Nuxt on 3010 | API CORS now allows local `127.0.0.1`/`localhost` development ports |
| Harness command execution did not honor working directory | `Invoke-LoggedCommand` accepted `workingDirectory` but did not `Push-Location` | Harness now pushes/pops command working directory around each external command |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Harness has one-command real backend QA path | PASS | `qa/real-backend-e2e.ps1` composes backend health/start, API smoke, and real-backend Playwright |
| Harness can reuse existing backend or start bootRun | PASS | latest run reused healthy backend; script starts `:backend:boot:bootRun` when unavailable |
| API smoke and Playwright env variables are consistent | PASS | harness sets `REAL_BACKEND_E2E`, `REAL_BACKEND_BASE_URL`, `NUXT_PUBLIC_API_BASE_URL`, `NUXT_DEV_PORT`, and `PLAYWRIGHT_BASE_URL` |
| Contract test verifies wiring without external services | PASS | `qa/real-backend-e2e.contract.ps1` passes |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| Harness default PostgreSQL URL is 5432, but current running container was exposed on 15432 | Low | Override parameter works; align actual compose/container convention in a future infra cleanup if needed |
| CSP uses `'unsafe-inline'` for Nuxt payload compatibility | Medium | Replace with nonce/hash-based CSP when Nuxt SSR nonce wiring is introduced |
| Full CI workflow still not wired | Medium | Add provider-specific job after CI target is selected |

## Decision

T24 is acceptable for current roadmap scope. Real-backend QA is now repeatable through a single harness and the frontend/backend runtime defects found while exercising it were fixed with regression coverage.
