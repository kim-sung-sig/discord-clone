# T230 선언적 트랜잭션 전환 계획

## 승인과 상태

- 상태: 0단계 구현 완료(게이트 활성화와 DB 이름, 테스트 순서 의존 수정), 변경분 독립 재검토와 PR 병합 승인 대기
- 승인 근거: 2026-11-28 사용자 결정과 [T230 아키텍처 방향 ADR](../../02-design/features/T230-architecture-direction.adr.md).
- 목표: 수동 트랜잭션을 걷어내고 기존 원자성, 잠금, 실패 처리 의미를 유지한다.

## 범위와 경계

트랜잭션 경계는 boot·services adapter의 원자적 port 메서드에 `@Transactional`로 선언한다. ADR의 예외가 실제 적용되기 전까지 `backend/modules/*`에는 트랜잭션 어노테이션을 두지 않는다. 컨텍스트 간 port와 인가 및 outbox/inbox 계약은 ADR을 따른다.

범위 밖은 저장 도구 전면 교체, 메시지 조회 N+1·색인 개선(T224-1~2), JPA entity 도입이다. JdbcClient·MyBatis·JPA 선택은 파일럿 뒤 결정한다.

## 관찰과 변경 파일

게이트 추가 전 `backend` job은 `postgres:17`과 접속 환경 변수를 제공했지만, `Backend tests` 단계에 DB 테스트 실행 변수가 없어 조건부 테스트 파일 26개가 PR CI에서 건너뛰어졌다.

0단계 변경 범위는 이 계획서, T230 ADR, [T224 복원 계획서](T224-jpa-persistence-migration.plan.md), `.github/workflows/ci.yml`, 그리고 무작위 순서 실행에서 순서 의존이 드러난 테스트 코드 5건이다(운영 코드 변경 없음). 새 환경 변수로 기존 문자열 단언이 깨지는 경우에만 관련 CI 계약을 조정하며, CI를 읽는 계약 스크립트 7개는 실행해 모두 통과했다.

## 구현 및 검증

### 0단계. CI PostgreSQL 테스트 실행

이 게이트를 켜면 조건부 PostgreSQL 테스트 파일 26개가 PR CI에서 처음 실행되므로 선행 실패가 드러날 수 있다.

코디네이터가 로컬 PostgreSQL 17에서 게이트를 켜고 전체 백엔드 스위트를 실행했다. DB 이름이 `discord`일 때 404개 중 9개가 실패했으며, 원인은 데이터 삭제가 포함된 테스트가 공용 DB를 보호하기 위해 `discord_runtime_guild_`로 시작하는 DB 이름만 허용하는 안전장치였다. 이름을 `discord_runtime_guild_t230`으로 바꾸고 다시 실행한 `PostgresGuildStoreContractTest`, `PostgresModerationServiceContractTest`, `PostgresGatewayEventLogContractTest`, `PostgresGuildServiceTest`는 모두 통과했다. 같은 DB(`discord_runtime_guild_t230`) 하나로 전체 백엔드 스위트(`gradlew test --continue`)를 다시 실행한 결과는 BUILD SUCCESSFUL(3분 20초), 테스트 422개, 실패 0개였다. 건너뛴 22개는 Postgres 게이트가 아니라 별도 환경 변수와 외부 서비스(Redis, Kafka)가 필요한 테스트다(`CentralKafkaGatewayEventBusSmokeTest`, `CentralRedisGatewayFanoutSmokeTest`, `CentralRedisGatewaySessionRegistrySmokeTest`, `CentralRedisConnectivitySmokeTest`, `MessageOutboxLeaseFailureIntegrationTest`, `MessagePublicationFailureIntegrationTest`)이며 이 단계의 범위 밖이다. 따라서 Postgres 게이트로 드러난 실패는 위 DB 이름 안전장치 하나뿐이었다. 로컬 검증에는 별도 포트로 띄운 일회용 PostgreSQL 컨테이너를 사용했다. 개발 PC의 다른 프로젝트 DB는 사용하지 않았다.

0단계 변경은 `Backend tests` 단계의 `DISCORD_RUN_POSTGRES_TESTS: "true"` 추가와 `backend` job의 DB 이름을 `discord_runtime_guild_ci`로 바꾸는 작업이다. 서비스 DB 이름, 헬스 체크와 JDBC URL에 같은 이름을 사용한다.

