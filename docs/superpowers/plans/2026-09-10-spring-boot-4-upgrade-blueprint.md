# Task A: Spring Boot 3.5.16 → 4.1.1 업그레이드

## Status

**완료. 검증 통과.** (2026-09-10)

- 기준 revision: `task_MFA-mfa-spring-security7`
- 검증: `./gradlew test checkstyleMain checkstyleTest` **BUILD SUCCESSFUL**
- 테스트: **454 tests / 0 failures / 59 skipped**
- 베이스라인(Boot 3.5.16) 동일 조건 측정: **454 / 0 / 59** → **완전 일치. 동작 보존 확인**
- 선행 관계: Task C(MFA 이관)의 선행 조건 충족. Task B(멀티클라이언트 auth)와는 독립

## Goal

기능 변경 0으로 백엔드 전체를 Spring Boot 4.1.1 / Spring Security 7.x로 올리고, 기존 테스트를 회귀 게이트로 고정한다.

`examples/mfa-spring-security7`가 Boot 4.1.1 / Security 7.1.1에서 이미 동작하므로 Task C가 요구하는 `AuthorizationManagerFactories.multiFactor()`와 native WebAuthn의 가용성은 입증돼 있었다. 이번 task가 검증한 것은 **백엔드 본체가 업그레이드를 버티는지**이며, 결과는 통과다.

## Non-goals

- Jackson 3(`tools.jackson`) 전면 이관 — 별도 task. 이번에는 Jackson 2 호환 유지
- Gradle 9.x — Boot 4는 8.14+로 충족되므로 Gradle 9의 breaking change를 같이 받지 않음
- 프론트엔드(`apps/*`, `packages/*`) 변경
- JUnit 5 → 6 강제 이행 — 모듈들의 `junit-bom:5.11.4` 핀은 유지했고 문제 없음

## 확정된 요구사항 (실측)

| 항목 | 변경 전 | 변경 후 | 근거 |
|---|---|---|---|
| Java 런타임 | 21 | 21 (변경 없음) | Boot 4 baseline은 **17+**, 상한 26 |
| Gradle | 8.11.1 | **8.14.3** | `SpringBootPlugin.verifyGradleVersion`이 8.14 미만 거부 |
| Spring Boot | 3.5.16 | **4.1.1** | 예제와 정렬 |
| Spring Security | 6.5.10 (명시 핀) | 7.x (BOM 관리) | 핀 제거 |

## 적용한 변경

### 1. Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties`: `gradle-8.11.1-bin.zip` → `gradle-8.14.3-bin.zip`

Boot를 올리기 전에 **Gradle만 올린 상태에서 Boot 3.5.16 빌드가 통과하는지 먼저 확인**했다 (변수 격리). 통과.

### 2. 루트 `build.gradle.kts`

- `org.springframework.boot` 3.5.16 → **4.1.1**
- **수동 버전 핀 4개 전부 제거**: `tomcat.version=10.1.55`, `jackson.version=2.21.4`, `netty.version=4.1.136.Final`, `kafka.version=4.2.0`
  - `tomcat.version`은 Boot 4의 Tomcat 11 / Jakarta EE 11과 직접 충돌하므로 제거가 필수였다
  - 나머지는 Boot 4 관리 버전을 받도록 위임. 제거 후 충돌 없음
- `io.spring.dependency-management` 1.1.7 유지 — Boot 4에서 정상 동작

### 3. Starter rename

`spring-boot-starter-web` → **`spring-boot-starter-webmvc`** (4곳)

- `backend/boot`, `backend/services/community`, `backend/services/identity`, `backend/services/message`

`spring-boot-starter-test` → **`spring-boot-starter-webmvc-test`** (5곳)

`starter-test` 자체는 Boot 4에도 존재하지만, `@AutoConfigureMockMvc`가 `spring-boot-webmvc-test` 모듈로 이동했으므로 이를 전이 포함하는 `starter-webmvc-test`로 교체했다. 이 starter는 `starter-test` + `starter-webmvc` + `spring-boot-webmvc-test` + `resttestclient`를 포함한다.

### 4. `@AutoConfigureMockMvc` 패키지 이동 — 21곳

```
org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc   (Boot 3)
  → org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc    (Boot 4)
```

영향받은 import는 **이 하나뿐**이었다 (boot 17곳 + services 4곳).

### 5. Jackson — 2 유지

Boot 4 기본은 Jackson 3(`tools.jackson`)이라 `com.fasterxml.jackson`이 클래스패스에서 빠지고 **28개 파일이 컴파일 실패**했다. 사용 타입은 `ObjectMapper`, `JsonNode`, `TypeReference`, `JsonProcessingException` 4개뿐이다.

"기능 변경 0" 원칙에 따라 Jackson 2를 유지:

- `org.springframework.boot:spring-boot-jackson2` 추가 (3곳: `boot`, `services/message`, `services/websocket`)

**주의**: 이 모듈은 우리 코드가 직접 쓰는 `ObjectMapper`만 되살린다. Spring MVC의 `@RequestBody` HTTP message converter는 **여전히 Jackson 3**를 쓴다. 아래 6절이 그 결과다.

### 6. `spring.jackson.use-jackson2-defaults: true`

Jackson 3가 **`FAIL_ON_NULL_FOR_PRIMITIVES`를 기본 활성**으로 바꿨다. Jackson 2는 비활성이었다.

증상: `PremiumController.GrantEntitlementRequest`의 `boolean simulateProviderFailure`가 요청 본문에 없으면 400.

```
Cannot map `null` into type `boolean`
(set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)
through reference chain: GrantEntitlementRequest["simulateProviderFailure"]
```

