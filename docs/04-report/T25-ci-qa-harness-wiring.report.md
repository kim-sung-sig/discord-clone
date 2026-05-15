# T25 CI QA Harness Wiring Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T25 CI QA Harness Wiring

## Summary

T25 promoted local QA evidence into CI wiring. The new GitHub Actions workflow runs backend tests, frontend tests/build, real-backend runtime smoke with PostgreSQL, and toolchain warning scan with artifact uploads.

## Delivered

- Added `.github/workflows/ci.yml`.
- Added `qa/ci-workflow.contract.ps1`.
- Made `qa/real-backend-e2e.ps1` portable across Windows PowerShell and Linux PowerShell Core.
- Made `qa/toolchain-warning-scan.ps1` use platform-aware Gradle wrapper selection and exit-code-based native command handling.
- Added artifact upload paths for real-backend and toolchain logs.

## Test Evidence

- `qa/ci-workflow.contract.ps1`: PASS
- `qa/real-backend-e2e.contract.ps1`: PASS
- `qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'`: PASS
- `qa/toolchain-warning-scan.ps1`: PASS

## Commits

- `86e4f2e docs: plan T25 ci qa harness wiring`
- `29274f3 ci: wire qa harness workflows`

## Residual Risks

- First remote GitHub Actions execution can still reveal runner-specific issues not reproducible locally.
- Nuxt inline-script CSP remains a production-hardening follow-up from T24.
- Toolchain warnings remain known upstream/tooling warnings from T22.

## Next Recommended Task

No T26 exists in the current breakdown. Recommended next promotion: production CSP nonce/hash hardening for Nuxt SSR, because T24 deliberately allowed inline scripts to restore hydration.