0단계 완료 기준은 **게이트가 켜지고 모든 실패가 원인별로 분류된 상태**다.

1단계 진입 조건은 **게이트를 켠 GitHub CI `backend` job의 첫 성공**이다. 실패 분류는 0단계 완료 기준에 해당한다. 현재 `qa-runtime`의 Ubuntu 26.04와 Playwright 1.60.0 비호환은 코드와 무관하며 이 진입 조건에서 제외한다.

**첫 GitHub CI 결과.** 게이트를 켠 `backend` job은 첫 실행에서 성공했다(5분 8초, 게이트를 끈 직전 PR의 2분 30초 대비 증가). 다만 CI 로그에는 개별 테스트의 실행·건너뜀 목록이 남지 않아 DB 테스트가 실제로 실행됐다는 직접 증거는 없고, 증가한 시간이 로컬에서 게이트를 켰을 때의 증가폭과 일치한다는 정황 증거뿐이다.

#### 순서 독립성 검증 (0단계)

같은 DB를 공유하는 게이트 테스트가 실행 순서에 의존하는지 확인하려고 JUnit의 클래스·메서드 순서를 무작위로 바꿔 `boot` 테스트를 반복 실행했다. 방법: 환경 변수 `JAVA_TOOL_OPTIONS`로 `junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random`, `junit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`, `junit.jupiter.execution.order.random.seed=<시드>`를 주고 `gradlew :backend:boot:test --rerun --continue`를 실행한다. 실행마다 새 DB(`discord_runtime_guild_*`)를 쓴다.

기본 순서에서는 통과하지만 무작위 순서에서는 다음이 실패했다. 첫 서베이는 시드 1~6, 11, 2026의 8회였고, 수정을 진행하면서 같은 시드를 다시 돌렸다.

| 테스트 | 실패한 실행 | 원인 |
| --- | --- | --- |
| `JdbcGatewayEventLogTest.oldestSequenceIgnoresExpiredRows` | 첫 서베이 8회 중 7회 | 다른 테스트가 `gateway_event_log`에 남긴 행에 시퀀스 가정이 의존 |
| `GlobalAdminRoleCommandRunnerTest` 5개 | 첫 서베이 8회 중 1회 | `DELETE FROM guilds`가 `premium_entitlements` 외래키에 막힘(`guilds`를 막는 유일한 비-CASCADE 자식) |
| `GatewayWebSocketIntegrationTest.clientFrameContractFixtureIsAcceptedByTheServer` | 첫 서베이 8회 중 2회(시드 2, 4). 타임아웃 분리 뒤에도 시드 11, 2026에서 실패 | 아래 "알려진 서버 동작" 참고 |
| `PostgresAuthStoreTest` 5개 | 첫 수정 이후 시드 11을 4번 돌려 3번 실패(첫 서베이의 시드 11은 통과) | 위 `GlobalAdminRoleCommandRunnerTest`와 같은 결함. 수정 과정에서 테스트 클래스 구성이 바뀌어 같은 시드의 순서가 달라진 것으로 보이며, 같은 구성에서도 한 번 통과해 간헐성이 있다 |
| `OperationalHardeningFilterTrustedProxyTest.authLoginRateLimitUsesForwardedClientIpFromAllowlistedProxy` | 첫 서베이 8회 중 1회(시드 4) | 원인 미확인(아래) |

**수정**(모두 테스트 코드, 운영 코드 변경 없음): `JdbcGatewayEventLogTest`는 정리 단계에서 `gateway_event_log`를 비운다, `GlobalAdminRoleCommandRunnerTest`와 `PostgresAuthStoreTest`는 `guilds` 앞에 `premium_entitlements`를 지운다, 계약 테스트는 identify 전에 데이터 이벤트 하나를 발행한다. 이 과정에서 100ms 하트비트 타임아웃을 쓰는 `websocketHeartbeatTimeoutClosesStaleSocket`를 별도 Spring 컨텍스트 클래스(`GatewayWebSocketHeartbeatTimeoutTest`)로 옮기고 공통 헬퍼를 `GatewayWebSocketTestSupport`로 뽑았다. 테스트 본문과 단언은 그대로다. 처음에는 이 계약 테스트의 실패 원인을 100ms 타임아웃과 콜드 스타트 경쟁으로 잘못 짚었다. 타임아웃을 분리해도 같은 실패가 남아서 서버 코드를 읽어 실제 원인을 확정했다.

