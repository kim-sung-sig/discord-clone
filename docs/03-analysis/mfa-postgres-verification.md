# MFA PostgreSQL 실측 검증 (machine handoff)

> 경로 `docs/03-analysis/mfa-postgres-verification.md`는 coordinator task 지시로 고정된 값이다. 이 디렉터리의 `*.analysis.md`/`task_*.md` 명명 관례와 다르며 의도한 예외다.
> 이 문서는 다음 agent가 읽는 handoff 문서다. 토큰·암호·secret·JDBC URL은 기록하지 않는다.

## 결론

- 실제 PostgreSQL에서 identity MFA 조각의 영속화 계약이 성립함을 **실행으로** 확인했다. H2·MockMvc·가상 authenticator 결과의 대체 근거가 아니라 별도 실측이다.
- 실행 DB: `PostgreSQL 16.14 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit` (`postgres:16-alpine`, Testcontainers 1.21.4 일회용 컨테이너).
- 격리 신원: 컨테이너 `809cf7704078`(1회차), `127af4e76251`(2회차), DB `mfa_pg_verify`, 사용자 `mfa_pg_verify`, 라벨 `discord.task=mfa-postgres-verification`, 정리 컨테이너 `testcontainers/ryuk:0.12.0`. 암호는 테스트 JVM이 임의 생성했고 기록하지 않았다.
- 공용 자원 무변경: 실행 전 컨테이너 19개가 실행 후에도 19개로 동일하고, 새로 만든 컨테이너는 30초 내 모두 사라졌다. `postgres-source`/`postgres-replica` 등 기존 컨테이너·volume·이미지·`infra/docker` compose DB는 사용·변경하지 않았다.
- 이 결과는 로컬 단일 JVM·loopback 조건의 검증이며 운영/외부 DB, 실제 기기, 분산 session, 복구 API, refresh, main 병합 승인이 아니다. commit/merge/push/예제 삭제는 하지 않았다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| worktree / branch | `C:/git/discord.worktrees/task_MFA-backend-transfer` / `task_MFA-backend-transfer` (HEAD `19ee3e4`, 기존 dirty 이관 변경 보존) |
| Docker | Docker Desktop 4.74.0, Engine 29.4.3 (linux/amd64). 작업 시작 시 daemon이 꺼져 있어 coordinator 승인 후 `Docker Desktop.exe`를 hidden window로 정상 시작했다. 재설치·리셋·volume 삭제 없음. 시작과 함께 기존 restart-policy 컨테이너(`postgres-source`, `postgres-replica`, `ms-redis`, `ms-kafka`, `ms-redis-ui`)가 자동으로 올라왔고 이들은 건드리지 않았다 |
| 로컬 PostgreSQL | 네이티브 설치·`psql` 없음(`/c/Program Files/PostgreSQL` 부재, 5432 리스너 없음) → 저장소 관례인 Testcontainers를 사용 |
| Gradle / JDK | Gradle 8.14.3 wrapper, Temurin 21.0.7 |
| Testcontainers | identity service에 BOM 1.21.4 + `postgresql` + `junit-jupiter`, core는 `dependencyManagement` 직접 선언으로 1.21.4 고정. `--offline` 해석 결과 `org.testcontainers:*` 전부 1.21.4, `docker-java 3.4.2`(Boot 4.1.1 BOM의 2.0.5 승격 없음) |

## 변경 파일 (task 소유)

| 경로 | 변경 |
| --- | --- |
| `backend/services/identity/build.gradle.kts` | `dependencyManagement { dependencies { dependency("org.testcontainers:testcontainers:1.21.4") } }` + test 의존성 3줄 추가. 기존 dirty 변경(webauthn/jdbc/postgresql/h2)은 그대로 |
| `backend/services/identity/src/test/java/com/example/discord/identityservice/PostgresMfaPersistenceTest.java` | 신규 opt-in 테스트(`@EnabledIfEnvironmentVariable(DISCORD_RUN_POSTGRES_TESTS=true)`, `@Testcontainers`) |
| `backend/services/identity/src/test/java/com/example/discord/identityservice/PasskeyTestDevice.java` | 신규 P-256 테스트 authenticator(assertion마다 sign counter 증가). 보안 검수 hash가 기록된 `WebAuthnControllerTest`는 수정하지 않음 |
| `qa/mfa-postgres-verify.sh` | 신규 opt-in 실행 스크립트(Docker 확인 → 컨테이너 ID 스냅샷 → focused test → 30초 대기 후 스냅샷 비교) |
| `docs/superpowers/plans/2026-09-14-mfa-backend-transfer.md` | blueprint "2026-09-19 PostgreSQL 실측 검증 조각"(rev1→rev4, Plan Review 3회) + 결과 |
| `docs/guides/mfa-webauthn-local.md` | "PostgreSQL 실측 검증" 절 추가 |
| 이 문서 | 신규 |

