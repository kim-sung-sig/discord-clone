# T23 Frontend Real API Integration Stabilization Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T23 Frontend Real API Integration Stabilization

## Architecture Decision

Keep current REST-backed shell actions but make request correlation explicit. `withBackendRequest` generates one request id per action, stores it in Pinia state, passes it to the REST client, and includes it in error text.

## Retry Policy

No automatic retry for mutating writes. Guild/channel/message/voice/stage actions are not idempotent enough for blind browser retries. UI should leave the shell in pre-mutation state and expose the request id so the user can manually retry after checking the alert.

## Auth Token Policy

Access token remains in Pinia memory only. Existing login tests assert no localStorage/sessionStorage/cookie persistence.

## Test Strategy

- Component/unit mocked tests assert all real-backend shell calls send Authorization and `X-Request-Id`.
- Rejection test asserts no state mutation and accessible error includes request id.
- Existing real-backend Playwright remains skipped unless `REAL_BACKEND_E2E=1`.

## Risks

- Browser refresh loses token by design until refresh-token flow exists.
- Request id is client-generated and must still be validated/sanitized by backend hardening filters.
