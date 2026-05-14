# T15 Operational Hardening Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T15 Operational Hardening/E2E Stabilization

## Problem

| 관점 | 내용 |
| --- | --- |
| User Problem | 기능 skeleton은 쌓였지만 장애/보안/회귀 추적 기준이 없어 운영 품질을 판단하기 어렵다. |
| Product Problem | Discord-like 서비스를 장기간 확장하려면 request 단위 추적, security baseline, 안정적인 QA evidence가 필요하다. |
| Engineering Problem | 현재 컨트롤러별 예외/테스트는 많지만 모든 API 응답에 공통 hardening header와 correlation id가 보장되지 않는다. |
| Core Value | T00-T14 기능을 운영 가능한 baseline으로 묶고, 이후 persistence/infra 전환 시 추적 가능한 request contract를 만든다. |

## Scope

- Backend servlet filter for `X-Request-Id` generation, sanitization, and response echo.
- Backend security headers for API responses.
- Backend API no-store cache-control baseline.
- MockMvc tests for generated request id, sanitized incoming id, and hardening headers.
- Nuxt API client request id propagation test.
- PDCA QA evidence for full backend/frontend/e2e regression.

## Out of Scope

- Distributed tracing backend such as OpenTelemetry collector.
- Rate limiting, WAF, bot detection, and DDoS controls.
- Production secrets, TLS termination, and Kubernetes ingress policy.
- Performance load tests with large datasets.

## Success Criteria

- API response without incoming request id returns a generated `X-Request-Id`.
- API response with safe incoming request id echoes the same id.
- Unsafe request id characters are not reflected.
- API responses include `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Content-Security-Policy`, and no-store cache headers.
- Frontend API client sends an `X-Request-Id` header and tests prove it.
- Full backend and frontend regression suites pass.

## Failure Criteria

- API response can omit request id.
- Client-controlled unsafe request id is reflected directly.
- Security headers appear only on selected controllers, not a common filter.
- Frontend request id behavior is implicit or untested.
- QA evidence is not recorded in PDCA analysis/report.

## Assumptions

- Request id format allows `[A-Za-z0-9._-]` and length up to 64.
- API responses are under `/api/**`; actuator may be excluded unless explicitly needed.
- Cache-control no-store applies to API responses in this skeleton because all API data is user/session-sensitive.