운영 코드(`JdbcTotpStore`, `MfaController`, `WebAuthnController`, `WebAuthnConfiguration`, schema)는 수정하지 않았다. 실측에서 runtime 결함이 드러나지 않아 "최소 runtime 수정"은 발생하지 않았다.

## 검증 명령과 결과

### RED

의존성 추가 전 `./gradlew --offline -q :backend:services:identity:compileTestJava` → `package org.testcontainers.containers does not exist` 등 컴파일 실패(exit 1). 의존성 추가 후 컴파일 성공.

### Focused (PostgreSQL, 실제 실행)

```bash
bash qa/mfa-postgres-verify.sh
# 내부적으로: DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:services:identity:cleanTest \
#   :backend:services:identity:test --tests com.example.discord.identityservice.PostgresMfaPersistenceTest
```

| 회차 | 결과 | 컨테이너 | 시간 |
| --- | --- | --- | ---: |
| 1 | `tests="2" skipped="0" failures="0" errors="0"` | `809cf7704078` | 6.2s (Gradle 17s, 스크립트 전체 31s) |
| 2 | `tests="2" skipped="0" failures="0" errors="0"` | `127af4e76251` | 6.1s (Gradle 15s) |

두 회차 모두 `[pg-evidence]` 라인(JUnit XML `system-out`)으로 다음을 남겼다.

| 항목 | 1회차 관찰값 |
| --- | --- |
| schema | `mfa-schema.sql` 2회 적용 후 `tables=[mfa_totp, user_credentials, user_entities]` (재적용 무손실) |
| T1 동시 confirm (2 adapter × 8 thread) | `[false, false, false, false, false, false, false, true]` → 정확히 1 성공 |
| T1 동시 challenge (8 thread) | `issued=1 of 8` (조건부 UPDATE 30초 간격) |
| T1 동시 complete (8 thread) | `[false, false, false, false, false, false, true, false]` → 1 성공, 직후 행 `failures=0`, `challenge_id IS NULL` |
| T1 fail-closed 후 행 | `active=true failures=5 consumed_step=37037036` (RFC 6238 T=1111111109 step) |
| T2 run #1 | TOTP enroll 200 → 중복 enroll 409(SQLState 23505) → confirm 204 → challenge 200 → complete 200 (`amr=[totp]`) → passkey register options 200(`userVerification=required`) → register complete 204 → `user_credentials=1` |
| T2 종료 | `close()` 후 첫 서버로의 요청은 `IOException`(연결 거부) |
| T2 run #2 (같은 DB, 새 Tomcat) | TOTP challenge 200 → complete 200(같은 key로 secret 복호화) → 같은 challenge replay **401** → passkey authenticate options 200(`allowCredentials` 1개) → complete 200 `amr=[passkey]` → 같은 payload replay **401** |
| T2 동시 authenticate complete (4 thread, 같은 JSESSIONID) | `[200, 401, 401, 401]` (2회차 `[401, 200, 401, 401]`) |
| T2 영속 행 | `signature_count=2 last_used_set=true totp.active=true totp.failures=0`, `challenge_id IS NULL` |

### Regression (게이트 미설정)

```bash
./gradlew --offline :backend:services:identity:cleanTest :backend:modules:identity:cleanTest \
  :backend:services:identity:test :backend:modules:identity:test \
  :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest
```

BUILD SUCCESSFUL. identity service 8 클래스 25 tests(`PostgresMfaPersistenceTest tests=2 skipped=2` — Docker 불필요 확인, 기존 `WebAuthnControllerTest` 9·`WebAuthnRuntimeTest` 4·`IdentityServiceApplicationTest` 3 등 23개 통과), identity module 8 클래스 24 tests 통과, Checkstyle 통과. `git diff --check` 통과. 새 테스트 파일에 `@Configuration`/`@Component`/`@TestConfiguration`/`@SpringBootTest` 없음(`rg` 확인) → 기존 `@SpringBootTest` 컨텍스트에 테스트 Clock이 주입되지 않는다.

