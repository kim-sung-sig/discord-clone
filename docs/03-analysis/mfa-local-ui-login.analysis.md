# MFA 로컬 UI 실제 로그인 연결 검증 (machine handoff)

> 이 문서는 다음 agent가 읽는 handoff 문서다. 토큰·비밀번호·TOTP secret·코드·JDBC 자격증명은 기록하지 않는다.
> blueprint: `docs/superpowers/plans/2026-09-14-mfa-backend-transfer.md`의 "2026-09-19 로컬 UI 실제 로그인 연결 조각". 가이드: `docs/guides/mfa-webauthn-local.md`의 "실제 로그인 연결" 절.

## 결론

- identity 서비스의 정적 페이지 `mfa-webauthn.html`이 **실제 legacy-auth 발급자(boot `AuthController`)** 로 회원가입·로그인하고, 같은 사용자의 TOTP 등록·확인·추가 인증 → 패스키 등록·step-up → identity·boot 양쪽 `/api/users/@me` 동일 사용자 확인까지 메모리-only 토큰으로 완주함을 **실제 브라우저(Chrome + CDP 가상 CTAP2 authenticator)로 2회 연속 확인**했다.
- identity·boot의 main Java, schema, `build.gradle.kts`, 의존성은 변경하지 않았다. 새 endpoint, 임의 토큰 발급, demoCode, refresh는 없다. 변경은 identity 정적 자산 3개(main resource), 테스트 1개, QA 스크립트 4개, 문서 3개다.
- 계정 출처는 coordinator 결정 A: boot `legacy-auth` in-memory 저장소 + 런타임 signup/login 폼. 테스터·러너 자격증명은 저장소·문서·로그·artifact에 남기지 않는다.
- 이 결과는 로컬 loopback·저장소 테스트 키·H2·가상 authenticator·Windows 조건의 검증이다. 운영 로그인, 배포, 실제 기기 UV, PostgreSQL 조합(별도 실측 존재), 비Windows 프로세스 정리, main 병합 승인이 아니다. commit/merge/push/예제 삭제는 하지 않았다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| worktree / branch | `C:/git/discord.worktrees/task_MFA-backend-transfer` / `task_MFA-backend-transfer` (HEAD `19ee3e4`, 기존 dirty 이관 변경 보존) |
| Gradle / JDK / Node | Gradle 8.14.3 wrapper, Temurin 21.0.7, Node v24.12.0 |
| Playwright | `C:/git/discord/node_modules/playwright` 1.60.0 (`MFA_E2E_PLAYWRIGHT_ROOT`로 지정; 이 worktree에는 `node_modules`가 없음), Chrome 설치본(`channel: chrome`) |
| boot 기동 | `SPRING_PROFILES_ACTIVE=legacy-auth,test` + `qa/mfa-local-ui/boot-legacy-auth.init.gradle`(test runtime classpath) + `--no-daemon`. 기본 프로필은 `ExpressionService`/`ThreadService` bean 부재로 기동 실패(probe에서 확인, `expressionController` UnsatisfiedDependency) |
| identity 기동 | `qa/mfa-local-ui/identity-h2.init.gradle` + H2 in-memory(`INIT=RUNSCRIPT` schema) + `discord.auth.mfa/webauthn.enabled=true`, `server.address=127.0.0.1`, origin `http://localhost:<port>` + `--no-daemon` |
| 키 | 두 서버 모두 저장소 테스트 fixture `backend/modules/identity/src/test/resources/identity/ed25519-*.pem`, issuer `discord-identity`, audience `discord-api`, kid `identity-2026-07` (로컬 전용) |

## 변경 파일 (task 소유)

