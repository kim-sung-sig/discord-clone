# T52 Style Nonce/Hash Enforcement Removal Pass Plan

Date: 2026-05-18
Slice: T52 Style Nonce/Hash Enforcement Removal Pass

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T38 introduced report-only `style-src 'self'` while keeping enforce `style-src 'self' 'unsafe-inline'`. T51 added sanitized CSP telemetry storage. T52 now promotes the strict style policy to enforce mode and removes enforce `style-src 'unsafe-inline'`.

## Implementation Plan

Major topics:

1. Enforce CSP style hardening
   - Change generated enforce CSP to use `style-src 'self'`.
   - Keep script nonce policy unchanged.

2. Test coverage
   - Add/adjust unit tests to assert enforce CSP no longer contains style `unsafe-inline`.
   - Assert report-only remains strict.

3. Compatibility check
   - Run focused security tests.
   - Run Nuxt production build to catch SSR/build regressions.

## Out of Scope

- Style hash manifest generation.
- Browser visual regression matrix.
- Production CSP telemetry threshold automation.

## Acceptance Criteria

- `Content-Security-Policy` contains `style-src 'self'`.
- `Content-Security-Policy` does not contain `style-src 'self' 'unsafe-inline'`.
- `script-src` nonce behavior remains unchanged.
- Security tests and web build pass.

## Failure Criteria

- Enforce CSP still allows style `unsafe-inline`.
- Script nonce policy regresses.
- Nuxt build fails after style policy change.
