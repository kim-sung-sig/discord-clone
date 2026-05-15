# T26 Nuxt SSR CSP Nonce Hardening Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T26 Nuxt SSR CSP Nonce Hardening

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T24에서 Nuxt hydration을 복구하기 위해 HTML CSP에 `script-src 'unsafe-inline'`을 허용했고, 이는 production hardening gap이다. |
| Solution | SSR 응답마다 nonce를 생성하고, Nuxt inline script 태그와 CSP `script-src`에 같은 nonce를 적용한다. |
| Function UX Effect | 로그인/앱 shell hydration은 유지하면서 브라우저가 nonce 없는 inline script 실행을 차단한다. |
| Core Value | runtime QA를 깨뜨리지 않고 CSP를 production-grade 방향으로 되돌린다. |

## Scope

- Add nonce-aware CSP generation for Nuxt HTML responses.
- Add Nitro server plugin to generate per-response nonce and inject it into rendered script tags.
- Remove unconditional `script-src 'unsafe-inline'` from HTML CSP.
- Preserve local dev websocket/connect allowances.
- Cover CSP and HTML nonce injection with component/unit tests.

## Out of Scope

- Full CSP report endpoint.
- Third-party script allowlist.
- Backend API CSP changes.
- Replacing style `unsafe-inline`, which is separate from script execution risk.

## Success Criteria

- HTML CSP contains `script-src 'self' 'nonce-...'` for nonce-backed responses.
- HTML CSP no longer contains script `unsafe-inline`.
- SSR inline script tags receive the matching nonce.
- Login and app-shell Playwright e2e remain green.
- Real-backend QA harness remains green.

## Failure Criteria

- Hydration breaks again.
- CSP still allows arbitrary inline scripts.
- Nonce in CSP and script tags can diverge.
