# T01 Identity/User Plan

작성일: 2026-05-13  
PDCA Phase: Plan

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 인증은 모든 Discord 기능의 선행 조건이며, 토큰/세션/프로필 정책이 약하면 이후 서버/채널/메시지 권한 검사가 무너진다. |
| Solution | `identity`와 `user`를 별도 backend module로 만들고, password hashing, JWT access token, refresh rotation, lockout, profile validation을 TDD로 고정한다. |
| Function UX Effect | 프론트는 아직 실제 로그인 API에 연결하지 않지만, 다음 단계에서 login UI/e2e를 붙일 수 있도록 인증 정책을 먼저 안정화한다. |
| Core Value | 인증 실패/토큰 만료/refresh 재사용 같은 보안 회귀를 단위 테스트로 빠르게 잡는다. |

## Scope

- email value object
- password hashing
- access token issue/verify/expiry
- refresh token session rotation/revocation
- login failure lockout
- user profile validation
- boot module dependency wiring
- architecture gate update

## Out of Scope

- database persistence
- REST auth controller
- httpOnly cookie wiring
- Nuxt login form

위 항목은 T01-B API/UI slice에서 이어서 구현한다. 이번 slice는 보안 정책을 먼저 고정하는 backend foundation이다.

## Success Criteria

- password hash is not raw password and verifies only correct password
- expired access token is rejected
- refresh token rotation revokes old session and creates a new token hash
- repeated invalid login attempts lock an email for the configured duration
- invalid usernames are rejected
- `.\gradlew.bat test` passes
- existing Nuxt component/e2e smoke tests still pass

## Failure Criteria

- password stored or compared as plain text
- refresh token can be reused after rotation
- access token expiry is not enforced
- lockout produces ambiguous state
- user profile accepts invalid username

