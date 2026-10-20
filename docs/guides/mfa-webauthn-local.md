# WebAuthn 로컬 연결 가이드

패스키 목록·삭제와 TOTP 잠금 해제·분실 기기 교체는 [로컬 MFA 복구 가이드](mfa-recovery-local.md)를 따른다.

이 구현은 **이미 로그인한 사용자가 패스키를 등록하고, 패스키로 추가 인증하여 5분 MFA access token을 받는 흐름**이다. 패스키만으로 처음 로그인하는 기능은 아니다. refresh는 이번 범위에서 제외했다.

## 먼저 볼 포인트 6가지

| 포인트 | 이 프로젝트에서 확인할 값과 이유 |
| --- | --- |
| 등록과 인증 | `create()`는 새 공개키를 계정에 연결하고, `get()`은 등록된 키로 새 challenge에 서명한다. 등록 성공만으로 새 MFA JWT를 발급하지 않는다. |
| RP ID와 Origin | RP ID는 `localhost`이고 포트가 없다. Origin은 `http://localhost:18081`처럼 scheme·host·port 전체다. 주소창을 `127.0.0.1`로 바꾸면 같은 설정으로 사용할 수 없다. |
| 사용자 확인(UV) | 등록·인증 options 모두 `userVerification: required`다. 키를 터치하는 사용자 존재(UP) 확인과 PIN·생체 등을 통한 UV는 다르다. 기기에 따라 PIN 또는 생체 UI가 나타난다. |
| 계정 결속 | 사용자 UUID는 요청 body가 아닌 검증된 JWT에서 가져온다. 등록에는 최근 MFA가 필요하고, assertion으로 검증된 사용자도 JWT 사용자와 일치해야 한다. |
| challenge와 JWT | challenge는 3분짜리 일회성 요청이다. accessToken은 API 호출 권한이다. 둘을 혼용하거나 이전 인증 session을 새 MFA 증명으로 재활용하지 않는다. |
| 저장 위치 | 서버 DB에는 credential ID·공개키·사용자 연결·검증 메타데이터를 저장한다. authenticator의 개인키나 PIN·생체 원본을 서버에 전송하지 않는다. |

