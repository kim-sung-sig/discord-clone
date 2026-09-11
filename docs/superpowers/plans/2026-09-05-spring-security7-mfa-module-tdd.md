# Spring Security 7 MFA Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Discord의 `user`·`identity` 모듈을 실제로 소비하며 Spring Security 7 MFA, JWT challenge, refresh 경계를 TDD로 검증하는 독립 예제를 만든다.

**Architecture:** 예제 build는 기존 `backend/modules/user`, `backend/modules/identity`를 Gradle subproject로 포함한다. MFA application은 사용자 profile과 refresh session 도메인을 직접 재사용하고, SQLite에는 account/factor transaction/challenge의 adapter 상태만 저장한다. access JWT는 base/elevated assurance를 명시하고 resource server converter가 `amr`을 Spring Security 7 factor authority로 변환한다.

**Tech Stack:** Java 21, Gradle, Spring Boot 4.1.1, Spring Security 7.1, SQLite JDBC, JUnit 5, MockMvc, Discord `user`/`identity` modules.

---

## 고정 규칙

- 모든 production 변경 전에 해당 실패를 표현하는 JUnit test를 작성하고 focused Gradle test가 **기대 원인으로 실패**한 것을 확인한다.
- `sub`는 반드시 `UserProfile.id()` UUID다. email, request body, path의 user ID로 caller를 정하지 않는다.
- refresh token raw value는 DB/log/response body에 저장하지 않는다. hash만 보관하고 refresh는 rotation 직후 이전 token을 revoke한다.
- step-up은 refresh session assurance를 변경하지 않는다. elevated access JWT만 5분이다.
- 기존 root project나 Discord production source는 이 단계에서 수정하지 않는다. 이 예제는 이관 계약을 검증한다.
- 사용자가 commit보다 MVP 확인을 우선하도록 지시했으므로 이 계획에서는 commit step을 수행하지 않는다.

## 파일 구조

| Path | 책임 |
|---|---|
| `examples/mfa-spring-security7/settings.gradle.kts` | `:discord-user`, `:discord-identity` 외부 project 포함 |
| `examples/mfa-spring-security7/build.gradle.kts` | Spring/SQLite와 Discord module dependency |
| `examples/mfa-spring-security7/src/main/java/com/example/mfa/domain/*` | factor, assurance, transaction, challenge 불변식 |
| `examples/mfa-spring-security7/src/main/java/com/example/mfa/application/*` | 로그인·step-up·refresh use case |
| `examples/mfa-spring-security7/src/main/java/com/example/mfa/adapter/*` | SQLite account/transaction repository와 mock factor |
| `examples/mfa-spring-security7/src/main/java/com/example/mfa/security/*` | access/temp JWT, `amr` authority converter, filter chain |
| `examples/mfa-spring-security7/src/main/java/com/example/mfa/web/*` | 검증된 HTTP DTO와 controllers |
| `examples/mfa-spring-security7/src/test/java/com/example/mfa/**` | domain 단위와 HTTP 경계 회귀 테스트 |

### Task 1: 실제 Discord 사용자/identity 모듈 연결