**최종 커밋(`0f32654`) 검증**: 기본 순서 1회와 시드 11, 2026, 2, 4, 7, 8, 9 총 8회 모두 `BUILD SUCCESSFUL`이었다. 시드 1, 3, 5, 6은 최종 커밋에서 다시 돌리지 않았다(중간 커밋에서는 통과). 이 결과는 순서 의존이 없다는 증명이 아니라 **알려진 반례를 제거했다는 증거**다. 실제로 시드를 넓혀서 앞선 시드로는 보이지 않던 `PostgresAuthStoreTest`가 드러났고, 같은 시드에서도 실패가 재현되지 않는 경우가 있어 간헐성이 남아 있다.

**알려진 서버 동작(수정하지 않음).** `READY`·`HEARTBEAT_ACK` 같은 제어 이벤트는 `InMemoryGatewayEventLog.allocateSequence()`로 번호만 소모하고 로그에는 저장되지 않는다. 로그가 비어 있으면 `oldestSequence()`가 `nextSequence`를 돌려주므로, 데이터 이벤트가 하나도 없는 상태에서 `identify → heartbeat → 재연결 resume`을 하면 `InMemoryGatewayService.resumeInRound`의 보존 범위 검사(`replayFrom < oldestSequence - 1`)가 `RESYNC_REQUIRED`를 낸다. 잃은 데이터가 없는데도 전체 재동기화를 요구하는 동작이다. 데이터 이벤트가 쌓인 운영에서는 드물 수 있으나 조용한 환경에서는 날 수 있고, 프런트가 `RESYNC_REQUIRED`를 어떻게 처리하는지는 확인하지 않았다. 테스트는 이를 우회하도록 데이터 이벤트를 먼저 발행한다. 서버 수정은 인가·이벤트 로그에 가까워 별도 결정이 필요하므로 이 단계 범위 밖이다.

**미해결.** (1) `OperationalHardeningFilterTrustedProxyTest.authLoginRateLimitUsesForwardedClientIpFromAllowlistedProxy`가 첫 서베이의 시드 4에서 한 번 실패했다. 이후 실행에서는 재현되지 않았다. 코드를 읽어 빈별 카운터와 서로 다른 IP를 확인했으나 실패한 요청의 증거를 찾지 못했고, rate limit이 분 단위 고정 창이면 요청 도중 분이 바뀌는 시간 기반 불안정성이 원인일 수 있다는 가설만 남았다. 고치지 않았다. (2) `PostgresAuthStoreTest`의 간헐 실패는 위 정리 결함으로 설명되지만, 같은 시드에서 재현이 달랐던 이유(캐시된 Spring 컨텍스트의 백그라운드 작업이 공유 DB에서 다른 테스트의 정리와 겹칠 가능성)는 확인하지 못했다.

### 1단계. 트랜잭션 기반

- 기존 연결 헬퍼도 같은 트랜잭션에 참여하도록 `TransactionAwareDataSourceProxy` 또는 `JdbcClient`/`DataSourceUtils`를 검토한다.
- 쓰기 도중 실패를 유도하고 일부만 커밋되는 일이 없는지 PostgreSQL에서 확인할 롤백 테스트 틀을 만든다.
- adapter에서 `setAutoCommit`, `commit()`, `rollback()` 호출을 금지하는 가드 테스트를 둔다. 기존 수동 트랜잭션 클래스는 예외 목록에 두고 전환할 때마다 제거한다.

### 2단계. 스레드 저장소 파일럿

`JdbcThreadService`의 수동 트랜잭션 1곳을 전환한다. 명시적 행 잠금이 없는 경로에서 연결 참여, 롤백, 기존 PostgreSQL 계약을 확인한 뒤 이후 전환 방식을 확정한다.

기존 `PostgresThreadServiceTest.java`의 48행과 73행은 `new JdbcThreadService(...)`로 Spring 프록시를 우회한다. 파일럿 완료 조건은 Spring 컨텍스트가 주입한 `ThreadService` 빈을 호출해 첫 번째 쓰기 이후 실패를 유도하고, PostgreSQL에서 두 쓰기의 변경분이 모두 롤백됐는지 확인하는 것이다.