`./gradlew --offline test`(전체)는 BUILD SUCCESSFUL이지만 모든 `:test` 태스크가 UP-TO-DATE/NO-SOURCE로 재실행되지 않았다. 이 task의 변경은 identity service test 소스와 그 build 파일에만 있으므로 위 focused 게이트가 실질 회귀 근거다.

### Cleanup

스크립트 출력 `pre-existing containers=19` → `cleanup ok (no pre-existing container lost, no task-owned container left)` (2회 모두). 실행 중 관찰: `postgres:16-alpine <임의 이름> mfa-postgres-verification`, `testcontainers/ryuk:0.12.0 testcontainers-ryuk-<sessionId>`.

## 설계 사실 (durable)

- Boot 4.1.1 BOM은 `testcontainers.version=2.0.5`로 `org.testcontainers:testcontainers` core만 승격한다. boot 모듈은 1.21.3 모듈 + 2.0.5 core 혼합 그래프다(이 task 범위 밖, 미수정). identity service는 `ext["testcontainers.version"]` 재정의가 효과가 없어 `dependencyManagement` 직접 선언으로 core를 1.21.4에 고정했다.
- Spring Security 7.1.1 `Webauthn4JRelyingPartyOperations.authenticate`는 저장된 `signature_count`가 아니라 등록 attestation authData의 signCount를 기준 counter로 삼는다. 따라서 이 테스트의 `signature_count`/`last_used` 단언은 **쓰기 영속성**만 뜻하며 "재시작 후 클론 authenticator 탐지"가 아니다. 테스트 기기는 assertion마다 counter를 1씩 올린다.
- `WebAuthnConfiguration`은 origin의 형식(`http://localhost:<port>`, `server.address=127.0.0.1`)만 검사하고 실제 서버 포트와 비교하지 않는다. 그래서 `server.port=0` + 고정 origin `http://localhost:8080`으로 bind 경합 없이 두 번 기동할 수 있다. `WebAuthnController`는 `remoteAddr == 127.0.0.1`을 요구하므로 클라이언트는 `127.0.0.1`로 접속해야 한다.
- 테스트 Clock은 `SpringApplicationBuilder.initializers`의 `ApplicationContextInitializer`가 `registerBean(..., primary=true)`로 넣는다. 스테레오타입 어노테이션이 없어 같은 패키지를 스캔하는 기존 `@SpringBootTest`에는 영향이 없다.
- 시계 진행표(T0 confirm → +30 complete/register → +60 재기동 challenge → +90 complete/authenticate → +101 동시 authenticate)는 `consumed_step`, challenge 30초 간격·180초 만료, WebAuthn options 10초 스로틀, freshMfa 300초 창을 모두 만족하도록 고정했다.

## 검수

- Plan Review(fresh context, preset Plan Review, 기준 85): rev1 82/100 FAIL(P1 2) → rev2 83/100 FAIL(P1 1: Testcontainers 혼합 그래프) → rev3 88/100 **PASS**(P0/P1 0, P2 4 → rev4 반영). rev3 SHA256 `de30b293d51ac7213355965f8b179b1b6c14769891a7e89c179bc761322d19cf`.
- Implementation Review / Security Review: 아래 "구현 검수 결과" 절에 기록.

## 구현 검수 결과

fresh context 독립 검수(Diff-Only Review Packet: blueprint, 변경 4개 파일 SHA256, 검증 증거). 검수 대상 SHA256: `build.gradle.kts` `b0d29c7a…dc40fc`, `PostgresMfaPersistenceTest.java` `26890291…927d0`, `PasskeyTestDevice.java` `d73ef189…b57d3`, `qa/mfa-postgres-verify.sh` `ffc33ca9…4fce`.

| 구분 | Preset | 점수 | P0/P1/P2 | 판정 |
| --- | --- | ---: | --- | --- |
| Implementation | Implementation Review (Blueprint 19/20, Correctness 18/20, Security 14/15, Tests 19/20, Maintainability 12/15, Ops/docs 9/10) | 91/100 | 0/0/3 | PASS (기준 80) |
| Security | Security Review (Threat 18/20, Access 18/20, Exposure 13/15, Regression 19/20, Operational 14/15, Dependency 9/10) | 91/100 | 0/0/3 | PASS (기준 90) |

검수자가 코드 판독으로 확인한 사실: 비공허성(정확히 1 승자 단언, 전부 401이면 실패), 두 번 `boot()` + `close()` + 종료 후 `IOException` 단언으로 재기동 강제, 시계 진행표 일치, Clock initializer의 기존 테스트 비유출, 클래스 레벨 게이트가 Docker 접근 전에 평가됨, `dependencyManagement` 직접 선언이 imported BOM보다 우선, `[pg-evidence]`에 비밀값 없음.

