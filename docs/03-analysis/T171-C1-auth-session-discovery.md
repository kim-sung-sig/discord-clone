---
id: T171-C1-auth-session-discovery
title: T171-C1 인증·세션·RBAC 실측 분석
status: discovery-complete
date: 2026-08-09
scope: backend authentication, session lifecycle, JWT verification, RBAC ownership
---

# T171-C1 인증·세션·RBAC 실측 분석

## 1. 결론

현재 저장소에는 **Spring Session Redis가 적용되어 있지 않다**. 로그인 세션은 `legacy-auth` 프로필의 boot 애플리케이션에서 PostgreSQL(`auth_refresh_sessions`)이 관리하고, access token은 Ed25519 JWT로 발급된다. `identity`, `community`, `message`, `websocket` 독립 서비스는 Spring Security filter chain이나 SessionRepository를 사용하지 않고 각 서비스가 동일한 공개키 설정으로 JWT를 직접 검증한다.

따라서 다음을 현재 사실로 확정할 수 있다.

1. Redis 세션을 전제로 한 `spring:session:*` 키나 `userId:guildId:channelId` 키는 현재 존재하지 않는다.
2. access token과 refresh session은 서로 다른 수명·저장소·폐기 경로를 가진다.
3. 현재 JWT에는 `sub`, `iss`, `aud`, `iat`, `exp`, `kid`만 들어가며 `sid`와 `authzVersion`은 없다.
4. 서비스별 자체 인가는 JWT에서 `userId`를 얻는 단계까지만 구현되어 있다. 각 서비스가 독립적으로 RBAC를 판단할 수 있는 권한 projection/버전 계약은 아직 없다.
5. Redis는 rate limit, ephemeral presence/typing, Gateway session registry/event bus의 구현에 사용되지만 인증 세션 저장소로 사용되지 않는다.

T171-C1의 권장 방향은 **Identity를 인증·refresh session의 원본으로 유지하고, JWT는 짧은 수명의 신원 증명으로 사용하며, 각 서비스가 자체 RBAC projection을 읽어 인가하는 혼합형**이다. Community(길드·역할 원본)는 권한 변경 이벤트를 발행하고, 각 소비 서비스는 자기 저장소 또는 명시된 Redis projection을 갱신한다. 요청마다 Community를 동기 호출하지 않는다. Spring Session Redis 도입은 이 문제의 필수 조건이 아니므로 별도 요구가 없는 한 도입하지 않는다.

이 문서는 구현 결정을 확정하는 설계서가 아니라, 코드로 검증한 현재 상태와 다음 설계에서 닫아야 할 결정 목록이다.

## 2. 조사 기준과 읽은 파일

조사 기준일: 2026-08-09. 소스 파일의 `build/`와 `bin/` 생성물은 제외했다.

| 영역 | 확인 경로 |
|---|---|
| 멀티모듈 구조 | `settings.gradle.kts:1-51` |
| boot 의존성 | `backend/boot/build.gradle.kts:1-35` |
| 독립 서비스 의존성 | `backend/services/identity/build.gradle.kts:1-13`, `backend/services/community/build.gradle.kts:1-13`, `backend/services/message/build.gradle.kts:1-13`, `backend/services/websocket/build.gradle.kts:1-13` |
| JWT 발급·검증 | `backend/modules/identity/build.gradle.kts:1-12`, `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:16-48`, `BearerTokenVerifier.java:8-24` |
| boot 인증 구성 | `backend/boot/src/main/java/com/example/discord/auth/AuthConfiguration.java:20-75`, `AuthenticatedUserResolver.java:10-41` |
| legacy auth lifecycle | `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:28-259`, `AuthController.java:32-135` |
| 저장소 계약·JDBC 구현 | `backend/boot/src/main/java/com/example/discord/auth/AuthStore.java:11-46`, `JdbcAuthStore.java:1-233` |
| 인증 DB migration | `backend/boot/src/main/resources/db/migration/V1__baseline_core_schema.sql:1-22`, `V2__auth_revoked_access_tokens.sql:1-4`, `V6__auth_refresh_sessions.sql:1-12`, `V7__user_global_roles.sql:1-8`, `V8__user_global_role_audit_log.sql:1-11` |
| Redis 설정·사용처 | `backend/boot/src/main/resources/application-redis.yml:1-16`, `RedisRateLimitStore.java:10-46`, `RedisPresenceTtlStore.java:14-126`, `RedisGatewaySessionRegistry.java:21-143`, `RedisGatewayEventBus.java:33-180` |
| 독립 서비스 JWT 구성 | `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:20-57`, `ProfileController.java:13-34`, `backend/services/community/src/main/java/com/example/discord/communityservice/CommunityServiceApplication.java:17-31`, 동일 패턴의 message/websocket `*ServiceApplication.java` |
| 현재 권한 계산 | `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java:3-22`, `PermissionSet.java:3-26`, `EffectivePermissionCalculator.java:7-57`, `backend/boot/src/main/resources/db/migration/V1__baseline_core_schema.sql:32-75`, `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java:134-159` |

