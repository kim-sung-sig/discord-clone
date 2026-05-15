# T26 Nuxt SSR CSP Nonce Hardening Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T26 Nuxt SSR CSP Nonce Hardening

## Summary

T26 replaced Nuxt HTML script `unsafe-inline` with per-response CSP nonces. The Nitro plugin now injects the same nonce into rendered script tags and response CSP, preserving hydration while tightening script execution policy.

## Delivered

- Added nonce-aware `htmlSecurityHeaders({ scriptNonce })`.
- Added `addNonceToScriptTags` helper coverage.
- Added `server/plugins/csp-nonce.ts` Nitro plugin.
- Removed script `unsafe-inline` from CSP generation.
- Added local artifact ignore rules for generated `.serena/` and `**/bin/` outputs.

## Test Evidence

- `npm run test -- --run tests/components/security-headers.test.ts`: PASS, 2 tests
- SSR nonce probe against `http://127.0.0.1:3020/login`: PASS
- `npm run test -- --run`: PASS, 41 tests
- `npm run e2e -- tests/e2e/login.spec.ts tests/e2e/app-shell.spec.ts`: PASS, 14 tests
- `npm run build`: PASS with known T22 warnings
- `qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'`: PASS

## Commits

- `4428600 docs: plan T26 nuxt csp nonce hardening`
- `c4cf31b feat: harden nuxt csp with script nonces`

## Residual Risks

- `style-src 'unsafe-inline'` remains and should be handled as a separate CSS/style CSP hardening task.
- CSP reporting is not wired yet.
- First remote CI run should still be observed after push because GitHub runner behavior can differ from local Windows.

## Next Recommended Task

No T27 exists in the current breakdown. Recommended next promotion: `T27 CSP Reporting and Style Policy Hardening` or, if deployment is higher priority, remote CI run validation after pushing this branch.
