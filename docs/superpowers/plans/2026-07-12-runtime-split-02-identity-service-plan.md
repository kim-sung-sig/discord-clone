# 런타임 분리: Identity Service 구현 계획

> **에이전트 작업자용:** `executing-plans`를 작업 단위로 사용한다. 진행 상황은 checkbox(`- [ ]`)로 기록한다.

**목표:** 클라이언트 JWT 계약을 바꾸지 않고 사용자 인증과 계정 소유권을 `identity-service`로 이전한다.

**아키텍처:** `identity-service`는 계정 레코드, 비밀번호 해시, refresh token 상태, 소셜 identity 연결, MFA 증적, 이메일 인증, 프로필을 소유한다. 데모에서는 기존 HS256 access JWT를 유지한다. 비밀키는 환경변수·Config Server·Kubernetes Secret 중 배포 환경이 제공하는 비밀 관리 경로로 identity와 검증 런타임에 주입한다. 다른 서비스는 같은 비밀키로 토큰을 로컬 검증하며 identity 테이블을 읽거나 요청 경로에서 identity-service를 호출하지 않는다. 공개키/JWK와 키 회전은 비대칭 서명 전환 시의 후속 확장이다.

**기술 스택:** Java 21, Spring Boot, PostgreSQL/Flyway, BCrypt, JWT, JUnit 5.

## 승인 및 범위

- 상태: 데모 HS256 공유 비밀키 검증 승인 — 2026-07-14.
- 쿠키 경계: login/refresh endpoint는 기존 refresh-cookie 계약을 유지한다. cookie는 `HttpOnly`, `SameSite=Lax`, `/api/auth` 범위, 기존의 제한된 max age를 유지하며 기존 secure-request 정책에서만 `Secure`를 적용한다.
- 무상태 경계: message-service, websocket-service, community-service는 Bearer access JWT만 받는다. refresh cookie 또는 session 상태를 수신·설정·파싱·저장하지 않는다.
- 보류: 비대칭 서명, public-key/JWK endpoint, key rotation, 서비스 간 session 복제, role/room 인가.

## 성공 기준

1. identity-service만 access JWT를 발급하고 refresh-token 상태를 소유한다.
2. login과 refresh response는 기존 refresh-cookie flag 및 클라이언트 token payload 계약을 보존한다.
3. 각 non-identity runtime은 배포 비밀 관리 경로로 주입된 HS256 비밀키만으로 malformed·expired Bearer token을 거부한다.
4. non-identity runtime에는 identity HTTP client, identity datasource, refresh-cookie 처리, account-store 의존성이 없다.
5. 로그와 test output에는 raw access token, refresh cookie, password, 비밀키, MFA code가 없다.
6. Gateway `/auth`는 identity-service로만 route하고 login → refresh → authenticated Bearer request smoke가 통과한다.

## TDD 시나리오

한 번에 하나의 수직 슬라이스를 실행한다. 명시한 공개 인터페이스 테스트를 추가해 RED를 확인하고, GREEN을 만들 최소 코드만 작성한 뒤 집중 회귀 검증을 실행한다.

1. `identity-service`: 유효한 login은 기존 access-token response와 refresh cookie flag를 반환한다.
2. `identity-service`: 유효한 cookie로 refresh하면 새 access token을 반환하고 cookie 계약을 유지한다.
3. `identity-service`: 인증된 profile 조회는 자신이 발급한 Bearer token을 허용한다.
4. `message-service`: identity가 발급한 유효 HS256 Bearer token을 로컬 검증으로 허용한다.
5. `message-service`, `websocket-service`, `community-service`: 만료·손상된 Bearer token을 로컬에서 거부한다.
6. Gateway smoke: `/auth` login과 refresh는 identity-service로 전달되며, non-identity runtime의 인증 요청은 refresh cookie를 요구하지 않는다.

각 슬라이스 뒤에는 해당 서비스 test task를 집중 실행한다. 최종 gate는 네 서비스 전체 테스트, API schema 변경 시 `npm run openapi:check`, `docker compose -f infra/docker/docker-compose.yml config`, Gateway smoke를 실행한다.

---

## 파일 구조

- 이전: `backend/boot/src/main/java/com/example/discord/auth/**`.
- 이전: `backend/boot/src/main/java/com/example/discord/social/**`.
- 생성: `backend/services/identity/src/main/java/com/example/discord/{auth,social,user}/**`.
- 테스트 이전: `backend/boot/src/test/java/com/example/discord/auth/**`.
- 생성: identity 소유 Flyway migration 위치와 서비스별 profile 설정.
- 수정: base-path 변경이 필요할 때만 `docs/api/openapi.json`과 generated client.

### 작업 1: 인증 런타임 경계 이전 — 1 MM

- [ ] `AuthController`, `AuthConfiguration`, `AuthService`, `AuthStore`, password hashing, bearer resolution과 테스트를 identity-service로 이전한다.
- [ ] endpoint를 옮기기 전 login, refresh, 인증 profile 조회의 실패하는 identity-service controller test를 작성한다.
- [ ] refresh cookie flag(`HttpOnly`, `SameSite=Lax`, `/api/auth` path, bounded max age, conditional `Secure`)를 보존한다.
- [ ] 이전 뒤 집중 identity test를 실행하며, 다른 서비스가 이전한 store에 compile-time 의존하지 않게 한다.

### 작업 2: JWT 검증 계약 수립 — 1 MM

- [ ] HS256 algorithm, expiry, subject, key ID와 배포 비밀키 주입 규칙을 포함한 최소 버전형 token-verification contract를 공개한다.
- [ ] message/websocket/community runtime에 만료·malformed token 거부 실패 테스트를 추가한다.
- [ ] 로컬 검증만 구현하며 hot path에 동기 identity-service 호출을 추가하지 않는다.
- [ ] token, cookie, password, MFA code가 로그에 들어가지 않음을 검증한다.

### 작업 3: 계정 부가 기능 이전 — 1 MM

- [ ] social linking, email verification, MFA, profile mutation을 identity API 소유로 이전한다.
- [ ] target-role/room authorization은 identity-service 범위 밖에 둔다.
- [ ] 계정/refresh-token persistence용 database migration과 opt-in PostgreSQL test를 추가한다.

### 작업 4: `/auth` 전환 — 0.5–1 MM

- [ ] `/auth`는 identity-service로만 route한다.
- [ ] Gateway를 통한 login → refresh → authenticated request smoke를 실행한다.
- [ ] smoke 통과 뒤에만 기존 runtime의 중복 auth bean을 제거한다.

**출구 gate:** identity-service만 계정/JWT 발급자이며, 다른 런타임은 토큰을 로컬 검증만 한다.
