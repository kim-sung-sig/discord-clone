# T26 Nuxt SSR CSP Nonce Hardening Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T26 Nuxt SSR CSP Nonce Hardening

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `npm run test -- --run tests/components/security-headers.test.ts` before implementation | RED | CSP still used script `unsafe-inline`; nonce helper missing |
| `npm run test -- --run tests/components/security-headers.test.ts` after implementation | PASS | 2 tests passed |
| SSR nonce probe against `http://127.0.0.1:3020/login` | PASS | `SSR_CSP_NONCE_PASS`; CSP nonce matched rendered script nonce |
| `npm run test -- --run` in `apps/web` | PASS | 5 files passed; 41 tests passed |
| `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts` with `NUXT_DEV_PORT=3021` | PASS | 14 Playwright tests passed |
| `npm run build` in `apps/web` | PASS | Nuxt production build completed; known T22 warnings remain |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'` | PASS | API smoke PASS and real-backend Playwright 1 test passed; artifacts under `qa/artifacts/real-backend-e2e/20260515-213416/` |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| HTML CSP uses `script-src 'self' 'nonce-...'` | PASS | SSR probe captured nonce-backed CSP |
| script `unsafe-inline` is absent from `script-src` | PASS | unit test and SSR probe check script directive specifically |
| rendered script tags carry the matching nonce | PASS | SSR probe verifies matching script nonce in HTML |
| login/app-shell/real-backend Playwright remains green | PASS | 14 local e2e tests and real-backend 1-test smoke passed |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| T24 required `script-src 'unsafe-inline'` to restore hydration | Nuxt SSR emits inline payload scripts that were blocked by strict script CSP | T26 injects per-response nonce into rendered script tags and CSP |
| Local verification generated `**/bin` and `.serena` untracked outputs | Tooling/test execution generated local workspace artifacts | `.gitignore` now excludes `.serena/` and `**/bin/` |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| `style-src 'unsafe-inline'` remains | Medium | Future CSS nonce/hash hardening can address style policy separately |
| CSP report endpoint is not implemented | Low-medium | Add report-only/report-to pipeline when production observability target exists |
| Nitro render hook shape is framework-version sensitive | Low | Unit tests cover policy helpers; Playwright/e2e covers runtime hydration |

## Decision

T26 is acceptable for current roadmap scope. Nuxt SSR no longer needs script `unsafe-inline`, and runtime hydration remains verified by local and real-backend Playwright flows.
