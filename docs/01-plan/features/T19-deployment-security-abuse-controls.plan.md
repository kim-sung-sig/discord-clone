# T19 Deployment Security/Abuse Controls Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T19 Deployment Security/Abuse Controls

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | API 보안 헤더는 존재하지만 Nuxt HTML CSP가 없고, auth/message/invite/gateway 남용 요청을 제어하는 rate limit 경계가 없다. |
| Solution | Nuxt HTML 보안 헤더를 Nitro middleware로 적용하고, Spring Boot API 필터에 포트 기반 rate limiter를 추가한다. |
| Function UX Effect | 과도한 로그인/메시지/초대/gateway identify 요청은 일관된 429 JSON 응답과 `Retry-After` 헤더로 거절된다. |
| Core Value | 실제 프론트/백엔드 플로우와 관측성 위에 배포 전 최소 abuse control을 만든다. |

## Scope

- Nuxt HTML responses get documented deployment security headers:
  - `Content-Security-Policy`
  - `X-Content-Type-Options`
  - `X-Frame-Options`
  - `Referrer-Policy`
  - `Permissions-Policy`
- API rate limiting for:
  - `POST /api/auth/login`
  - `POST /api/invites/{code}/accept`
  - `POST /api/channels/{channelId}/messages`
  - `POST /api/gateway/identify`
- Rate limit key dimensions:
  - auth: client IP plus normalized endpoint
  - invite/message/gateway: authenticated user when present, otherwise client IP plus normalized endpoint
- Abuse-oriented error response policy:
  - HTTP 429
  - `Retry-After`
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - JSON body `{ "message": "rate limit exceeded" }`
- Redis production parity design through a `RateLimitStore` port and a documented Redis command model.

## Out of Scope

- Real Redis container dependency in local tests.
- Bot detection or WAF integration.
- CAPTCHA or account risk scoring.
- Distributed gateway/session abuse analytics.
- Changing existing login lockout behavior; rate limiting sits before lockout abuse can be amplified.

## Success Criteria

- API and HTML responses both have documented security headers.
- Brute-force auth attempts are rate-limited before lockout abuse becomes the only control.
- Message spam, invite accept bursts, and gateway identify bursts are throttled.
- Rate limit tests cover user/IP/key dimensions.
- Production path is not hard-wired to process memory; Redis parity is represented by a replaceable store boundary and documented command semantics.

## Failure Criteria

- Security headers apply only to JSON API responses.
- Rate limit counters are only local process memory for the production path.
- Limiter bypass is possible by changing raw UUID/path segments.
- 429 responses leak auth tokens, passwords, invite codes, or message bodies.