**Files:**
- Modify: `examples/mfa-spring-security7/settings.gradle.kts`
- Modify: `examples/mfa-spring-security7/build.gradle.kts`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/UserModuleContractTest.java`

- [ ] **Step 1: 외부 모듈 사용 실패 테스트 작성**

```java
class UserModuleContractTest {
    @Test
    void createsTheDiscordUserProfileUsedByMfa() {
        UserProfile user = UserProfile.create(UUID.randomUUID(), Username.from("mfa_demo"), "MFA Demo", Instant.EPOCH);
        assertThat(user.id()).isNotNull();
        assertThat(user.username().value()).isEqualTo("mfa_demo");
    }
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.UserModuleContractTest`

Expected: `package com.example.discord.user does not exist`.

- [ ] **Step 3: 외부 project와 dependency 추가**

```kotlin
// settings.gradle.kts
include(":discord-user", ":discord-identity")
project(":discord-user").projectDir = file("../../backend/modules/user")
project(":discord-identity").projectDir = file("../../backend/modules/identity")
```

```kotlin
implementation(project(":discord-user"))
implementation(project(":discord-identity"))
```

- [ ] **Step 4: GREEN 확인**

Run: `gradle test --tests com.example.mfa.UserModuleContractTest`

Expected: PASS.

### Task 2: Login transaction 도메인과 access/refresh 차단

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/domain/FactorId.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/domain/AssuranceLevel.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/domain/LoginTransaction.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/domain/LoginTransactionTest.java`

- [ ] **Step 1: password만 완료하면 발급 불가인 RED test 작성**

```java
@Test
void cannotCompleteUntilEveryRequiredFactorIsCompleted() {
    LoginTransaction transaction = LoginTransaction.start(
        UUID.randomUUID(), List.of(FactorId.PASSWORD, FactorId.OTT), Instant.parse("2026-09-05T00:05:00Z"));

    transaction.complete(FactorId.PASSWORD);

    assertThat(transaction.readyForTokenIssue()).isFalse();
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.domain.LoginTransactionTest`

Expected: compile failure because `LoginTransaction` does not exist.

- [ ] **Step 3: 최소 불변식 구현**

```java
public boolean readyForTokenIssue() {
    return completedFactors.containsAll(requiredFactors) && Instant.now(clock).isBefore(expiresAt);
}
```

`complete`는 required factor만 받고, 완료 transaction·만료 transaction은 `IllegalStateException`으로 거부한다. `AssuranceLevel`은 완료 factor의 configured level 최대값만 계산한다.

- [ ] **Step 4: GREEN 및 재사용 회귀 추가**

Run: `gradle test --tests com.example.mfa.domain.LoginTransactionTest`

Expected: PASS. 이어서 완료 transaction 재완료와 만료 transaction의 실패 test를 추가한다.

### Task 3: UserProfile 기반 SQLite account adapter

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/adapter/MfaUserAccount.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/adapter/SqliteMfaAccountRepository.java`
- Modify: `examples/mfa-spring-security7/src/main/resources/schema.sql`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/adapter/SqliteMfaAccountRepositoryTest.java`

- [ ] **Step 1: UUID UserProfile을 저장/조회하는 RED test 작성**

```java
@Test
void findsAnAccountByNormalizedEmailAndPreservesDiscordProfileId() {
    UserProfile profile = UserProfile.create(userId, Username.from("mfa_demo"), "MFA Demo", now);
    repository.save(new MfaUserAccount(EmailAddress.from("Demo@Example.com"), "bcrypt-hash", profile));

    assertThat(repository.findByEmail(EmailAddress.from("demo@example.com")).orElseThrow().profile().id())
        .isEqualTo(userId);
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.adapter.SqliteMfaAccountRepositoryTest`

Expected: missing repository/schema mapping failure.

- [ ] **Step 3: schema와 query-shaped adapter 구현**

`mfa_accounts`에는 `user_id`, normalized `email`, `password_hash`, `username`, `display_name`, `created_at`만 저장한다. `email`에 UNIQUE constraint를 둔다. password hash와 JWT는 select/exception/log message에 포함하지 않는다.

- [ ] **Step 4: GREEN 확인**

Run: `gradle test --tests com.example.mfa.adapter.SqliteMfaAccountRepositoryTest`

Expected: PASS.

### Task 4: auth-factor/auth-challenge JWT binding

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/security/TemporaryTokenService.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/domain/FactorChallenge.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/security/TemporaryTokenServiceTest.java`

- [ ] **Step 1: 다른 challenge에 token을 쓰는 RED test 작성**

```java
@Test
void rejectsAChallengeTokenBoundToAnotherChallenge() {
    String token = tokens.issueChallenge(userId, transactionId, firstChallengeId, FactorId.OTT, expiresAt);

    assertThatThrownBy(() -> tokens.requireChallenge(token, userId, transactionId, secondChallengeId, FactorId.OTT))
        .isInstanceOf(InvalidTemporaryTokenException.class);
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.security.TemporaryTokenServiceTest`

Expected: missing token service failure.

- [ ] **Step 3: type/subject/transaction/challenge/factor/expiry 검증 구현**

`auth-factor`는 `transaction_id`, `purpose`를, `auth-challenge`는 추가로 `challenge_id`, `factor_id`를 claim으로 갖는다. 검증은 정확한 type과 모든 binding claim이 일치할 때만 성공한다.

- [ ] **Step 4: GREEN 확인 및 replay test 추가**

Run: `gradle test --tests com.example.mfa.security.TemporaryTokenServiceTest`

Expected: PASS. repository conditional update가 승인된 challenge를 두 번째로 승인하지 못하는 test를 추가한다.

### Task 5: access JWT, refresh boundary, assurance freshness

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/security/MfaAccessTokenService.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/application/RefreshBoundary.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/security/MfaAccessTokenServiceTest.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/application/RefreshBoundaryTest.java`

- [ ] **Step 1: step-up refresh가 level 80을 연장하지 않는 RED test 작성**

```java
@Test
void refreshIssuesBaseAssuranceInsteadOfElevatedAssurance() {
    RefreshSession session = RefreshSession.create(sessionId, userId, "hash", "Chrome", now, now.plus(Duration.ofDays(7)));

    MfaAccessToken refreshed = refreshBoundary.refresh(session, baseLoginEvidence);

    assertThat(refreshed.assuranceLevel().value()).isEqualTo(50);
    assertThat(refreshed.amr()).containsExactly("password", "ott");
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.application.RefreshBoundaryTest`

Expected: missing refresh boundary failure.

- [ ] **Step 3: 최소 access claim 계약 구현**

`MfaAccessTokenService`는 `sub`, `sid`, `amr`, `assurance_level`, `auth_time`, `typ=access`를 발급한다. elevated access는 5분 TTL, base access는 설정 TTL을 사용한다. `RefreshBoundary`는 Discord `RefreshSession` rotation port 결과로 base evidence만 받아 access를 발급한다.

- [ ] **Step 4: GREEN 확인 및 refresh reuse contract test 추가**

Run: `gradle test --tests com.example.mfa.security.MfaAccessTokenServiceTest --tests com.example.mfa.application.RefreshBoundaryTest`

Expected: PASS. revoked refresh의 reuse가 `revokeAllForUser(userId)` port를 요청하는 test를 추가한다.

### Task 6: Spring Security 7 `amr` authority converter와 인가

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/security/AmrFactorGrantedAuthoritiesConverter.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/security/MfaSecurityConfiguration.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/security/AmrFactorGrantedAuthoritiesConverterTest.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/web/FinanceAuthorizationTest.java`

- [ ] **Step 1: `amr=[password,ott]` authority mapping RED test 작성**

```java
@Test
void mapsPasswordAndOttAmrToSpringSecurityFactorAuthorities() {
    Collection<GrantedAuthority> authorities = converter.convert(jwtWithAmr("password", "ott"));

    assertThat(authorities).extracting(GrantedAuthority::getAuthority)
        .contains(FactorGrantedAuthority.PASSWORD_AUTHORITY, FactorGrantedAuthority.OTT_AUTHORITY);
}
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.security.AmrFactorGrantedAuthoritiesConverterTest`

Expected: missing converter failure.

- [ ] **Step 3: converter와 resource server wiring 구현**

`amr`의 allowlist(`password`, `ott`, `passkey`, `webauthn`)만 `FactorGrantedAuthority`로 변환한다. unknown amr는 authority가 되지 않는다. `JwtAuthenticationConverter`에 converter를 연결하고 finance는 level 80과 required factor authority를 모두 요구한다.

- [ ] **Step 4: GREEN 확인**

Run: `gradle test --tests com.example.mfa.security.AmrFactorGrantedAuthoritiesConverterTest --tests com.example.mfa.web.FinanceAuthorizationTest`

Expected: PASS. level 50은 `403 MFA_REQUIRED`, level 80 access만 finance summary를 받는다.

### Task 7: HTTP flow와 refresh cookie 경계

**Files:**
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/web/MfaLoginController.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/web/StepUpController.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/web/MockDeviceController.java`
- Create: `examples/mfa-spring-security7/src/main/java/com/example/mfa/web/RefreshController.java`
- Create: `examples/mfa-spring-security7/src/test/java/com/example/mfa/web/MfaFlowIntegrationTest.java`

- [ ] **Step 1: password-only login response가 access/refresh를 갖지 않는 RED test 작성**

```java
mockMvc.perform(post("/api/login").contentType(APPLICATION_JSON)
        .content("{\"email\":\"demo@example.com\",\"password\":\"password\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.authFactorToken").isNotEmpty())
    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    .andExpect(jsonPath("$.accessToken").doesNotExist());
```

- [ ] **Step 2: RED 확인**

Run: `gradle test --tests com.example.mfa.web.MfaFlowIntegrationTest`

Expected: failure because previous prototype issues tokens/uses incompatible response contract.

- [ ] **Step 3: controller를 application service에만 연결**

login 완료 후 response body에는 access JWT, response cookie에는 `HttpOnly`, `SameSite=Lax`, `/api/auth` path의 refresh만 둔다. challenge와 factor temporary JWT는 전용 header에서만 받는다. mock device endpoint는 demo profile에서만 활성화한다.

- [ ] **Step 4: 전체 flow GREEN 확인**

Run: `gradle test --tests com.example.mfa.web.MfaFlowIntegrationTest`

Expected: password-only 차단, OTT approve, level 50 access/refresh, finance 403, step-up level 80, refresh level 50, expired/replayed temporary token 거부가 모두 PASS.

### Task 8: 문서와 full verification

**Files:**
- Modify: `examples/mfa-spring-security7/README.md`
- Modify: `docs/superpowers/specs/2026-09-04-mfa-module-tdd-design.md`

- [ ] **Step 1: README에 모듈 이식과 실행 flow 반영**

README는 `:discord-user`, `:discord-identity`가 실제 source dependency임을 명시하고, demo-only SQLite/HMAC/mock device가 이관 대상이 아님을 설명한다. access claims와 refresh/elevated access lifetime을 표로 고정한다.

- [ ] **Step 2: full test 실행**

Run: `gradle test`

Expected: PASS, 8개 회귀 항목이 모두 테스트 이름과 assertion으로 추적 가능.

- [ ] **Step 3: README smoke flow 실행**

Run: `gradle bootRun`

Expected: mock login → OTT approval → base access → finance denial → step-up → elevated access → refresh base access 흐름이 성공한다.

## Plan self-review

- Spec coverage: user module 직접 의존(Task 1, 3), 분리된 MFA 경계(Task 2–7), OTT/factor authority(Task 6), challenge JWT(Task 4), refresh rotation/freshness(Task 5, 7), TDD 회귀(Task 2–7)를 모두 포함한다.
- Placeholder scan: 비어 있는 구현 항목이 없고, 각 task의 명령과 expected result를 명시했다.
- Type consistency: 모든 JWT subject는 `UUID userId`; base assurance는 50, finance elevation은 80; `RefreshSession`은 Discord identity module의 type을 사용한다.
