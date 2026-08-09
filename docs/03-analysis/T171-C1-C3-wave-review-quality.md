# T171-C1/C3 조사 문서 품질 검수

상태: PASS
작성일: 2026-08-09
검수 범위: `docs/03-analysis/T171-C1-auth-session-discovery.md`, `docs/03-analysis/T171-C3-event-contract-discovery.md`를 실제 소스와 핵심 계약 기준으로 대조
기준: 90/100 이상 및 P0/P1 0건

## 1. 고정 입력

검수 기준 commit: `fe6807d17b9266e87c74c4e67f0ec9b1d7e95785`
branch: `task_T171c-msa-scaleout-decision`
working tree 참고: 입력 문서와 관련 계획 문서가 untracked인 상태에서 문서 내용 자체를 SHA-256으로 고정했다.

SHA-256 계산 명령: `sha256sum docs/03-analysis/T171-C1-auth-session-discovery.md docs/03-analysis/T171-C3-event-contract-discovery.md`
의미: PowerShell `Get-FileHash -Algorithm SHA256`와 같은 파일 바이트 해시다. `git hash-object`는 Git object header/저장소 해시 알고리즘의 영향을 받으므로 이번 입력 파일 SHA-256 근거로 쓰지 않았다.

| 입력 | SHA-256 |
|---|---|
| `docs/03-analysis/T171-C1-auth-session-discovery.md` | `f01ffb29de532d2bd601b11f6b60b6b1ad361495c62cda521abd536008e7deb4` |
| `docs/03-analysis/T171-C3-event-contract-discovery.md` | `9a87fa07d03f752becdec7533ff39e9f312464ac2541d641886c581d4a034760` |

## 2. 판정

| 대상 | 점수 | P0 | P1 | P2 | 판정 |
|---|---:|---:|---:|---:|---|
| C1 인증·세션·RBAC 조사 | 94/100 | 0 | 0 | 1 | PASS |
| C3 이벤트·Outbox·Kafka 계약 조사 | 96/100 | 0 | 0 | 0 | PASS |
| 통합 | 95/100 | 0 | 0 | 1 | PASS |

## 3. Findings

### P0

없음.

### P1

없음.

### P2-1. C1 독립 서비스 의존성 목록이 실제 build 파일보다 좁게 서술됨

위치: `docs/03-analysis/T171-C1-auth-session-discovery.md:49`, `docs/03-analysis/T171-C1-auth-session-discovery.md:51`, `docs/03-analysis/T171-C1-auth-session-discovery.md:52`, `docs/03-analysis/T171-C1-auth-session-discovery.md:53`, `docs/03-analysis/T171-C1-auth-session-discovery.md:54`

문서는 독립 서비스 build 파일에 "다음만 있다"고 정리하지만 실제 의존성에는 추가 project/shared/crypto 의존성이 있다.

- `backend/services/identity/build.gradle.kts:13-19`: `identity` 외에 `social`, `user`, `shared:common`, `spring-security-crypto`가 있다.
- `backend/services/message/build.gradle.kts:13-21`: `shared:common` 및 여러 도메인 module 의존성이 있다.
- `backend/services/community/build.gradle.kts:13-21`: `shared:common` 및 여러 도메인 module 의존성이 있다.
- `backend/services/websocket/build.gradle.kts:13-18`: `shared:common`이 있다.

영향: 핵심 결론인 Spring Session Redis와 Spring Security web filter chain 부재는 유지된다. 전체 검색에서 관련 hit는 `backend/boot/build.gradle.kts:33`과 `backend/services/identity/build.gradle.kts:19`의 `spring-security-crypto`뿐이며, `spring-session-data-redis`, `SessionRepository`, `SecurityFilterChain`, `HttpSession` 구현은 확인되지 않았다. 따라서 구현 차단은 아니지만 문서 정확도 감점이다.

## 4. 핵심 소스 대조 결과

C1 핵심 계약은 소스와 일치한다.