## 3. 검증된 현재 사실

### 3.1 모듈과 의존성

루트 `settings.gradle.kts:8-31`은 `boot`, `identity`, `message`, `websocket`, `community` 서비스를 별도 Gradle 프로젝트로 포함한다. `boot`는 Redis starter와 WebSocket, Kafka, JDBC/Flyway 등을 함께 가진다(`backend/boot/build.gradle.kts:18-35`). 반면 독립 서비스 build 파일에는 다음만 있다.

- identity: `identity` module, actuator, web, test (`backend/services/identity/build.gradle.kts:7-12`)
- community: `identity` module, channel/event/guild/invite/message modules, actuator, web, test (`backend/services/community/build.gradle.kts:7-12`)
- message: `identity` module, thread module, actuator, web, test (`backend/services/message/build.gradle.kts:7-12`)
- websocket: `identity`, event, gateway modules, actuator, WebSocket starter, test (`backend/services/websocket/build.gradle.kts:7-12`)

Spring Session dependency(`spring-session-data-redis`, `spring-session-core`)와 Spring Security web starter는 확인되지 않는다. 문자열 검색에서도 `SessionRepository`, `RedisIndexedSessionRepository`, `SecurityFilterChain`, `HttpSession` 구현은 확인되지 않았다. Redis starter는 boot build에만 있고, 독립 서비스에는 없다.

JWT library는 `backend/modules/identity/build.gradle.kts:5-7`의 JJWT `0.12.6`이다. 독립 서비스가 이 모듈을 직접 의존하므로 서비스별 JWT 검증은 가능한 구조지만, 현재는 공유 session/authz 저장소를 주입하지 않는다.

### 3.2 JWT 발급·검증 계약

발급 구현은 `AccessTokenService.issue(UUID)` (`AccessTokenService.java:30-33`)에서 다음을 설정한다.

```text
header: alg=EdDSA, kid=<configured key id>
claims: sub=<user UUID>, iss=<issuer>, aud=[<audience>], iat=<now>, exp=<now + 1h>
```

Identity 서비스의 TTL은 `Duration.ofHours(1)`로 하드코딩되어 있다(`backend/services/identity/.../IdentityServiceApplication.java:33-38`). 발급 private key와 검증 public key map은 `discord.auth.jwt.private-key-location`, `public-key-locations.*` 설정에서 읽으며, 기본 위치는 `DISCORD_JWT_CONFIG_TREE` configtree(`/etc/discord-config/`)다(`backend/services/identity/src/main/resources/application.yml:1-5`).

검증 구현(`AccessTokenService.java:35-44`)은 `kid`로 public key를 선택하고, 서명·alg `EdDSA`·issuer·audience·iat·exp·UUID subject를 확인한다. 만료 이후 토큰은 거부된다. `AccessTokenClaims`가 보존하는 값은 `userId`, `issuedAt`, `expiresAt`뿐이며 현재 `sid`, `authzVersion`, role/permission claim을 전달하지 않는다.

