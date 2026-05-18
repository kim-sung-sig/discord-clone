# T38 CSP Reporting & Style Policy Hardening Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T38 CSP Reporting & Style Policy Hardening

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T26으로 script `unsafe-inline`은 제거됐지만 CSP violation reporting과 `style-src 'unsafe-inline'` 축소 경로는 아직 없다. |
| Solution | CSP report-only/report endpoint, browser security telemetry artifact, style policy hardening analysis를 추가한다. |
| Function UX Effect | 정상 사용자는 변화를 거의 느끼지 않지만, 보안 정책 위반과 브라우저 차단 원인을 운영자가 추적할 수 있다. |
| Core Value | CSP를 정적 header에서 관측 가능한 browser security control로 승격한다. |

## Scope

- Add CSP report endpoint for Nuxt/browser violation reports.
- Add report-only policy option for staged rollout.
- Filter and normalize incoming CSP reports before persistence/logging.
- Add QA artifact for browser security telemetry.
- Analyze remaining `style-src 'unsafe-inline'` dependency.
- Prototype nonce/hash-based style policy where feasible.
- Keep T26 script nonce behavior green.

## Out of Scope

- Full SIEM integration.
- Long-term CSP report storage UI.
- Third-party script marketplace allowlist.
- Complete style CSP removal if Nuxt/runtime CSS requires a broader refactor.
- Backend API CSP redesign unrelated to HTML/browser policy.

## Success Criteria

- CSP reports are accepted through a safe endpoint with size and content limits.
- CSP report payloads are redacted and do not store tokens, cookies, or request bodies.
- Report-only mode can be enabled separately from enforce mode.
- Script nonce CSP remains unchanged and regression tests pass.
- Style CSP hardening options and remaining exceptions are documented.
- Browser security telemetry artifact is produced during QA.

## Failure Criteria

- CSP report endpoint stores sensitive user data.
- CSP reports can be used for log flooding without limits.
- Script nonce policy regresses to `unsafe-inline`.
- Style policy change breaks hydration, layout, or PWA shell behavior.
- T38 closes without a clear decision on remaining style exceptions.