적용 위치 **두 곳 모두 필요**:

- `backend/boot/src/main/resources/application.yml`
- `backend/boot/src/test/resources/application.yml`

**테스트 클래스패스의 `application.yml`이 main 것을 가린다.** main에만 넣으면 테스트에서는 적용되지 않는다. 이 프로젝트 구조에서 반복될 함정이므로 기록해 둔다.

### 7. `spring.autoconfigure.exclude` FQCN 이동 — 가장 위험했던 항목

Boot 4에서 auto-configuration 클래스 패키지가 이동했는데, `spring.autoconfigure.exclude`는 클래스를 **문자열 FQCN으로 참조**한다. 구 이름은 조용히 무시되어 **제외가 무효화**된다. 예외도 경고도 없다.

```
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
  → org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
  → org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
  → org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
```

적용: `backend/boot`, `backend/services/message`, `backend/services/websocket`의 `src/main/resources/application.yml`

증상은 `services/message` 테스트 2건 실패였다. 제외가 풀리면서 actuator health가 `dbHealthContributor` → `dataSource`를 eager 생성하려다 `Failed to determine a suitable driver class`로 컨텍스트 로딩 실패.

`org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration` 항목은 **그대로 남겼다.** Boot 4 클래스패스에 해당 auto-configuration이 없어(별도 `spring-boot-flyway` 모듈, 현재 미포함) 제외가 무의미하며, 남겨둬도 오류를 만들지 않음을 테스트로 확인했다. 향후 flyway starter를 추가하면 이 항목을 새 FQCN으로 정정해야 한다.

### 8. Testcontainers BOM 명시

Boot 4 BOM이 Testcontainers를 관리하지 않아 `Could not find org.testcontainers:postgresql:.` 로 실패.

`backend/boot`에 `platform("org.testcontainers:testcontainers-bom:1.21.3")` 추가. Testcontainers 2.x는 major 변경이라 최신 1.x를 선택했다.

### 9. Spring Security 명시 핀 제거 — 2곳

`spring-security-crypto:6.5.10` → 버전 제거, BOM 관리 위임 (`backend/boot`, `backend/services/identity`). `BCryptPasswordHasher`는 API 변경 없이 동작.

## 검증 결과

| 게이트 | 결과 |
|---|---|
| `./gradlew classes` | BUILD SUCCESSFUL |
| `./gradlew testClasses` | BUILD SUCCESSFUL |
| `./gradlew test` | BUILD SUCCESSFUL — 454 / 0 failures / 59 skipped |
| `./gradlew checkstyleMain checkstyleTest` | BUILD SUCCESSFUL |
| Boot 3.5.16 베이스라인 대조 | 454 / 0 / 59 — **완전 일치** |

베이스라인은 `git stash`로 업그레이드 변경만 되돌리고 Gradle 8.14.3을 상수로 고정한 상태에서 측정했다. 테스트 수·skip 수가 동일하므로 테스트가 조용히 사라지거나 비활성화되지 않았다.

59 skipped는 Docker(Testcontainers) 미실행 환경의 조건부 skip이며 업그레이드 전후 동일하다. **Docker 환경에서의 통합 테스트는 아직 미검증이다.**

## 환경 메모: Gradle 실행 경로

작업 중 Git Bash에서 모든 Gradle 빌드가 실패했다:

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
  at sun.nio.ch.UnixDomainSockets.connect0(Native Method)
  at sun.nio.ch.PipeImpl$Initializer$LoopbackConnector.run(PipeImpl.java:138)
```

JDK NIO가 `Selector.open()`의 내부 파이프를 AF_UNIX 소켓으로 만드는 경로가 실패한다. temurin 17.0.15 / 21.0.7 / 21.0.10 전부 동일했고, `--no-daemon`도 무효였다.

**원인은 머신이나 보안 소프트웨어가 아니라 셸 실행 환경이다. PowerShell에서는 정상 동작한다.** 동일한 6줄 Java 재현 코드가 PowerShell에서는 `SELECTOR OK` / `PIPE OK`를 출력한다.

따라서 이 저장소의 `AGENTS.md`가 규정한 Git Bash 우선 정책에는 예외가 필요하다: **Gradle 실행은 PowerShell(`.\gradlew.bat`)로 한다.** 파일 읽기·검색·편집·git은 Git Bash를 그대로 쓴다.

## 후속 task

1. **Docker 환경에서 통합 테스트 검증** — Testcontainers Postgres, Kafka, Redis 경로. 59 skipped를 실제로 실행
2. **각 서비스 부팅 확인** — `boot`, `services/identity|message|websocket|community` 5개 애플리케이션 실제 기동
3. **Jackson 3 이관** — 28파일 × 4타입. 직렬화 포맷 회귀 테스트 동반 필수 (Kafka payload, Redis 값, WebSocket 프레임)
4. **`use-jackson2-defaults` 의존 제거** — `GrantEntitlementRequest.simulateProviderFailure`를 `Boolean`으로 바꾸거나 기본값을 명시하면 스위치 없이도 동작. 스위치는 광범위한 동작을 되돌리므로 장기적으로는 줄이는 편이 낫다
5. **JUnit 6 이행 검토** — 20개 모듈의 `junit-bom:5.11.4` 핀. 현재 문제 없으나 `archunit-junit5:1.3.0`의 JUnit 6 호환성 확인 필요
6. **Flyway auto-configuration 제외 항목 정정** — flyway starter 도입 시

## 되돌리기

```bash
git checkout build.gradle.kts backend/ gradle/wrapper/gradle-wrapper.properties
```