공유 검증 wrapper인 `BearerTokenVerifier.requireUserId` (`BearerTokenVerifier.java:15-23`)는 Authorization header에서 bearer token을 분리하고 `AccessTokenService.verify`로 검증한 후 UUID만 반환한다. 즉 서비스 경계에서 사용할 수 있는 주체 정보는 현재 `userId` 하나다.

각 독립 서비스는 자체 `Clock`과 `BearerTokenVerifier` bean을 만든다.

- community: `CommunityServiceApplication.java:23-31`
- message: `MessageServiceApplication.java:23-31`
- websocket: `WebsocketServiceApplication.java:23-31`
- identity: 발급·검증 모두 가능한 `AccessTokenService` bean, `IdentityServiceApplication.java:27-57`

모든 서비스는 issuer/audience/key id/public key map을 configtree에서 읽는다. 현재 JWKS endpoint나 자동 key discovery는 없다. key rotation은 새 `kid`와 public key map을 배포하는 수동 설정 방식으로 보이며, rotation orchestration/overlap 정책은 코드에서 확인되지 않았다.

### 3.3 legacy boot 인증 lifecycle

`AuthController`와 `AuthService`에는 `@Profile("legacy-auth")`가 붙어 있다(`AuthController.java:32-35`, `AuthService.java:28-30`). 따라서 이 로그인 API는 해당 profile을 명시한 boot 실행에서만 활성화되는 legacy 경로다. `DiscordApplication` 자체는 `@SpringBootApplication`과 scheduling만 선언한다(`DiscordApplication.java:7-11`); 어떤 profile을 production에서 켜는지는 코드만으로 확정할 수 없다.

로그인/가입(`AuthService.java:57-95`)은 account를 만들고 `authResult`를 호출한다. `authResult`는 다음을 수행한다(`AuthService.java:198-214`).

1. 32-byte secure random refresh token을 생성한다.
2. 원문 refresh token의 SHA-256 hex hash만 store에 저장한다.
3. UUID session id, user id, device name, created/expires(7일)를 저장한다.
4. access token은 `accessTokenService.issue(profile.id())`로 새로 발급한다.

HTTP 응답은 access token을 JSON으로 반환하고, refresh token은 `dc_refresh` HttpOnly cookie로 설정한다(`AuthController.java:36-37`, `47-83`, `207-243`). cookie path는 `/api/auth`, SameSite는 `Lax`이며 production/HTTPS에서 Secure를 켠다. 이 cookie는 Spring Session cookie가 아니다.

refresh(`AuthService.java:106-128`)는 DB에서 token hash를 찾고, revoked이면 모든 refresh session을 revoke한 뒤 reuse 오류를 반환한다(`110-113`). 만료이면 해당 session을 revoke하고 실패한다(`114-116`). 정상 refresh는 기존 session을 revoked 상태로 바꾸고 새 UUID/token hash row를 만든다(`120-128`). `RefreshSession.rotate`도 이미 revoked/expired인 세션을 회전할 수 없게 한다(`backend/modules/identity/.../RefreshSession.java:42-60`).

logout은 두 경로를 별도로 처리한다(`AuthController.java:85-100`). bearer access token이 있으면 `AuthService.logout`이 access token을 revoke하고, refresh cookie가 있으면 token hash로 refresh session을 revoke한다. 개별 session revoke와 전체 revoke API도 있다(`AuthController.java:117-128`, `AuthStore.java:28-32`).

### 3.4 access token revoke의 실제 범위

`AuthStore`는 raw token을 받는 `revokeAccessToken`/`isAccessTokenRevoked` 계약을 가진다(`AuthStore.java:18-20`). JDBC 구현은 raw token을 내부에서 SHA-256 hash한 뒤 `auth_revoked_access_tokens`에 저장·조회한다(`JdbcAuthStore.java`의 해당 메서드 및 `V2__auth_revoked_access_tokens.sql:1-4`). In-memory store도 hash를 map key로 사용한다(`InMemoryAuthStore.java:51-59`, `160-167`).

