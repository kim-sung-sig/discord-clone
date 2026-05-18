# T38 CSP Reporting & Style Policy Hardening Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T38 CSP Reporting & Style Policy Hardening

## Summary

T38 turns the Nuxt CSP from a static header into observable browser security telemetry. The app now advertises CSP report endpoints, accepts sanitized violation reports, keeps T26 script nonce hardening intact, and runs a stricter report-only style policy to measure the impact of removing `style-src 'unsafe-inline'`.

## Delivered

- Added CSP reporting options to `htmlSecurityHeaders`.
- Added `Content-Security-Policy-Report-Only` and `Reporting-Endpoints`.
- Added report normalization for classic CSP reports and Reporting API payloads.
- Added size and content-type guards for report payloads.
- Added `POST /api/security/csp-report`.
- Added `POST /api/security/csp-report-only`.
- Added Playwright runtime security header/report endpoint smoke.
- Added browser security telemetry artifact.

## Verification

- `npm test -- --run tests/components/security-headers.test.ts`: PASS
- `npm test -- --run`: PASS
- `npm run build`: PASS
- `npm run e2e -- security-headers.spec.ts` on fresh port 3108: PASS
- `npm run e2e` on fresh port 3109: PASS, 17 passed and 1 skipped

## Coverage

- CSP script nonce regression
- Enforce/report-only policy generation
- Report endpoint no-content response
- Classic CSP report normalization
- Reporting API payload normalization
- URL query/path redaction
- user-agent hashing
- oversized/malformed/unsupported report rejection
- browser runtime header verification

## Residual Risks

- CSP reports are process-log telemetry only.
- Report endpoint has body/content guards but not distributed rate limiting.
- Enforce style policy still allows `unsafe-inline`; report-only policy is the staging mechanism for future removal.

## Next Recommended Task

Proceed to T39 Backup/Restore Migration Drill. T38 leaves a follow-up for durable CSP telemetry storage and full style nonce/hash removal after runtime violations are known.