### 3단계. 묶음 전환

파일럿을 제외한 클래스는 위험이 작은 순서로 PR당 2~3개씩 묶는다. 클래스마다 (a) 기존 `Connection` 헬퍼를 유지한 채 수동 트랜잭션을 제거하는 변경과 (b) 나중에 `JdbcClient`로 옮기는 변경을 나눈다. (b)의 도구 적용 여부는 파일럿 결정에 따른다.

| 수동 처리 위치 수 | 클래스 |
| --- | --- |
| 4 | `JdbcMessageStore` |
| 3 | `JdbcServerEventService`, `JdbcInviteService` |
| 2 | `JdbcDirectMessageService`, `JdbcAuthStore` |
| 1 | `JdbcTotpStore`, `JdbcThreadService`(2단계), `JdbcNotificationInboxService`, `JdbcModerationService`, `JdbcGuildStore`, `JdbcGatewaySessionCursorStore`, `JdbcGatewayEventLog`, `JdbcChannelReadService` |

### 4단계. 후속 정리

`services/identity`의 `TransactionTemplate`을 정리하고 검증이 끝난 지식을 wiki에 반영한다. 전환한 각 저장소에서 `InMemory*` 쌍이 맡던 동작을 PostgreSQL 테스트가 덮는지 확인한 뒤 해당 쌍을 제거한다.

## 전환 위험과 검증 기준

1. 트랜잭션 인식 처리가 없는 `DataSource`에서 `getConnection()`을 직접 호출하면 `@Transactional` 메서드 안에서도 트랜잭션에 참여하지 않는 별도 연결을 얻는다. 롤백 테스트에서 같은 원자적 쓰기에 속한 변경이 함께 되돌아가는지 확인한다.
2. checked 예외인 `SQLException`은 기본 롤백 대상에서 빠진다. 현재 수동 코드는 `SQLException | RuntimeException`에서 롤백하므로 checked 예외가 경계를 통과하는 경로는 `rollbackFor`로 기존 의미를 맞춘다.
3. `final` 클래스인 `JdbcExpressionService`, `JdbcModerationService`, `JdbcTotpStore`는 Spring 기본 클래스 기반 프록시(CGLIB)로 감쌀 수 없다. 실제 호출이 프록시를 통과하는지 확인한다.
4. T212의 moderation·guild `FOR UPDATE` 잠금 대상과 획득 순서를 유지한다. 트랜잭션 범위를 넓히지 않고 기존 경합 테스트로 확인한다.
5. `JdbcInviteService.create`의 중복 키(SQLState `23505`) 재시도는 시도마다 새 트랜잭션이 필요하다. 재시도 루프를 트랜잭션 밖에 두고 각 시도를 트랜잭션 경계로 진입시킨다. 같은 클래스 안의 호출(self-invocation)에는 프록시 AOP가 적용되지 않는다.
6. `JdbcGuildStore.readSnapshot`은 SQL `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY`로 두 속성을 강제한다. `@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)`로 옮기는 전환의 완료 조건은 해당 트랜잭션에서 `SHOW transaction_read_only`는 `on`, `SHOW transaction_isolation`은 `repeatable read`를 반환한다고 직접 단언하는 테스트다.
7. Guild·Auth 계열 등 게이트 테스트 일부는 공유 DB에서 조건 없는 `DELETE`로 정리한다. `rg -n --hidden 'maxParallelForks|junit[.]jupiter[.]execution[.]parallel' -g '*.gradle.kts' -g '*.gradle' -g 'gradle.properties' -g 'junit-platform.*'`로 검색한 결과 일치 항목은 없었다. 코디네이터의 로컬 기본 순서 실행에서는 같은 DB로 422개가 통과했다. 같은 설정으로 CI를 다시 돌리는 것은 반복 확인일 뿐 순서 독립성을 검증하지 못한다. 순서 독립성은 JUnit 클래스·메서드 순서를 무작위로 바꿔 실제로 확인했고, 그 결과 실제 순서 의존 결함이 발견되어 고쳤다(아래 "순서 독립성 검증" 절). 모든 순서에서 안전하다는 증명은 아니다.

0단계의 GitHub CI 실행 결과는 PR에서 확인하고 이 계획서에 반영한다(로컬 검증은 위와 같다).
