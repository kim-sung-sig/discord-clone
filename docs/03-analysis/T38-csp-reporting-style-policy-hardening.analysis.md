# T38 CSP Reporting & Style Policy Hardening Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T38 CSP Reporting & Style Policy Hardening

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `npm test -- --run tests/components/security-headers.test.ts` | PASS | CSP report headers, report normalization, abuse limits, and no-content handler tests passed. |
| `npm test -- --run` | PASS | Full web Vitest suite passed: 51 tests. |
| `npm run build` | PASS | Nuxt production build completed and emitted CSP report routes. |
| `$env:NUXT_DEV_PORT='3108'; $env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3108'; $env:CI='1'; npm run e2e -- security-headers.spec.ts` | PASS | Runtime header and report endpoint smoke passed. |
| `$env:NUXT_DEV_PORT='3109'; $env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3109'; $env:CI='1'; npm run e2e` | PASS | 17 Playwright tests passed, 1 real-backend test skipped. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| CSP reports are accepted through safe endpoint with size/content limits | PASS | `shouldAcceptCspReport` enforces supported content types and 16 KiB max body. |
| Payloads are redacted and do not store tokens, cookies, or request bodies | PASS | Normalization strips URLs to origins/literals, hashes user agent, and never persists raw body/script samples. |
| Report-only mode can be enabled separately from enforce mode | PASS | `Content-Security-Policy-Report-Only` generated with separate report endpoint. |
| Script nonce CSP remains unchanged | PASS | Tests assert script nonce and no script `unsafe-inline`. |
| Style CSP hardening options and remaining exceptions documented | PASS | `qa/artifacts/security/browser-security-telemetry.md` records report-only style policy and removal condition. |
| Browser security telemetry artifact produced | PASS | `qa/artifacts/security/browser-security-telemetry.md`. |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| RED unit tests failed because reporting directives and normalizers were absent | Existing header utility only emitted enforce CSP | Added reporting options, report-only CSP, Reporting-Endpoints, and report normalization helpers. |
| First local e2e run saw empty CSP header | Playwright reused an existing dev server on port 3000 | Re-ran on fresh ports with `CI=1`; clean dev server passed. |

## Implementation Notes

- Added `POST /api/security/csp-report` and `POST /api/security/csp-report-only`.
- Both endpoints return `204` and do not echo report content.
- Classic `application/csp-report` and Reporting API array payloads are normalized.
- Enforce CSP keeps `style-src 'self' 'unsafe-inline'`.
- Report-only CSP uses `style-src 'self'` to surface style hardening violations safely.

## Residual Risks

- CSP reports currently go to process logs only; durable storage/SIEM is intentionally out of T38 scope.
- Rate limiting is represented by size/content controls in Nuxt; shared IP/user rate limiting should be added if report volume grows.
- Full style `unsafe-inline` removal remains blocked on Nuxt/runtime style injection validation.