| 경로 | 변경 |
| --- | --- |
| `backend/services/identity/src/main/resources/static/mfa-webauthn.html` | 단계형 화면(로그인 서버 주소·이메일·비밀번호·사용자 이름·표시 이름 폼, TOTP, 패스키, 보호 API 확인, 토큰 상태). CSP `connect-src 'self' http://127.0.0.1:* http://localhost:*`. 붙여넣기 토큰 입력 제거 |
| `backend/services/identity/src/main/resources/static/mfa-webauthn.js` | ESM DOM 배선. `accessToken`(로그인 1h)과 `mfaToken`(identity 5분) 분리 보관, boot 호출 `credentials: "omit"`, 비밀번호 입력창 즉시 비움, 상태 문구에 토큰·비밀번호·코드·secret 미출력 |
| `backend/services/identity/src/main/resources/static/mfa-local-core.js` | 신규 순수 함수(loopback URL·자격증명·username·코드 검증, 표시용 claim 해석, otpauth URI, 상태 문구, 버튼 가드) |
| `backend/services/identity/src/test/java/com/example/discord/identityservice/MfaLocalUiRuntimeTest.java` | 신규 3 tests(+상속 3): 정적 자산 서빙·CSP 정확 문자열·inline script 부재·storage API 부재·`"omit"`/`no-store` 존재; boot 계약 토큰(동일 키, `amr` 없음) → `@me` 200, enroll 200, `register/options` 403, `authenticate/options` 409, Origin 불일치 403; 다른 키 서명 토큰 → 401 전부, 무토큰 401 |
| `qa/mfa-local-ui/core.test.mjs` | 신규 Node 내장 test runner 8 tests |
| `qa/mfa-local-ui/boot-legacy-auth.init.gradle`, `qa/mfa-local-ui/identity-h2.init.gradle` | 신규 init script(classpath만 변경) |
| `qa/mfa-local-ui-e2e.mjs` | 신규 opt-in 브라우저 E2E 러너 |
| `docs/guides/mfa-webauthn-local.md` | "실제 로그인 연결" 절 추가, "현재 제약"·"화면에서 진행" 갱신 |
| `docs/superpowers/plans/2026-09-14-mfa-backend-transfer.md` | blueprint 조각(rev1→rev2) + 실행 결과 |
| 이 문서 | 신규 |

## 검증 명령과 결과

### RED (구현 전)

- `node --test "qa/mfa-local-ui/*.test.mjs"` → `ERR_MODULE_NOT_FOUND ... mfa-local-core.js`, tests 1 pass 0.
- `./gradlew --offline :backend:services:identity:test --tests com.example.discord.identityservice.MfaLocalUiRuntimeTest` → `pageLoadsOnlySameOriginModulesUnderLoopbackOnlyCspAndUsesNoBrowserStorage FAILED`, 6 tests 1 failed(토큰 경계 테스트 2건은 기존 endpoint만 사용해 이미 통과).

### GREEN / Focused

```bash
node --test "qa/mfa-local-ui/*.test.mjs"                       # tests 8 pass 8 fail 0
./gradlew --offline :backend:services:identity:test --tests com.example.discord.identityservice.MfaLocalUiRuntimeTest
#   TEST-...MfaLocalUiRuntimeTest.xml: tests="6" skipped="0" failures="0" errors="0"
```

### Runtime (opt-in 브라우저 E2E)

```bash
MFA_E2E_PLAYWRIGHT_ROOT=C:/git/discord node qa/mfa-local-ui-e2e.mjs
```

| 회차 | artifact | 결과 |
| --- | --- | --- |
| 1 | `qa/artifacts/mfa-local-ui-e2e/20260919080726/` (gitignored) | `PASS`, boot 50521 / identity 50522, 정리 확인. Node DEP0190 경고(`shell: true`) → 러너 spawn을 `cmd.exe /d /s /c` + 자체 quoting으로 수정 |
| 2 | `qa/artifacts/mfa-local-ui-e2e/20260919081016/` (gitignored) | `PASS`, boot 51898 / identity 51899, 경고 없음, 정리 확인 |

두 회차 공통 `summary.json` 단계 상태: `signup 201, loginWrongPassword 401, login 200, enroll 200, confirmWrongCode 401, confirm 204, totpComplete 200, registerOptionsWithLoginToken 403, registerOptionsWithoutToken 401, registerComplete 204, authenticateComplete 200, identityProfile 200, loginServerProfile 200, profileWithoutToken 401`; `storageEntries 0`; `cookieNames ["JSESSIONID"]`. 러너는 추가로 다음을 단언했다: 로그인 전 단계 버튼 전부 비활성, 비밀번호 입력창 요청 직후 빈 값, 로그인 토큰 `amr` 없음·`sub`=user.id, TOTP secret 32자 Base32·otpauth URI 표시·확인 후 제거, TOTP/패스키 MFA 토큰 `amr` 각 `[totp]`/`[passkey]`·`sub` 동일·`exp-auth_time ≤ 300`, 보호 API 상태 문구에 동일 UUID 3회, 지우기 후 프로필 버튼 비활성.

