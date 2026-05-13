# T01 Identity/User Report

작성일: 2026-05-13  
PDCA Phase: Report

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 인증/세션 정책이 없으면 이후 guild/channel/message 권한 처리를 안전하게 붙일 수 없다. |
| Solution | `identity`와 `user` backend modules를 추가하고 password hashing, access token expiry, refresh rotation, lockout, profile validation을 테스트로 고정했다. |
| Function UX Effect | 아직 로그인 화면/API는 없지만, 다음 slice에서 안전한 signup/login/logout transport를 붙일 수 있는 도메인 기반이 생겼다. |
| Core Value | plain-text password, 만료 무시 token, refresh 재사용, invalid username 같은 보안 회귀를 단위 테스트로 차단한다. |

## Implemented

- `EmailAddress` normalization and validation
- `PasswordHasher` abstraction
- `BCryptPasswordHasher`
- HMAC-SHA256 `AccessTokenService`
- `AccessTokenClaims`
- explicit `TokenVerificationException`
- immutable `RefreshSession` rotation model
- `LoginFailureTracker`
- REST signup/login/logout endpoints
- bearer-token `/api/users/@me`
- duplicate signup conflict handling
- invalid login lockout for existing and unknown emails
- access token logout revocation denylist
- `Username`
- `PrivacySettings`
- `UserProfile`
- Nuxt login page and login form
- in-memory placeholder frontend auth store
- Playwright login e2e
- boot module dependency wiring
- ArchUnit rule coverage for identity/user/permission modules

## Verification Evidence

```powershell
.\gradlew.bat test
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

Results:

- Backend: `BUILD SUCCESSFUL in 17s` with `--rerun-tasks`
- Frontend component: Vitest `2 files passed`, `3 tests passed`
- Frontend e2e: Playwright Chromium `3 passed`
- Frontend build: Nuxt production build completed
- Infra: Docker Compose config rendered successfully

## Next Slice

T02 should start guild/channel/permission implementation. Before production auth hardening, a later auth persistence slice should add:

- database schema/entities for users and refresh sessions
- refresh token httpOnly cookie transport
- durable access-token/session revocation
- backend-connected Nuxt login flow
