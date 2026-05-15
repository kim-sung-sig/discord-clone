# T24 Real Backend QA Orchestration Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T24 Real Backend QA Orchestration

## Summary

T24 added a repeatable real-backend QA harness and used it to expose and fix runtime-only defects around Nuxt hydration, local Playwright port collisions, CORS, and command working directory handling.

## Delivered

- Added `qa/real-backend-e2e.ps1` orchestration harness.
- Added `qa/real-backend-e2e.contract.ps1` static contract verification.
- Added Playwright dev port/base URL override support.
- Stabilized login and real-backend e2e readiness waits.
- Adjusted Nuxt HTML CSP so client hydration works under SSR/dev.
- Expanded backend local CORS to support local Nuxt override ports.

## Test Evidence

- `qa/real-backend-e2e.contract.ps1`: PASS
- `qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'`: PASS
- Latest runtime artifacts: `qa/artifacts/real-backend-e2e/20260515-134822/`
- `npm run test -- --run`: PASS, 40 tests
- `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts` with `NUXT_DEV_PORT=3018`: PASS, 14 tests
- `npm run build`: PASS with known T22 warnings
- `./gradlew.bat test`: PASS

## Commits

- `2d741dc docs: plan T24 real backend qa orchestration`
- `45ffeeb test: add real backend qa orchestration harness`
- `532820e test: stabilize real backend qa orchestration`

## Residual Risks

- Local PostgreSQL port convention is inconsistent in this environment: compose declares 5432, the currently running container exposed 15432. The harness supports both through `-PostgresJdbcUrl`.
- Nuxt CSP currently permits inline scripts for hydration compatibility. A nonce/hash CSP should replace this for production hardening.
- CI workflow integration remains future work.

## Next Recommended Task

No T25 exists in the current task breakdown. Promote the next task from newly discovered residual risks, with the strongest candidate being CI wiring for the T24 real-backend harness and toolchain warning artifacts.
