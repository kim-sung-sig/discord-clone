# T15 Operational Hardening Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T15 Operational Hardening/E2E Stabilization

## Verification Summary

| Area | Command | Result |
| --- | --- | --- |
| Backend targeted | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Frontend targeted | `npm run test -- --run tests/components/shell-contracts.test.ts` | PASS, 6 tests |
| Backend full | `.\gradlew.bat test` | PASS, BUILD SUCCESSFUL in 32s |
| Frontend full | `npm run test -- --run` | PASS, 4 files / 37 tests |
| Frontend build | `npm run build` | PASS, known Nuxt sourcemap/Vue export warnings only |
| Frontend E2E | `npm run e2e -- tests/e2e/app-shell.spec.ts` | PASS, 13 tests |

## Success Criteria Check

- Every API response has `X-Request-Id`: PASS for `/api/**` via common filter and MockMvc proof.
- Safe incoming request id is echoed: PASS with `qa-request_123.trace`.
- Unsafe request id is not reflected: PASS; invalid input is replaced with a UUID-shaped safe id.
- Security headers are present on API responses: PASS for content type, frame, referrer, CSP, permissions policy, and no-store cache control.
- Frontend API client sends request id: PASS for provided and generated request id test cases.
- Full backend/frontend/e2e QA remains green: PASS.

## Design Match

- Backend hardening is implemented as one `OncePerRequestFilter`, keeping controller code unchanged.
- Filter scope is API-only through `/api/` URI matching.
- Frontend request correlation is implemented in the REST client, preserving bearer token and JSON body behavior.
- Tests verify behavior rather than implementation details.

## Residual Risks

- This is a baseline hardening pass, not a full production security program.
- HTML-level CSP for Nuxt pages is not configured; T15 only applies API response headers.
- There is no distributed trace propagation beyond `X-Request-Id`.
- Existing Gradle deprecation and Nuxt/Vue warnings remain non-blocking.
