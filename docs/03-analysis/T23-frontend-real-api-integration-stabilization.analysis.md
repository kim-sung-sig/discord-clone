# T23 Frontend Real API Integration Stabilization Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T23 Frontend Real API Integration Stabilization

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `npm run test -- --run tests/components/app-shell.test.ts` in `apps/web` | PASS | 1 file passed; 30 tests passed |
| `npm run test -- --run` in `apps/web` | PASS | 5 files passed; 40 tests passed |
| `npm run build` in `apps/web` | PASS | Nuxt production build completed; known Nuxt/Vue warnings remain |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| login -> create guild/channel -> send message -> voice/stage smoke remains REST-backed | PASS | existing real-backend smoke flow still uses backend shell actions; component coverage passed |
| frontend request ids are visible in request headers and error state | PASS | `X-Request-Id` is asserted for every real-backend shell request and `apiLastRequestId` is exposed |
| API errors are surfaced accessibly in UI | PASS | existing `role="alert"` shell error remains wired; rejection test verifies error text includes request id |
| mocked and real-backend tests are clearly separated | PASS | component tests use mocked fetch; Playwright real-backend spec remains gated by `REAL_BACKEND_E2E=1` |

## Failure Criteria Review

| Failure Criteria | Status | Evidence |
| --- | --- | --- |
| UI can show success while backend rejected the action | NOT OBSERVED | rejection test verifies guild state is unchanged after backend failure |
| access tokens are persisted insecurely | NOT OBSERVED | existing login component coverage remains passing; T23 did not add token persistence |
| Playwright only validates local store mutations | MITIGATED | real-backend spec remains separated from mocked component tests and exercises REST-backed smoke when enabled |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| Real-backend Playwright was not executed in this pass | Medium | Requires running backend, database, and `REAL_BACKEND_E2E=1`; keep as environment-gated QA step |
| Request id format is client-generated only | Low-medium | Backend validation/sanitization should remain authoritative for incoming request ids |
| Mutating writes have no automatic retry | Intentional | Manual retry is safer until idempotency keys exist for guild/channel/message/voice/stage actions |

## Decision

T23 is acceptable for current roadmap scope. Frontend real API actions now emit explicit correlation ids, preserve pre-failure state, and expose request ids in error state for backend log tracing.