P2와 처리:

1. `second.verify(owner, "081804", …)` step 재사용 거부가 `failures`를 1로 올려 이후 5회 루프가 실제로는 4회만 카운트된다는 지적 → `failures == 1` 단언과 주석을 추가하고 PostgreSQL 3회차 재실행으로 확인(`tests=2 skipped=0 failures=0`, 컨테이너 `93714b61a7f2`). 수정 후 테스트 파일 SHA256 `3640b4401e12f341927cf2f15aa8498be4f039b739df47b624ac7c654dd7a2d4`.
2. 컨테이너 기동 로그의 비밀값 노출 미검증 → 보존한 1·2회차 JUnit XML 사본을 `jdbc:postgresql|Bearer |password|encryption|secret|eyJ`로 grep. 일치는 테스트 메서드 이름(`…TotpSecret…`)과 Testcontainers INFO 로그의 자격증명 없는 JDBC URL(`jdbc:postgresql://localhost:<임의포트>/mfa_pg_verify?loggerLevel=OFF`) 두 건뿐. 암호·Bearer·secret·encryption key 노출 없음. 이 XML은 `build/` 산출물로 Git 추적 대상이 아니다.
3. 스크립트 종료 코드: 잔존 컨테이너 시 exit 3이 Gradle 실패 코드를 덮어쓰고(Gradle 코드는 직전 줄에 echo됨), 실행 중 다른 세션이 새 컨테이너를 만들면 오탐 FAIL이 날 수 있다는 지적. blueprint에 명시한 trade-off(스냅샷 기준, 라벨 비의존)라 유예. 필요 시 `org.testcontainers.sessionId` 라벨 교집합으로 좁힐 수 있다.
4. `PasskeyTestDevice`가 `WebAuthnControllerTest.Authenticator`와 중복(차이: UV 고정·sign counter 증가). 보안 검수 hash 보존 사유로 이번 사이클 유예. 다음 검수 사이클에서 inner 클래스를 `PasskeyTestDevice(uv, counter)`로 대체 권장.
5. 스타일(`assertEquals(null, …)` → `assertNull`, `code()`의 inline FQN → import) → 반영. 4회차 PostgreSQL 재실행 `tests=2 skipped=0 failures=0`(컨테이너 `7067343c0830`, 동시 authenticate `[200,401,401,401]`), `checkstyleTest`·`git diff --check` 통과. 최종 테스트 파일 SHA256 `9213d8057a739cab928ecb358a9b32394cc49ba4a1c463f1ffa089d629a4e24c`.

검수자 Acceptance: Implementation **PASS**(91/100, P0/P1 없음, 모든 항목 절반 이상; adapter 재생성·두 번의 `run()`+`close()`·일회 소비·replay 401·단일 승자를 공허하게 통과할 수 없는 단언 구조), Security **PASS**(91/100, 미해결 고위험 없음; 비밀값·토큰은 stdout/보고서에 없고 게이트·Clock 초기화기는 기본 빌드 및 기존 `@SpringBootTest`와 격리). P2-1/2/5 반영 후 변경은 단언 추가·grep 확인·스타일뿐이며 재실행 결과가 동일하므로 별도 재검수는 요청하지 않았다(P2 처리 기록으로 남김).

## 남은 항목 / 다음 agent를 위한 메모

- 이 검증은 identity MFA 조각의 PostgreSQL 영속화까지다. 전체 이관 독립 리뷰, 제품 client 계약, 실제 기기 UV, 복구/삭제 API, refresh, 예제 정리, main 병합은 여전히 미완료다.
- boot 모듈의 Testcontainers 혼합 그래프(1.21.3 모듈 + 2.0.5 core)는 별도 task로 정리할 후보다. 그 상태에서 boot의 opt-in PostgreSQL 테스트가 실제로 동작하는지는 이 task에서 확인하지 않았다.
- 재현: Docker Desktop 실행 → `bash qa/mfa-postgres-verify.sh`. Docker daemon이 없으면 exit 2로 즉시 중단되며 테스트를 skip으로 위장하지 않는다.
- 참고한 Wiki: `index.md`, `LLM Wiki Development Loop`, `Agent Development Guide`, `Backend Architecture`(identity/MFA·postgres 절), `QA Infra Operations`(Testcontainers/Docker 절).