legacy boot의 `AuthenticatedUserResolver.requireUserId`는 먼저 이 revoke store를 확인한 뒤 JWT를 검증하고 사용자 row가 존재하는지 확인한다(`AuthenticatedUserResolver.java:20-33`). 그러나 독립 identity/community/message/websocket 서비스는 이 `AuthStore`나 `auth_revoked_access_tokens`를 의존하지 않는다. 독립 서비스는 `BearerTokenVerifier`로 서명·claim·만료만 검사한다. 따라서 현재 access token logout의 즉시 무효화는 **legacy boot 경로에 한정**되며, 독립 서비스에서 같은 JWT가 만료 전 계속 통과할 가능성이 있다. 이는 T171-C1이 해결해야 할 P1 설계 gap이다.

### 3.5 DB session schema와 Redis 사용처

기존 baseline에는 `auth_sessions` 테이블도 있다(`V1__baseline_core_schema.sql:16-22`). 그러나 현재 AuthStore/JdbcAuthStore lifecycle은 `auth_refresh_sessions`를 사용하며(`V6__auth_refresh_sessions.sql:1-12`), `auth_sessions`를 읽거나 쓰는 호출은 확인되지 않았다. 이 테이블은 레거시 잔재인지 후속 구현인지 추가 검증이 필요하다.

`auth_refresh_sessions`는 `id`, `user_id`, unique `token_hash`, `device_name`, `created_at`, `expires_at`, nullable `revoked_at`으로 구성되며 user/created index가 있다(`V6...:1-12`). 재사용 검출은 row 단위 revoked 상태와 user 전체 revoke로 구현된다. DB에 TTL 자동 삭제 정책은 없다.

Redis 설정(`application-redis.yml:1-16`)에는 host/port/password/timeout과 Gateway 전용 key/stream 설정만 있다. Spring Session namespace, Redis serializer, session flush mode, session timeout 설정은 없다.

실제 Redis component는 책임이 분리되어 있다.

- `RedisRateLimitStore` (`.../ops/RedisRateLimitStore.java:10-46`): `rl:<policy>:<subject>:<windowStart>` counter를 TTL로 유지하고 Redis 장애 시 fail-closed.
- `RedisPresenceTtlStore` (`.../presence/RedisPresenceTtlStore.java:14-126`): presence/typing payload를 자체 문자열 포맷으로 저장하고 TTL로 만료시킨다. Redis 장애 시 empty/offline degrade.
- `RedisGatewaySessionRegistry` (`.../gateway/RedisGatewaySessionRegistry.java:21-143`): 설정된 `gateway:sessions` registry와 `gateway:sessions:<sessionId>` 값을 JSON으로 관리하고 24시간 기본 TTL을 적용한다. 이것은 사용자 로그인 session이 아니라 Gateway 연결 상태다.
- `RedisGatewayEventBus` (`.../gateway/RedisGatewayEventBus.java:33-180`): `gateway:*` Redis Stream, node-scoped consumer group, ACK/DLQ를 사용한다. 이것은 event transport이며 authentication session이 아니다.

따라서 Spring Session이 생성할 법한 키를 추측하여 RBAC를 저장하거나, Gateway registry를 사용자 인증 세션으로 재사용하면 책임이 섞인다.

### 3.6 현재 RBAC 원본과 계산

RBAC 관련 DB 원본은 baseline의 `guild_members`, `guild_roles.permissions`, `guild_member_roles`, `channels`, `channel_role_overwrites`다(`V1__baseline_core_schema.sql:32-75`). 전역 role은 `user_global_roles`와 audit log migration(`V7`, `V8`)에 있다. `Permission` enum은 VIEW/SEND/MANAGE 등 bit와 ADMINISTRATOR bit를 정의한다(`Permission.java:3-22`). `PermissionSet.allows`는 ADMINISTRATOR가 모든 permission을 허용한다(`PermissionSet.java:24-26`).

