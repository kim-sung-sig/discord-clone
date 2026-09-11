# Spring Security 7 MFA 모듈 TDD 설계

## 상태

- 승인: 사용자 승인
- 구현 방식: JWT access + 기존 refresh session 재사용
- 작업 위치: `examples/mfa-spring-security7`
- 커밋: 사용자가 MVP 확인을 우선하므로 이번 작업에서 만들지 않는다.

## 목적

이 예제는 Discord 이관 전용 인증 모듈 검증장이다. Spring Security 7 MFA와 One-Time Token(OTT)을 로그인 factor에 사용하고, 임의 factor의 step-up challenge는 서버 상태와 짧은 JWT capability를 함께 사용한다.

## 사용자 모듈 이식

예제는 복사한 사용자 모델을 만들지 않는다. Gradle settings에서 기존 `backend/modules/user`를 `:discord-user` project로 포함하고, `com.example.discord.user.UserProfile`을 직접 사용한다.

사용자 인증 정보는 별도 MFA adapter가 관리한다.

```text
UserProfile(id, username, displayName, createdAt, privacy)
        ↑
MfaUserAccount(email, passwordHash, UserProfile)
```

`UserProfile.id`는 모든 access/auth-factor/auth-challenge JWT의 `sub` 원본이다. email은 subject가 아니다.

## 토큰 및 상태 경계

| 이름 | 저장/전달 | 용도 | 재사용 |
|---|---|---|---|
| login transaction | SQLite | 로그인에 필요한 factor 완료 상태 | 불가 |
| auth-factor JWT | 클라이언트 전용 header | 해당 transaction 완료 capability | 불가 |
| auth-challenge JWT | mock device 전용 header | 하나의 challenge 승인 capability | 불가 |
| access JWT | Bearer | 일반 resource 접근 | TTL까지 가능 |
| elevated access JWT | Bearer, 5분 | level 80 step-up resource 접근 | TTL까지 가능 |
| refresh token | HttpOnly cookie + 서버 hash | base login assurance access 재발급 | rotation 후 불가 |

Access JWT claim은 아래 계약을 따른다.

```json
{
  "sub": "UserProfile UUID",
  "sid": "refresh session UUID",
  "amr": ["password", "ott"],
  "assurance_level": 50,
  "auth_time": "instant",
  "iss": "discord-identity",
  "aud": ["discord-api"]
}
```

Spring Security resource server의 `JwtAuthenticationConverter`는 `amr`을 `FactorGrantedAuthority`로 변환한다. 따라서 `FactorGrantedAuthority.PASSWORD_AUTHORITY`와 `OTT_AUTHORITY`를 기반 factor로 인가할 수 있다. Spring Security 7이 bearer 인증에 더하는 `FACTOR_BEARER`는 별도 factor로 간주하지 않는다.

Step-up 성공은 refresh session의 assurance를 바꾸지 않는다. level 80 access JWT만 발급하며, refresh하면 기존 base level 50 access JWT를 다시 발급한다. 이 규칙은 강한 인증의 freshness를 보장한다.

## 모듈 구조

```text
examples/mfa-spring-security7
  domain/        Factor, AssuranceLevel, LoginTransaction, Challenge
  application/   LoginMfaService, StepUpService, RefreshBoundary
  adapter/       SQLite account/transaction stores, mock OTT/push factor
  security/      SecurityConfiguration, JWT issuer/converter
  web/           Login, challenge, step-up, user endpoints
```

controller는 HTTP 형식만 처리한다. `LoginMfaService`가 factor 충족과 토큰 발급 여부를 결정하며, request body/path에서 user ID를 받지 않는다.

## TDD 규칙과 실패 회귀 목록

모든 production 변경은 아래 순서로 진행한다.

1. 실패하는 테스트를 먼저 작성하고 실패 원인을 확인한다.
2. 가장 작은 production code를 추가한다.
3. focused test와 전체 example test를 통과시킨다.
4. 새로 발견한 실패는 회귀 테스트 이름으로 추가한다.

우선 고정할 실패 사례는 다음과 같다.

1. password만 성공한 사용자는 access/refresh를 받지 못한다.
2. password와 OTT가 모두 완료된 뒤에만 base access/refresh가 발급된다.
3. auth-challenge JWT는 다른 user, challenge, transaction에 사용할 수 없다.
4. 승인·만료된 challenge와 완료 transaction은 재사용할 수 없다.
5. base level 50 access JWT는 finance API에서 `MFA_REQUIRED`다.
6. step-up 성공은 5분 level 80 access만 발급하고 refresh level은 승격하지 않는다.
7. refresh rotation 재사용은 해당 user의 모든 refresh session revoke를 요청한다.
8. access JWT `amr`은 Spring Security 7 factor authority로 변환된다.

## 단계와 완료 기준

1. Gradle에서 실제 `:discord-user` project를 포함하고 UserProfile 사용 테스트를 GREEN으로 만든다.
2. 단일 파일 prototype을 domain/application/adapter/security/web로 분리한다.
3. login 2-factor와 challenge JWT의 RED/GREEN 회귀를 만든다.
4. refresh boundary와 elevated access freshness를 만든다.
5. Spring Security 7 authority converter와 resource authorization을 만든다.
6. full example test와 README flow를 실행한다.

완료는 위 8개 실패 회귀가 테스트로 존재하고 통과하며, 예제가 `UserProfile`을 실제로 소비하고, access/refresh/challenge의 책임 경계가 README와 코드에 일치할 때다.

## 이관 대상과 비대상

- 이관 대상: user module dependency, access JWT claim 계약, refresh rotation boundary, MFA transaction/factor/challenge port, JWT-to-factor authority converter.
- 이관 비대상: SQLite 저장소, mock device approval endpoint, HMAC demo key, demo user seed.
