# T23 Frontend Real API Integration Stabilization Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T23 Frontend Real API Integration Stabilization

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 현재 Nuxt shell은 Pinia 로컬 상태만 변경해 백엔드가 거절해도 UI가 성공처럼 보일 수 있다. |
| Solution | 기존 mock shell 테스트는 유지하고, 실제 REST-backed 로그인/길드/채널/메시지/보이스/스테이지 smoke 경로를 분리해 추가한다. |
| Function UX Effect | 사용자는 API 오류를 role=alert 영역에서 볼 수 있고, 성공 UI는 실제 백엔드 응답을 받은 뒤 갱신된다. |
| Core Value | T16 PostgreSQL 기반 위에서 프론트가 실제 서비스 상태와 동기화되는지 검증 가능한 기반을 만든다. |

## Scope

- Browser access token policy: memory-only Pinia state, no localStorage/sessionStorage/cookie token persistence.
- Runtime config for API base URL.
- Real REST-backed auth login using existing `createDiscordRestClient`.
- Real-backend shell actions for the T23 smoke path:
  - create guild
  - create text/voice channel
  - send message
  - join voice
  - start stage session
- Accessible API error display for login and shell operations.
- Separate Playwright test file for real backend flow.

## Out of Scope

- Replacing every shell feature with REST in one slice.
- Long-lived refresh token/session storage.
- Full websocket/gateway UI replacement.
- Frontend persistence across page reloads.

## Success Criteria

- Login uses backend `/api/auth/login` and stores token only in memory.
- Real-backend smoke executes login -> create guild/channel -> send message -> voice join -> stage start through backend APIs.
- UI updates only after successful backend responses.
- API errors are rendered accessibly with `role="alert"`.
- Mock/local shell tests and real-backend Playwright tests are named and separated.

## Failure Criteria

- UI shows success after backend rejection.
- Access token appears in localStorage, sessionStorage, or cookies.
- Playwright real-backend test only validates local store mutations.
- Mock and real-backend tests share ambiguous setup or assertions.