개념은 [MDN Web Authentication API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API)의 등록/인증 절을 먼저 보고, 구현 연결은 [Spring Security Passkeys](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)의 JDBC 저장과 options 흐름을 보면 좋다. UV와 보안 경계의 정확한 의미는 [W3C WebAuthn 보안 고려사항](https://www.w3.org/TR/webauthn-3/#sctn-security-considerations)을 참고한다.

## Spring 내장 기능을 어디까지 쓰는가

- Spring `Webauthn4JRelyingPartyOperations`: options 생성, challenge·Origin·RP·서명·UV의 실제 프로토콜 검증.
- Spring `JdbcPublicKeyCredentialUserEntityRepository`, `JdbcUserCredentialRepository`: 사용자 handle과 credential 영속화.
- Spring `WebauthnJacksonModule`: WebAuthn JSON 직렬화/역직렬화.
- 애플리케이션 코드: Bearer 사용자 확인, 등록 권한, 일회 challenge 수명·동시 소비, 사용자별 속도·개수 제한, 기존 Ed25519 MFA 토큰 발급.

Spring의 기본 `/webauthn/register`·`/login/webauthn` filter와 form login은 설치하지 않았다. 현재 identity 서비스는 Bearer JWT 기반이므로 세션 로그인 체계까지 추가하지 않고 native 검증 API를 연결했다. 따라서 Spring 문서의 기본 URL을 그대로 호출하면 안 된다.

브라우저는 별도 패스키 라이브러리 대신 [parseCreationOptionsFromJSON](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/parseCreationOptionsFromJSON_static), `parseRequestOptionsFromJSON`, `toJSON`을 사용한다. 이를 지원하지 않는 브라우저는 명시적으로 안내하고 중단한다. 직접 Base64url/ArrayBuffer 변환기를 늘리지 않았다.

## 로컬 실행 준비

작업 위치: `C:/git/discord.worktrees/task_MFA-backend-transfer`, branch `task_MFA-backend-transfer`.

필요한 것:

1. Java 21과 저장소 Gradle wrapper.
2. 전용 로컬 PostgreSQL DB와 접근 계정. 기존 서비스 DB나 운영 DB를 대상으로 이 SQL을 실행하지 않는다.
3. 이 identity 서비스용 Ed25519 키 설정과 로컬 테스트 사용자의 기존 access token.
4. 활성 TOTP 등록과 해당 TOTP를 확인해서 받은 최근 MFA access token. 등록에는 일반 access만으로 부족하다.
5. WebAuthn JSON API를 지원하는 브라우저와 PIN·생체 또는 호환 보안키.

**2026-09-19 갱신:** identity runtime 자체에는 여전히 로그인이 없지만, 이 페이지는 이제 로컬 boot `legacy-auth` 서버의 실제 `/api/auth/signup`·`/api/auth/login`으로 로그인해 최초 access token을 받는다(아래 "실제 로그인 연결" 절). 위 3~4번 항목의 "기존 토큰"은 그 로그인으로 대체되며, 붙여넣기 토큰 입력은 화면에서 제거했다. 브라우저에서 JWT를 임의 생성하는 예제, 공용 demoCode, 운영 키를 사용하는 편법은 여전히 제공하지 않는다.

### DB 준비

저장소 루트에서 전용 DB에 적용한다. 암호는 명령 인자에 넣지 말고 프롬프트를 사용한다.

```bash
psql --host=localhost --dbname=discord_mfa_local --username=mfa_local -W \
  -v ON_ERROR_STOP=1 \
  -f backend/services/identity/src/main/resources/db/mfa-schema.sql
```

기존 로컬 TOTP DB에는 `ALTER TABLE mfa_totp ADD COLUMN IF NOT EXISTS webauthn_started_at BIGINT`가 적용된다. 기존 TOTP 행/secret을 삭제하지 않는다. 새 `user_entities`, `user_credentials` 이름을 이미 다른 용도로 사용하는 DB에는 그대로 적용하지 말고 먼저 충돌을 확인한다. SQL은 자동 실행되지 않는다.

### 서버 설정

버전 관리 밖의 로컬 설정 파일(예: `C:/tmp/discord-mfa-local.yml`)에 기존 `discord.auth.jwt` 설정과 다음 설정을 합친다. 아래 환경변수는 로컬 환경에서 준비하며 비밀값을 커밋하지 않는다.

```yaml
server:
  address: 127.0.0.1
  port: 18081
  servlet:
    session:
      timeout: 3m
      cookie:
        http-only: true
        same-site: strict
discord:
  auth:
    mfa:
      enabled: true
      jdbc-url: jdbc:postgresql://127.0.0.1:5432/discord_mfa_local
      database-user: mfa_local
      database-password: ${MFA_LOCAL_DB_PASSWORD}
      encryption-key: ${MFA_LOCAL_ENCRYPTION_KEY}
    webauthn:
      enabled: true
      origin: http://localhost:18081
```

`MFA_LOCAL_ENCRYPTION_KEY`는 32 byte AES key의 Base64다. TOTP 등록 후 키를 변경하면 기존 secret을 해독할 수 없으므로 보존한다. JWT 설정은 기존 `IdentityServiceApplication.JwtProperties`의 issuer, audience, key-id, private-key-location, public-key-locations 계약을 그대로 쓴다.

```bash
./gradlew --version
./gradlew :backend:services:identity:bootRun \
  --args='--spring.config.additional-location=file:C:/tmp/discord-mfa-local.yml'
```

실행한 뒤 [로컬 연결 화면](http://localhost:18081/mfa-webauthn.html)을 연다. **이 링크는 해당 포트에 직접 서버를 실행한 뒤에만 동작한다.** 이 작업이 서버를 상시 실행해 두었다는 뜻은 아니다.

### 화면에서 진행

1. (2026-09-19부터) 화면의 로그인 폼으로 로그인한 뒤 TOTP 등록·확인·추가 인증으로 최근 MFA access를 만든다. 단계 상세는 아래 "실제 로그인 연결" 절을 따른다. 토큰은 페이지 메모리에만 보관한다.
2. **패스키 등록**을 누르고 브라우저/OS의 PIN·생체 안내를 따른다.
3. 등록이 완료되면 최소 10초 후 **패스키로 추가 인증**을 누른다. 브라우저가 새 challenge에 대한 확인을 요청한다.
4. 성공하면 메모리의 토큰이 `amr=[passkey]`, 현재 `auth_time`, 최대 5분 만료를 가진 새 access로 교체된다.
5. **현재 토큰으로 프로필 확인**을 눌러 `/api/users/@me`에서 같은 UUID를 조회한다. 이 endpoint 자체가 MFA 전용인 것은 아니며 토큰 소비 호환성 확인 용도다.
6. **토큰 지우기** 또는 새로고침으로 페이지 메모리를 정리한다. 이는 서버 측 토큰 폐기나 패스키 삭제와 다르다.

## Network 탭에서 따라가기

| 순서 | POST 경로 (`/api/auth/mfa/webauthn` 기준) | 요청/응답 |
| --- | --- | --- |
| 등록 준비 | `/register/options` | 최근 MFA Bearer → native creation options |
| 등록 완료 | `/register/complete` | `{credential: credential.toJSON(), label}` → 204 |
| 인증 준비 | `/authenticate/options` | 기존 access Bearer → native request options |
| 인증 완료 | `/authenticate/complete` | `credential.toJSON()` → `{accessToken}` |

모든 POST에는 Bearer와 정확한 Origin이 필요하다. 브라우저가 Origin을 설정하며 JS에서 임의로 넣지 않는다. options와 complete는 같은 session 쿠키를 사용하지만 **쿠키만으로는 API 인증이 되지 않는다**. session에는 미완료 ceremony만 저장하고 Spring 로그인 상태를 저장하지 않는다.

관찰할 값은 endpoint 순서, 응답 상태, RP ID, UV 정책, challenge가 매번 바뀌는지다. Authorization, 쿠키, assertion 원문, token 응답을 스크린샷·HAR·로그로 공유하지 않는다. 브라우저 개발자 도구에는 이런 값이 실제로 보인다.

## 막힐 때 가장 싼 확인

| 증상 | 먼저 확인 |
| --- | --- |
| 연결 거부/404 | identity 프로세스·포트·WebAuthn enabled 여부. 기존 예제 서버 주소와 혼동하지 않았는지 확인. |
| 401 | 토큰 만료, session 쿠키 누락, 3분 초과, 이미 사용한 challenge 여부. caller 검증을 통과한 동일 사용자의 완료 시도는 실패해도 소비하므로 options부터 다시 시작. 잘못된 Bearer·Origin·다른 사용자의 요청은 기존 pending을 소비하지 않음. |
| 403 | 등록에 일반 access를 썼는지, Origin의 scheme/host/port가 정확한지, loopback 접속인지. |
| 409 | 활성 TOTP가 없는지, 등록 credential이 없는지, 사용자별 5개 제한에 도달했는지, 또는 마지막 패스키 삭제 정책에 걸렸는지 확인. |
| 429 | 같은 사용자는 다른 탭/session도 포함해 options를 10초 간격으로 요청해야 함. |
| 503 | schema 적용과 DB 연결 설정. 설정·DB 오류를 인증 성공으로 우회하지 않음. |
| `NotAllowedError` | 취소·시간 초과·사용 가능한 패스키 없음 등 여러 원인 가능. OS 창과 authenticator 선택을 확인하고 새 options로 재시도. |
| `SecurityError` | secure context, RP ID와 주소창 host 관계부터 확인. |

## 보안상 과장하면 안 되는 부분

- `required` UV는 요청 옵션 표시뿐 아니라 서버 검증에서도 강제한다. 현재 관리 버전은 Spring Security **7.1.1**이며, 분산 session 직렬화 시 UV 검증 우회 문제의 수정 버전이다. [Spring 보안 공지 CVE-2026-47841](https://spring.io/security/cve-2026-47841/)를 참고한다.
- 패스키는 WebAuthn을 사용하지만 모든 패스키가 물리 보안키에 고정되는 것은 아니다. 동기화 여부와 authenticator 정책에 따라 다르다. `attestation=none`인 이 데모는 장치 제조사/하드웨어 신뢰 등급을 보증하지 않는다.
- RP/Origin 결속이 XSS, 이미 탈취된 access token, 취약한 최초 로그인, 계정 복구까지 해결하지는 않는다.
- 로컬 단일 JVM이 전제다. session별 원자 소비를 여러 서버에 그대로 확장하면 안 된다. reverse proxy·분산 session·공개 도메인·TLS 배포는 새 검증 게이트다.
- TOTP 잠금 해제와 기기 교체는 등록 패스키가 있어야 한다. 패스키가 모두 분실된 경우 이 데모에는 self-service 복구 경로가 없다. 키 rotation, 최초 로그인 UI, refresh도 범위 밖이다.

## 실행한 검증과 남은 검증

현재 실행한 계약 검증:

```bash
./gradlew :backend:services:identity:test :backend:modules:identity:test \
  :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest
node --check backend/services/identity/src/main/resources/static/mfa-webauthn.js
```

`WebAuthnControllerTest`는 실제 P-256 key로 서명한 assertion과 CBOR attestation을 native verifier에 전달한다. UV 누락·Origin 변조·다른 사용자·challenge 교체/만료/재사용을 검증하고, 여러 session의 속도 제한·동시 소비·동시 등록 5개 제한을 확인한다. `WebAuthnRuntimeTest`는 enabled Spring runtime wiring과 기존 health/profile 동작을 확인한다.

2026-09-17에는 실제 Chrome + CDP 가상 CTAP2 authenticator로 TOTP 등록/확인 HTTP → 새 TOTP challenge 완료 → 이 HTML의 패스키 등록 → 패스키 assertion → `amr=[passkey]` JWT → 같은 사용자의 프로필 조회까지 통과했다. token 입력창 비움과 localStorage/sessionStorage 미사용도 확인했다. 임시 서버는 종료했다.

재현용 로컬 artifact는 `qa/artifacts/webauthn-smoke.init.gradle`과 `qa/artifacts/webauthn-browser-smoke.cjs`다. 전자는 H2 test classpath와 저장소 테스트 키를 사용하는 임시 loopback 서버, 후자는 실제 화면 버튼과 native 브라우저 API를 사용하는 smoke다. 이 파일들은 Git에서 제외되며 재현 환경의 Playwright 설치 위치에 의존한다. 운영 실행용 파일이 아니다.

H2 검증은 실제 PostgreSQL 검증을 대체하지 않는다. 가상 authenticator는 Windows Hello·휴대전화·물리 보안키의 실제 사용자 경험 검증을 대체하지 않는다. 독립 보안 검수는 이 로컬 조각에 한정해 95/100, 미해결 P0/P1/P2 없음이다. 빈 실패 세션 생성 문제는 RED 재현 후 수정했고 409/429 응답에서 세션이 생기지 않는 회귀 테스트가 통과했다. 현재 문서는 전체 MFA 이관 완료·운영 배포·main 병합 승인 문서가 아니다.

참고한 프로젝트 Wiki: `index.md`, `Backend Architecture`, `QA Infra Operations`, `Agent Development Guide`. 과거 예제 서버의 브라우저 성공 기록을 이번 identity runtime의 성공 증거로 옮겨 쓰지 않았다.

## PostgreSQL 실측 검증 (2026-09-19)

H2 검증과 별도로, 실제 PostgreSQL에서 TOTP·native passkey 영속화 계약을 확인하는 opt-in 테스트를 추가했다. 기본 `./gradlew test`에서는 skip되며 Docker가 필요하지 않다.

```bash
# Docker Desktop이 실행 중이어야 한다. 일회용 postgres:16-alpine 컨테이너를 Testcontainers가 만들고 ryuk이 지운다.
qa/mfa-postgres-verify.sh
# 동일 명령의 수동형
DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:services:identity:cleanTest :backend:services:identity:test \
  --tests com.example.discord.identityservice.PostgresMfaPersistenceTest
```

`PostgresMfaPersistenceTest`는 두 가지를 확인한다.

| 테스트 | 확인 내용 |
| --- | --- |
| adapter 재생성·동시성 | 같은 DB에 `JdbcTotpStore`를 두 개 만들고 8 thread로 confirm/challenge/complete를 동시에 호출해 각각 정확히 하나만 성공, 패자는 실패 횟수를 올리지 않음, 소비한 challenge·step 재사용 거부, 5회 실패 후 fail-closed, 중복 enroll이 SQLState 23505로 409가 되는 경로 |
| application 재시작 | `SpringApplicationBuilder`로 identity runtime을 loopback Tomcat에 기동해 실제 HTTP로 TOTP enroll→confirm→challenge→complete와 passkey 등록을 수행하고, 컨텍스트를 `close()`한 뒤 같은 DB로 다시 기동해 TOTP challenge/complete, 등록된 passkey로 인증(`amr=[passkey]`), 같은 challenge replay 401, 4 thread 동시 complete 중 하나만 200을 확인 |

기본 origin은 기존 runtime 테스트와 같은 `http://localhost:8080`이고 서버 포트는 `server.port=0`으로 임의 배정된다. `WebAuthnConfiguration`은 origin의 형식만 검사하고 실제 포트와 비교하지 않으므로 이 조합이 성립한다. 클라이언트는 `remoteAddr` 검사 때문에 `127.0.0.1`로 접속한다.

실행 결과(2026-09-19, 4회 연속): `PostgreSQL 16.14 on x86_64-pc-linux-musl`(postgres:16-alpine), DB/사용자 `mfa_pg_verify`, 2 tests / 0 skipped / 0 failures. 스크립트는 실행 전후 `docker ps -aq` 스냅샷을 비교해 기존 컨테이너 19개가 그대로이고 새 컨테이너가 남지 않았음을 확인했다. 상세 근거와 명령은 `docs/03-analysis/mfa-postgres-verification.md`에 있다.

이 검증이 말하지 않는 것: 운영 DB·외부 DB, 실제 기기 UV, 분산 session, 복구/삭제 API, refresh. `signature_count`/`last_used`는 인증 성공 후 기록됨만 확인했고, Spring Security 7.1.1은 검증 시 저장된 counter 대신 등록 attestation의 signCount를 기준으로 하므로 이 결과를 "재시작 후 클론 authenticator 탐지"로 해석하지 않는다.

## 실제 로그인 연결: 로그인 → TOTP → 패스키 → 보호 API (2026-09-19)

위 "현재 제약"에 적었던 "로그인 화면이 없다"는 상태를 로컬 범위에서 해소했다. 같은 `mfa-webauthn.html`이 이제 **실제 legacy-auth 발급자(boot)** 로 회원가입·로그인하고, 받은 일반 access로 TOTP 등록·확인·추가 인증, 최근 MFA 토큰으로 패스키 등록, 패스키 step-up, 그리고 identity와 boot 양쪽 `/api/users/@me`가 같은 사용자인지 확인한다. identity·boot의 main Java, schema, 의존성은 바꾸지 않았고 정적 페이지·JS 3개와 QA 스크립트, 테스트만 추가·수정했다.

### 토폴로지와 왜 이렇게 되는가

| 구성 | 값 | 근거 |
| --- | --- | --- |
| 로그인 서버 | boot `DiscordApplication`, 프로필 `legacy-auth,test`, `InMemoryAuthStore` | 실제 발급자는 `backend/boot/.../auth/AuthController`(`POST /api/auth/signup`, `/api/auth/login`)뿐이다. identity 서비스에는 로그인이 없고 새로 만들지 않았다(가짜 로그인·임의 토큰 발급 금지). |
| 왜 `test` 프로필 | boot 기본 프로필은 `ExpressionService`/`ThreadService` bean이 `postgres` 또는 `test`에만 있어 `legacy-auth` 단독 기동이 실패한다 | `expression/ExpressionConfiguration.java`, `thread/ThreadConfiguration.java`, test classpath의 `TestExpressionConfiguration`. `test` 프로필은 auth와 무관한 mock만 공급하고 로그인 경로(`AuthService`, `BCryptPasswordHasher`, `AccessTokenService`)는 main 코드다. boot test runtime classpath를 `bootRun`에 더하는 `qa/mfa-local-ui/boot-legacy-auth.init.gradle`이 필요하다. |
| 키 계약 | 두 서버가 같은 `discord.auth.jwt` issuer/audience/kid/키를 쓴다 | boot가 발급한 1시간 access를 identity가 검증하고, identity가 발급한 5분 MFA access를 boot `AuthService.profileForToken`이 같은 `AccessTokenService.verify`로 받아들인다. |
| 브라우저 경계 | 페이지는 identity origin(`http://localhost:<identity port>`)에서 열고 boot는 cross-origin으로 호출 | boot `OperationalHardeningFilter`가 `^http://(127\.0\.0\.1|localhost):\d{2,5}$` Origin에 CORS를 허용하고 preflight를 204로 답한다. 페이지의 CSP `connect-src`도 `'self' http://127.0.0.1:* http://localhost:*`로 같은 집합만 허용하고, 로그인 서버 주소 입력값을 같은 정규식으로 검증한다. |
| 토큰 보관 | `accessToken`(로그인 1시간)과 `mfaToken`(identity 최신 5분)을 별도 변수로 보관 | TOTP·패스키 인증·프로필은 access, 패스키 등록은 mfa, 보호 API 확인은 유효한 mfa가 있으면 그것을 쓰고 어느 토큰을 썼는지 상태 문구에 `[...]`로 표시한다. 5분 MFA 토큰이 만료돼도 재로그인 없이 추가 인증만 다시 한다. |
| 메모리-only | closure 변수와 입력창에만 존재 | boot 호출은 `credentials: "omit"`이라 `dc_refresh` 쿠키가 저장되지 않는다(refresh 미사용). localStorage/sessionStorage/IndexedDB를 쓰지 않는다. 비밀번호 입력창은 요청 직후 비운다. identity `JSESSIONID`는 진행 중 WebAuthn ceremony 결속 전용이다. |

### 두 서버 기동 (수동)

Gradle daemon 밖에서 실행하면 종료가 단순해진다(`--no-daemon`). 값은 예시이며 identity 포트와 `discord.auth.webauthn.origin`의 포트는 일치해야 한다.

```bash
# 1) boot legacy-auth 로그인 서버 (in-memory 계정: 재시작하면 계정이 사라진다)
SPRING_PROFILES_ACTIVE=legacy-auth,test \
SPRING_APPLICATION_JSON='{"server":{"address":"127.0.0.1","port":18092},"management":{"health":{"redis":{"enabled":false}}},"discord":{"auth":{"jwt":{"issuer":"discord-identity","audience":"discord-api","key-id":"identity-2026-07","private-key-location":"file:C:/git/discord.worktrees/task_MFA-backend-transfer/backend/modules/identity/src/test/resources/identity/ed25519-private.pem","public-key-locations":{"identity-2026-07":"file:C:/git/discord.worktrees/task_MFA-backend-transfer/backend/modules/identity/src/test/resources/identity/ed25519-public.pem"}}}}}' \
./gradlew --no-daemon --init-script qa/mfa-local-ui/boot-legacy-auth.init.gradle :backend:boot:bootRun

# 2) identity MFA 서버 (H2 in-memory; PostgreSQL을 쓰려면 init script 없이 위 "서버 설정" 절의 파일을 사용)
SPRING_APPLICATION_JSON='{"server":{"address":"127.0.0.1","port":18091},"discord":{"auth":{"jwt":{"issuer":"discord-identity","audience":"discord-api","key-id":"identity-2026-07","private-key-location":"file:C:/git/discord.worktrees/task_MFA-backend-transfer/backend/modules/identity/src/test/resources/identity/ed25519-private.pem","public-key-locations":{"identity-2026-07":"file:C:/git/discord.worktrees/task_MFA-backend-transfer/backend/modules/identity/src/test/resources/identity/ed25519-public.pem"}},"mfa":{"enabled":true,"jdbc-url":"jdbc:h2:mem:mfa-local;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '\''C:/git/discord.worktrees/task_MFA-backend-transfer/backend/services/identity/src/main/resources/db/mfa-schema.sql'\''","database-user":"sa","database-password":"","encryption-key":"<32 byte 랜덤 AES key Base64>"},"webauthn":{"enabled":true,"origin":"http://localhost:18091"}}}}' \
./gradlew --no-daemon --init-script qa/mfa-local-ui/identity-h2.init.gradle :backend:services:identity:bootRun
```

저장소 테스트 키 fixture는 **로컬 실행 전용**이다. 운영 키·운영 계정을 이 절차에 넣지 않는다. 두 init script는 classpath만 바꾸고 인자는 환경변수(`SPRING_APPLICATION_JSON`)로 준다.

### 화면에서 진행

1. `http://localhost:18091/mfa-webauthn.html`을 열고 로그인 서버 주소(`http://127.0.0.1:18092`)를 확인한다. loopback이 아닌 주소는 페이지가 거부하고 CSP도 차단한다.
2. 이메일·비밀번호·사용자 이름(`^[a-z0-9_](?:[a-z0-9_.]{1,30}[a-z0-9_])$`)·표시 이름을 넣고 **회원가입**(201) 후 **로그인**(200). 로그인은 IP당 분당 2회, 비밀번호 3회 실패 시 15분 잠금이다. 상태 문구에 사용자 UUID가 보이고 토큰 상태 줄이 "일반 access"로 바뀐다.
3. **TOTP 등록 시작** → 표시된 Base32 secret 또는 `otpauth://` URI를 인증 앱에 등록 → 현재 코드 입력 → **등록 확인**(204, secret 표시 제거). 다음 코드를 입력하고 **TOTP로 추가 인증**(challenge → complete, 5분 `amr=[totp]`). 확인에 쓴 코드는 재사용되지 않는다(검증 창은 현재±1 step, `consumed_step` 초과만 허용). 이미 등록된 계정은 등록 시작이 409이며 재시작 API가 없으므로 바로 추가 인증으로 간다.
4. **패스키 등록**(최근 5분 MFA 토큰 필요) → 10초 후 **패스키로 추가 인증**(`amr=[passkey]`).
5. **보호 API 확인**: identity와 로그인 서버의 `/api/users/@me`를 같은 토큰으로 호출해 로그인 응답의 `user.id`와 세 값이 동일한지 표시한다.
6. **토큰 지우기** 또는 새로고침. 서버 측 토큰 폐기·패스키 삭제·계정 삭제는 아니다.

### 자동 브라우저 E2E (opt-in)

```bash
# Playwright가 설치된 checkout(예: 루트에서 npm ci를 마친 곳)을 가리킨다. Chrome이 설치되어 있어야 한다(MFA_E2E_BROWSER_CHANNEL=chromium이면 번들 Chromium).
MFA_E2E_PLAYWRIGHT_ROOT=C:/git/discord node qa/mfa-local-ui-e2e.mjs
```

러너는 빈 포트 2개를 잡아 boot(`legacy-auth,test`)와 identity(H2)를 `--no-daemon`으로 기동하고 health를 기다린 뒤, Chrome + CDP 가상 CTAP2 authenticator로 실제 화면 버튼을 눌러 다음을 단언한다: 회원가입 201 → 오답 로그인 401(토큰 없음) → 로그인 200(비밀번호 입력창 빈 값) → TOTP 등록 200 → 오답 코드 401 → 확인 204 → 다음 step 코드로 추가 인증 200(`amr=[totp]`, `sub` 동일) → 페이지 컨텍스트 fetch로 일반 access `register/options` 403·무토큰 401 → 패스키 등록 204 → 10초 후 패스키 인증 200(`amr=[passkey]`, `exp-auth_time ≤ 300`) → identity·boot `/api/users/@me` 200과 상태 문구의 동일 UUID 3회 → storage 0, `indexedDB` 0, 쿠키는 `JSESSIONID`만(`dc_refresh` 없음) → 지우기 후 프로필 버튼 비활성·무토큰 `/api/users/@me` 401. 끝나면 두 프로세스 트리를 종료하고 두 포트가 30초 안에 연결 거부로 바뀌는지 확인한다(실패 시 exit 3).

artifact는 `qa/artifacts/mfa-local-ui-e2e/<stamp>/`(gitignored)에 `boot.log`, `identity.log`, `summary.json`(포트·사용자 UUID·단계별 HTTP 상태·쿠키 이름)이 남는다. 토큰·비밀번호·secret·코드는 출력하지도 저장하지도 않는다. 러너의 계정은 실행마다 `mfa-local-<stamp>@example.com`과 난수 비밀번호이며 프로세스 메모리에만 있다.

2026-09-19 실행 결과: 2회 연속 `PASS`, 단계별 상태 `signup 201, loginWrongPassword 401, login 200, enroll 200, confirmWrongCode 401, confirm 204, totpComplete 200, registerOptionsWithLoginToken 403, registerOptionsWithoutToken 401, registerComplete 204, authenticateComplete 200, identityProfile 200, loginServerProfile 200, profileWithoutToken 401`, `storageEntries 0`, `cookieNames ["JSESSIONID"]`, 두 포트 정리 확인. 로그에는 JWT·Bearer·password·secret 문자열이 없었다.

### 단위·계약 테스트

```bash
node --test "qa/mfa-local-ui/*.test.mjs"                                   # 페이지 순수 함수 8건(URL/자격증명/코드 검증, 표시용 claim 해석, 상태 문구, 버튼 가드)
./gradlew :backend:services:identity:test --tests com.example.discord.identityservice.MfaLocalUiRuntimeTest
# 정적 자산 서빙·CSP 정확 문자열·inline script 부재·storage API 부재, boot 계약 토큰(동일 키·amr 없음)으로 TOTP 200/@me 200/register options 403/authenticate options 409/Origin 불일치 403, 다른 키 서명 토큰 401 전부
./gradlew :backend:services:identity:test :backend:modules:identity:test :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest
```

### 이 연결이 말하지 않는 것과 잔여 위험

- boot `test` 프로필과 저장소 테스트 키, in-memory 계정은 **로컬 데모 조건**이다. 운영 로그인·운영 배포·refresh·계정 복구·삭제·secret/key rotation은 여전히 별도 작업이다.
- 정적 페이지는 `discord.auth.webauthn.enabled` 값과 무관하게 identity jar에 실려 서빙된다(기존 상태). 로그인 서버 주소 입력은 정규식과 CSP로 loopback에 한정되지만, 공개 배포에 이 페이지를 그대로 노출하지 않는다.
- identity 서비스에는 Spring Security 필터체인이 없어 정적 응답에 `X-Frame-Options`/`X-Content-Type-Options`/`Referrer-Policy` 헤더가 없고 meta CSP의 `frame-ancestors`는 브라우저가 무시한다. 다른 로컬 포트의 페이지가 이 화면을 iframe으로 감쌀 수 있다(토큰은 메모리 전용이고 교차 출처 읽기는 불가). 응답 헤더 filter는 main Java 변경이라 이 조각의 non-goal이며 후속 조각 후보다(Security Review P2).
- "메모리-only"는 페이지 스토리지 범위다. Chrome은 `autocomplete="off"`를 무시하고 비밀번호 저장을 제안할 수 있으므로 로컬 테스트 비밀번호를 브라우저에 저장하지 않는다. Network 내보내기·HAR에는 토큰이 들어간다.
- 가상 authenticator·H2·Windows 프로세스 정리만 실측했다. 실제 기기 UV, PostgreSQL 조합(별도 절에서 실측), 비Windows 정리 경로는 이 E2E의 증거가 아니다.
- boot 로그인 rate limit(분당 2회)과 TOTP 5회 실패 잠금 때문에 러너는 오답을 각 1회만 시도한다. 사람이 반복 실패하면 15분(로그인) 또는 계정 잠금(TOTP, 복구 API 없음)이 된다.