artifact 로그 점검(1·2회차, Security Reviewer도 2회차 재확인): `boot.log`/`identity.log`에 `eyJ`(JWT), `Bearer`, `password`, `secret`, `encryption-key` 문자열 0건. `summary.json`은 상태 코드·userId·쿠키 이름만 포함.

### Regression

```bash
./gradlew --offline :backend:services:identity:test :backend:modules:identity:test \
  :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest   # BUILD SUCCESSFUL
git diff --check                                                                          # ok (기존 LF/CRLF 경고만)
node --check backend/services/identity/src/main/resources/static/mfa-webauthn.js backend/services/identity/src/main/resources/static/mfa-local-core.js
```

identity service 31 tests(`PostgresMfaPersistenceTest` 2 skipped, 게이트 미설정), identity module 24 tests, 실패 0. 기존 `WebAuthnRuntimeTest`(`/mfa-webauthn.html` 200 포함) 4 tests 통과.

## 리뷰

- Plan Review #1 (rev1 SHA256 `008eac0d7bd9d7c8fa9990cc89459349cdb9a34c4709819e2f3470db7a180da2`, fresh context): 85/100 PASS, P0 0, P1 0, P2 10. 높음 3건(signup 필수 필드, daemon 하 `taskkill /T`, 토큰 보관 모델)과 P2 4~6·10을 rev2(SHA256 `a6bc7b38d58481eb284b966300ebc38f5db8a123ba2aa9dc51988bdec2c62929`)에 반영하고 P2 7~9는 구현·가이드에 반영했다.
- Implementation Review (fresh context, 검수 revision: 페이지 `017809e1…`, JS `09b631b4…`, core `e57b2e07…`, Java test `14d5a67c…`, runner `49d4f576…`): **90/100 PASS**, P0 0, P1 0, P2 7 — core.test 주석 명령, 지우기 후 단언 표현(blueprint 4단계 "401 문구"는 버튼 비활성이라 불가 → page fetch 401로 기록), 비밀번호 비움 순서, GET에 Content-Type 불필요, 미사용 `loginBase` 변수, 가이드 JSON 생략 표기, 로그 점검 2회차 기록. 요청한 10개 검증 항목(endpoint 한정·two-token·omit·storage 부재·status 비노출·정규식 동일·E2E 단계·page Origin·+1 step 유효성·테스트 의미성·컨텍스트 격리·--no-daemon 정리·문서 일치·규칙 위반 없음) 모두 확인.
- Security Review (fresh context, 같은 revision): **90/100 PASS**, P0 0, P1 0, P2 5 — 러너 assertion 실패 메시지의 비밀 echo 가능성, identity 정적 응답의 frame 헤더 부재(main Java non-goal → 가이드 잔여 위험·후속 조각), secret 패널 잔존, 러너 SIGINT 핸들러 부재, Java 부정 테스트 보강. 해석 메모: `MfaLocalUiRuntimeTest.loginServerIssuer()`가 저장소 fixture 키로 테스트 내부 토큰을 서명하는 것은 기존 controller 테스트와 같은 패턴이며 런타임·러너에 노출되지 않아 "임의 토큰 발급 금지"(운영 경로·러너) 위반이 아니라고 판정.
- P2 반영(수정 revision): Impl P2-1/2/3/4/5/6/7, Sec P2-1/3/4/5(b,c) 반영. Sec P2-2는 가이드 잔여 위험으로 기록하고 후속 조각. Sec P2-5(a) "auth_time 300초 경과 MFA 토큰 → 403"은 `issueMfa`가 `exp = auth_time + 300`으로 발급하고 `verify`가 `exp > auth_time+300`을 거부하므로 정상 발급 토큰으로는 만료(401)만 재현 가능해 `WebAuthnControllerTest`의 Clock 기반 검증에 남긴다. 수정 후 재검증 결과는 아래 "수정 revision 재검증" 절.