실제 계산은 `EffectivePermissionCalculator`가 everyone permission → member roles의 union → channel overwrite 순으로 계산한다(`EffectivePermissionCalculator.java:7-57`). `InMemoryGuildService.visibleChannels`는 member의 role과 channel overwrite를 로컬 Guild aggregate에서 계산한다(`InMemoryGuildService.java:134-159`). PostgreSQL 경로의 `JdbcGuildSnapshotStore`는 guild/roles/members/channels/overwrites를 한 snapshot으로 load/save한다(`JdbcGuildSnapshotStore.java:20-83`).

확인된 권한 projection은 이 로컬 aggregate/snapshot뿐이며, 별도 Redis RBAC projection, Kafka authz event consumer, `authzVersion` 또는 서비스별 permission cache는 아직 없다. 서비스가 독립 DB로 분리되면 이 모델을 이벤트로 복제할지, 각 서비스가 최소 권한 원본을 갖게 할지 결정해야 한다.

## 4. 현재 lifecycle 요약

```text
로그인/가입 (legacy-auth boot)
  ├─ PostgreSQL auth_accounts/users 저장
  ├─ PostgreSQL auth_refresh_sessions에 SHA-256(refresh token) 저장 (7일)
  ├─ Ed25519 access JWT 발급 (현재 1시간)
  └─ dc_refresh HttpOnly cookie 반환

일반 요청
  ├─ legacy boot: revoke token hash 조회 → JWT 검증 → user row 확인
  └─ 독립 service: JWT 서명/issuer/audience/time/UUID 검증만 수행

refresh
  ├─ refresh hash lookup
  ├─ revoked/expired 검사
  ├─ 기존 row revoke + 새 row 삽입
  └─ access JWT 재발급

RBAC
  └─ 현재 boot Guild aggregate/snapshot에서만 role/overwrite 계산
```

## 5. 확인되지 않은 사항

다음은 코드만으로 사실로 확정할 수 없으며, T171-C0/T171-C1 설계 승인 전에 배포 설정과 운영 환경에서 확인해야 한다.

| 미확정 항목 | 확인이 필요한 이유 | 확인 방법 |
|---|---|---|
| production에서 `legacy-auth`, `postgres`, `redis`, `kafka` 중 어떤 profile이 활성화되는가 | legacy AuthController가 실제 로그인 경로인지 판단해야 한다 | deployment/compose 실행 profile, 실제 startup log |
| 독립 서비스가 현재 운영 트래픽을 받는가 | boot와 standalone의 인증 폐기 불일치 위험 범위를 정해야 한다 | ingress route, service deployment, smoke test |
| identity 서비스가 사용자/refresh DB를 실제로 소유하는가 | 현재 identity 서비스는 JWT 발급과 `/api/users/@me`만 있고 DB auth API가 없다 | 실행 endpoint와 DB connection 설정 확인 |
| `auth_sessions`가 사용 중인가 | 중복 session schema를 삭제/이관할 수 있는지 결정해야 한다 | 전체 소스/DB schema 사용량과 migration history 확인 |
| Redis가 단일 인스턴스/replica/cluster 중 무엇인가 | projection/session consistency와 장애 모델이 달라진다 | 운영 Redis topology 및 ACL/namespace 확인 |
| Redis serializer/namespace/ACL 정책 | Spring Session 또는 RBAC projection 도입 시 충돌 방지 필요 | runtime bean/config와 Redis ACL 확인 |
| public key rotation overlap 기간과 private key custody | 새 `kid` 배포·폐기 순서를 결정해야 한다 | config delivery, secret rotation runbook 확인 |
| access token 즉시 revoke 요구 수준 | JWT-only와 version check의 선택을 좌우한다 | 보안/제품 요구사항 및 SLO 승인 |
| guild permission 변경 event가 어디서 발행되는가 | 현재 Guild mutation은 snapshot 저장만 하며 authz event는 확인되지 않는다 | mutation service와 Kafka producer trace |
| 서비스별 RBAC projection의 허용 stale window | eventual consistency에서 fail-open/closed 정책을 결정해야 한다 | 보안 요구사항과 부하 실측 |
| 사용자 profile과 guild membership의 read model ownership | 메시지/게이트웨이 payload에서 어떤 snapshot을 넣을지 결정해야 한다 | C0 target-state workshop |