- JWT 발급은 `sub`, `iss`, `aud`, `iat`, `exp`, `kid` 중심이며 `sid`/`authzVersion`을 보존하지 않는다: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:30-43`, `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenClaims.java:6`.
- 독립 서비스는 자체 `BearerTokenVerifier` 또는 `AccessTokenService`로 bearer JWT를 검증한다: `backend/services/community/src/main/java/com/example/discord/communityservice/CommunityServiceApplication.java:23-31`, `backend/services/message/src/main/java/com/example/discord/messageservice/MessageServiceApplication.java:23-31`, `backend/services/websocket/src/main/java/com/example/discord/websocketservice/WebsocketServiceApplication.java:23-31`, `backend/services/identity/src/main/java/com/example/discord/identityservice/ProfileController.java:22-31`.
- legacy boot logout/revoke는 `AuthStore`를 통해 access token hash와 refresh session DB row를 다룬다: `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:97-128`, `backend/boot/src/main/java/com/example/discord/auth/JdbcAuthStore.java:99-129`, `backend/boot/src/main/java/com/example/discord/auth/JdbcAuthStore.java:141-177`.
- legacy boot resolver만 revoke store와 user row 존재를 확인한다: `backend/boot/src/main/java/com/example/discord/auth/AuthenticatedUserResolver.java:20-33`.
- Redis 설정은 Gateway session/stream 중심이며 Spring Session namespace가 아니다: `backend/boot/src/main/resources/application-redis.yml:1-16`.

C3 핵심 계약은 소스와 일치한다.

- `MessagePublished`는 `eventId`, `messageId`, `author`, `target`, `mentions`, `correlationId`, `occurredAt`만 가진다: `backend/modules/message/src/main/java/com/example/discord/message/MessagePublished.java:8-16`.
- publish use case가 message와 별도 `MessagePublished.eventId`를 생성하고 outbox 저장에 넘긴다: `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:73-95`.
- JDBC `savePublished`는 message/idempotency/outbox를 같은 transaction에 저장한다: `backend/boot/src/main/java/com/example/discord/message/JdbcMessageStore.java:159-180`.
- relay는 dispatcher 성공 반환 뒤 `markPublished`를 호출한다: `backend/modules/message/src/main/java/com/example/discord/message/DefaultMessagePublicationRelay.java:63-68`.
- 현재 dispatcher는 `InMemoryGatewayService`를 직접 호출하고 Message를 다시 조회한다: `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java:91-106`.
- `GatewayBusPublishCommand`에는 eventId가 없고 Gateway bus adapter들이 새 UUID를 만든다: `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayBusPublishCommand.java:7-12`, `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayEventBus.java:21-29`, `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:66-78`, `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:99-115`.
- Kafka Gateway adapter는 `kafka.send(...)` future를 기다리지 않는다: `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:66-78`, `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:159-177`.
- Redis Gateway adapter는 listener failure 후 DLQ metadata를 남기고 원 stream record를 ACK한다: `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:165-177`, `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:239-245`, `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:269-285`.
- 독립 `message-service`에는 Kafka/Redis 의존성과 boot relay wiring이 없다: `backend/services/message/build.gradle.kts:12-22`, `infra/docker/docker-compose.yml:38-48`.

## 5. 구현 차단 미결정

다음은 문서 품질 결함은 아니지만, C1/C3 구현 착수 전에 닫아야 하는 결정이다.

- C1: production에서 `legacy-auth`, `postgres`, `redis`, `kafka` profile 중 무엇이 실제 활성인지와 standalone 서비스가 운영 트래픽을 받는지 확정해야 한다.
- C1: logout/revoke를 standalone 서비스에 즉시 반영할지, 짧은 JWT TTL과 `authzVersion`/session version check로 허용할지 결정해야 한다.
- C1: `authzVersion` 단위, projection 저장소, stale window, fail-closed 정책, key rotation overlap을 확정해야 한다.
- C3: `contentSnapshot`을 event에 넣을지, `payloadRef`/authenticated snapshot API로 갈지 하나로 고정해야 한다.
- C3: 원본 `eventId` 보존 envelope, Kafka ACK timeout/idempotence 설정, consumer inbox unique 제약, retry/DLQ replay 계약을 구현 기준으로 확정해야 한다.
- C3: 독립 `message-service` application context에 relay, DB adapter, Kafka publisher가 실제 등록되는 acceptance가 필요하다.

## 6. Evidence

검토 명령:

- `sha256sum docs/03-analysis/T171-C1-auth-session-discovery.md docs/03-analysis/T171-C3-event-contract-discovery.md`
- `nl -ba docs/03-analysis/T171-C1-auth-session-discovery.md`
- `nl -ba docs/03-analysis/T171-C3-event-contract-discovery.md`
- `rg -n "spring-session|SessionRepository|RedisIndexedSessionRepository|SecurityFilterChain|HttpSession|spring-boot-starter-security|spring-security" backend settings.gradle.kts build.gradle.kts`
- `rg -n "record MessagePublished|class DefaultPublishMessageUseCase|savePublished|MessagePublicationRelayWorker|DefaultMessagePublicationRelay|claimPendingPublications|markPublished|releaseFailed" backend -g "*.java"`
- `rg -n "record GatewayBusEvent|record GatewayBusPublishCommand|class KafkaGatewayEventBus|class RedisGatewayEventBus|UUID.randomUUID|kafka.send|dead-letter|ConsumerRecord|Acknowledg" backend/modules/gateway backend/boot/src/main/java -g "*.java"`

테스트 실행: 없음. 이번 작업은 기존 조사 문서의 소스 대조 품질 검수이며 제품 코드 변경이나 테스트 대상 동작 변경이 없었다.

## 7. 최종 권고

RECOMMENDATION: APPROVE

두 입력 문서는 핵심 계약 관점에서 구현 계획의 근거로 사용할 수 있다. 단, C1의 독립 서비스 의존성 목록은 후속 문서에서 보정하고, 위 구현 차단 미결정을 닫기 전에는 구현 착수 승인으로 해석하지 않는다.

STATUS: DONE
FINDINGS: P2 1건, P0/P1 없음
SPEC_ALIGNMENT: aligned, 단 C1 의존성 목록에 문서 정확도 gap 있음
TEST_EVIDENCE: 파일 SHA-256, `rg`, `nl -ba` 기반 소스 대조. 테스트 미실행
RISKS: 구현 전 미결정이 남아 있음
RECOMMENDATION: APPROVE