## 남은 모호성과 잔여 위험

- boot `test` 프로필 의존: 기본 프로필로는 boot가 기동하지 않으므로 로컬 로그인 서버는 test classpath가 필요하다. Wiki `Backend Architecture`의 "default: in-memory services"는 이 점에서 stale이며 수락 후 정정 대상이다.
- 정적 페이지는 `discord.auth.webauthn.enabled`와 무관하게 identity jar에 실려 서빙된다(기존 상태). 로그인 서버 주소 입력은 정규식+CSP로 loopback 한정이지만 공개 배포용 화면이 아니다.
- 브라우저 비밀번호 저장 프롬프트, Network/HAR 내 토큰은 페이지가 막을 수 없다. "메모리-only"는 페이지 스토리지 범위다.
- E2E는 Windows·Chrome·가상 authenticator·H2만 실측했다. 비Windows 정리 경로(`detached` + 프로세스 그룹 kill)는 작성만 했다.
- 등록 중단 후 재시작, TOTP 5회 잠금 복구, 패스키 삭제, key rotation, refresh는 API가 없다.
- 이 worktree에 `node_modules`가 없어 러너는 `MFA_E2E_PLAYWRIGHT_ROOT`로 다른 checkout의 Playwright를 사용했다. 저장소 루트 `npm ci` 후에는 지정 없이 동작해야 하지만 이번에 실측하지 않았다.

## 수정 revision 재검증 (P2 반영 후)

- 수정 revision SHA256(앞 16자): html `25ccf608ca81cd71`, js `615f308cacc9ef3d`, core `e57b2e07cbb791d2`, MfaLocalUiRuntimeTest `74e2590d6d007790`, runner `261d42f0f25936b3`, core.test `7de9d694b3d5b959`.
- `node --test "qa/mfa-local-ui/*.test.mjs"` 8/8. `./gradlew --offline :backend:services:identity:test :backend:modules:identity:test :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest` BUILD SUCCESSFUL, `MfaLocalUiRuntimeTest` 6/6(부정 단언 추가: `credentials: sameOrigin ? "same-origin" : "omit"`·`sameOrigin: false` 경로 문자열, `/api/auth/refresh`·`demoCode` 부재), identity service 31 tests(2 skipped), module 24. `git diff --check` ok.
- E2E 3회차 `qa/artifacts/mfa-local-ui-e2e/20260919082205/`: PASS, boot 60794 / identity 60795, 단계 상태 1·2회차와 동일, storage 0, 쿠키 JSESSIONID만, 지우기 후 비밀번호 빈 값·secret 패널 숨김 추가 단언 통과, 포트 정리 확인. 로그에 JWT·Bearer·password·secret·encryption-key 문자열 0건.
- 반영 내용: 러너 assertion을 길이/boolean 비교로 바꿔 실패 메시지에 비밀번호·secret이 실리지 않게 함, SIGINT/SIGTERM/SIGBREAK 핸들러로 두 JVM 트리 종료, TOTP 추가 인증 성공 시 secret 패널 제거와 5분 무효 안내, 로그인 서버 GET에 Content-Type 미부착, 비밀번호는 검증 통과 후 요청 전에 비움, 미사용 변수 제거, core.test 주석 명령 glob으로 정정, 가이드 identity 기동 JSON 전체 표기와 frame 헤더 부재 잔여 위험 추가.
- 남은 P2(의도적 유예): identity 정적 응답 보안 헤더(main Java 변경, 후속 조각), 비Windows 정리 경로 실측, 이 worktree에서 `npm ci` 후 Playwright 자동 탐색 실측.
- Security Review 델타 재검수(수정 revision js `615f308c…`, html `25ccf608…`, runner `261d42f0…`, Java test `74e2590d…`, core.test `7de9d694…`): **93/100 PASS**, 신규 P0/P1 없음, P2-1/3/4/5 해결 확인, P2-2(후속 조각)·P2-5(a)(Clock 기반 기존 테스트) 처분 수용. 부수 변경(비밀번호 비움 순서, Content-Type 조건부, loginBase 제거)에서 경계 약화 없음 판정.