## 6. 대안 비교

### A. Spring Session Redis를 로그인 세션의 단일 저장소로 도입

Spring Session dependency와 `SessionRepository`를 추가하고 refresh/access lifecycle을 Spring Security session으로 재작성한다. 즉시 revoke와 다중 인스턴스 세션 공유는 단순해지지만, 현재 stateless JWT API/독립 서비스 구조와 큰 migration이 필요하다. Spring Session 자체는 RBAC 원본이 아니며, guild/channel role 목록을 세션 attribute에 넣으면 권한 변경·크기·stale 문제가 생긴다. 현재 사용자 요구(각 서비스 자체 RBAC)와 직접적인 해결책이 아니다.

### B. DB refresh session + 짧은 JWT + 서비스별 RBAC projection (권장)

Identity가 password/refresh session/키 발급의 원본을 유지한다. JWT에는 `sub`, `sid`, `authzVersion`을 추가하되 role 목록 전체는 넣지 않는다. 각 서비스는 JWT 서명 검증 후 자기 projection을 읽어 permission을 판단한다. Community/Guild 변경은 `AuthzChanged` 이벤트로 publish하고, 각 서비스는 inbox/dedup 후 projection을 갱신한다. Redis를 쓰더라도 Spring Session key가 아니라 명시된 authz projection namespace를 별도로 소유한다.

장점은 서비스가 독립 인가하고 이벤트 기반 scale-out을 유지하면서 token 크기와 권한 stale 범위를 제어할 수 있다는 점이다. 단점은 projection lag, event replay, Redis 장애 정책을 명시해야 한다는 점이다.

### C. JWT에 전체 RBAC role/permission을 넣고 JWT만 검증

요청 경로가 단순하고 외부 저장소 호출이 없지만, 역할 제거/차단을 token 만료까지 즉시 반영할 수 없다. 전역 role과 guild/channel 권한이 커지면 token size와 재발급 폭증이 발생한다. 현재 access token이 user id만 포함한다는 실측과도 큰 변경이다. 즉시 revoke가 필요한 Discord형 권한에는 부적합하다.

### D. DB refresh session + 서비스별 로컬 DB projection (Redis 없음)

각 서비스가 own DB에 authz projection을 저장하고 Kafka로 갱신한다. Redis 장애가 권한 판단을 막지 않는 대신 서비스별 DB read/write와 projection bootstrap/lag 운영이 필요하다. Redis를 이미 운영하지 않는 독립 서비스에는 이 선택이 더 단순할 수 있으므로, B의 Redis 사용은 measured hot-read/latency 요구가 있을 때만 선택해야 한다.

## 7. 권장 결정 초안

다음은 C0 target-state에 올릴 권장 초안이다. 사용자/보안 승인 전에는 `approved`로 간주하지 않는다.

