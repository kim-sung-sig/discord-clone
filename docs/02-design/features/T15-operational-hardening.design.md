# T15 Operational Hardening Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T15 Operational Hardening/E2E Stabilization

## Backend Architecture

Add `backend/boot/src/main/java/com/example/discord/ops/OperationalHardeningFilter.java`. The filter runs once per request and applies only to `/api/` paths.

Responsibilities:

- Read incoming `X-Request-Id`.
- Accept only ids matching `[A-Za-z0-9._-]{1,64}`.
- Generate UUID when missing or unsafe.
- Set `X-Request-Id` response header.
- Set security headers:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: no-referrer`
  - `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  - `Cache-Control: no-store`

The filter does not change controller behavior and does not parse authentication state. This keeps hardening orthogonal to feature modules.

## Backend Test Strategy

Create `OperationalHardeningFilterTest` using MockMvc against a stable public API endpoint:

- `GET /api/premium/catalog` without `X-Request-Id` has generated id and security headers.
- `GET /api/premium/catalog` with `X-Request-Id: qa-request-123` echoes `qa-request-123`.
- `GET /api/premium/catalog` with `X-Request-Id: bad header<script>` does not echo unsafe value.

## Frontend Architecture

Extend `apps/web/services/discord-api.ts` request options with optional `requestId`. When not provided, generate a browser-safe request id using current time plus random suffix. Every API request sends `X-Request-Id`.

The generated id is not a tracing backend replacement; it is a client-visible correlation seed that backend can echo.

## Frontend Test Strategy

Extend `apps/web/tests/components/shell-contracts.test.ts` to mock `fetch` and assert:

- `X-Request-Id` is sent when provided in request options.
- A generated `X-Request-Id` exists when omitted.
- Existing auth/content-type behavior remains unchanged.

## Risks

- CSP on API JSON responses is conservative and safe, but does not replace app HTML CSP for Nuxt. HTML CSP should be handled separately when deployment headers are introduced.
- Random request id generation in frontend tests must be shape-based, not exact-value based.