1. **인증 원본:** Identity 서비스가 사용자 계정, refresh session, access token key lifecycle의 단일 owner가 된다. 기존 boot `legacy-auth`는 전환 기간 동안만 유지하며, 두 issuer/폐기 경로를 장기 운영하지 않는다.
2. **Access JWT:** `EdDSA`, `iss`, `aud`, `kid`, `sub`, `iat`, `exp`를 필수로 유지한다. 즉시 revoke 또는 권한 세대 확인이 필요하면 `sid`와 `authzVersion`을 추가한다. role 목록 전체는 넣지 않는다.
3. **Refresh session:** 현재 `auth_refresh_sessions`의 hash-at-rest, rotation, reuse detection을 보존한다. Spring Session Redis로 자동 대체하지 않는다. 세션 cookie 이름/경로와 refresh rotation contract는 Identity API 계약으로 승격한다.
4. **서비스 인증:** 각 서비스는 자체 JWT verifier를 가져야 한다. shared verifier library를 재사용하되, issuer/audience/key map 설정은 서비스별로 명시한다. user bearer token과 workload token은 서로 다른 audience로 분리한다.
5. **RBAC 원본:** Community/Guild가 guild membership, roles, role permissions, channel overwrite의 canonical writer다. 서비스 간 매 요청 동기 권한 호출은 하지 않는다.
6. **서비스 인가:** Message, Gateway/WebSocket, Notification 등 각 서비스가 자기 projection에서 permission을 계산한다. projection이 없거나 version이 허용 stale window를 넘으면 write/민감 read는 fail-closed한다.
7. **권한 공유:** 권한 변경은 versioned `AuthzChanged` event로 전파한다. projection 저장소는 Redis 또는 서비스 DB 중 하나를 task별로 선택하며, Spring Session key 구조와 섞지 않는다. Redis를 선택할 때만 명시적인 namespace, serializer, TTL, ACL, 장애 정책을 별도 계약으로 만든다.
8. **채널 범위:** 로그인 session object에 channel id 목록을 넣지 않는다. 채널 ACL은 Community canonical model에서 계산되어 서비스 projection에 필요한 범위로 전달된다. 채널별 permission이 실제 요구되면 별도의 channel policy projection을 정의한다.

## 8. T171-C1에서 닫아야 할 결정과 구현 전제

### 필수 결정

- standalone 서비스의 공식 인증 진입점과 legacy boot 전환 종료 조건
- JWT `sid`/`authzVersion` 도입 여부와 claim의 정수 타입/범위
- authz version의 단위: 사용자 전역, guild 단위, 또는 사용자-guild tuple
- projection 저장소: Redis shared projection인지 서비스별 DB인지
- 권한 event envelope: `eventId`, `aggregateId`, `authzVersion`, effective permissions, occurredAt, schemaVersion
- projection stale 허용 시간과 Redis/DB 장애 시 read/write별 fail-closed 정책
- key rotation의 old/new `kid` overlap, verifier reload, private key custody
- logout/revoke가 standalone 서비스에 즉시 반영되어야 하는지, 아니면 JWT TTL+version check로 허용할지

### 구현하지 말아야 할 추측

- Spring Session이 관리한다고 가정한 Redis 키 생성
- `userId:guildId:channelId`를 Spring Session 키로 사용하는 설계
- JWT에 현재 코드에 없는 roles/permissions를 즉시 추가하는 구현
- service 간 Community 동기 호출을 권한 경로의 기본값으로 넣는 구현
- 현재 Redis Gateway registry를 인증 session으로 재사용하는 구현

### 다음 task의 최소 검증

T171-C1 구현 전에 다음 테스트/증거가 있어야 한다.

1. 각 서비스가 동일한 `kid`/issuer/audience 정책으로 valid token을 허용하고 wrong audience, wrong issuer, unknown kid, expired token을 거부하는 테스트.
2. Identity refresh rotation의 정상, 만료, revoked, reuse-detected 경로와 DB unique hash 경계를 검증하는 테스트.
3. standalone 서비스가 revoke된 legacy token을 허용하는 현재 gap을 재현하는 테스트와, 목표 설계에서 이를 해결하는 검증 방식.
4. authz event의 duplicate/out-of-order/stale projection 처리 테스트.
5. Redis를 선택할 경우 serializer/namespace/ACL/TTL 및 Redis 장애 fail-closed 테스트. Spring Session을 선택하지 않는다면 해당 의존성이 없음을 dependency report로 유지.

## 9. 산출물 경계

이 파일은 조사 산출물이며 다음 파일을 수정하지 않았다.

- `backend/**` 코드 및 설정
- `docs/01-plan/**`, `docs/02-design/**` 기존 T171-C 문서
- Git 설정·커밋·푸시

다음 단계에서 이 문서를 근거로 C0 target-state와 C1 구현계획을 작성하되, 위 미확정 항목을 사실처럼 승격하지 않는다.
