# T230 선언적 트랜잭션 전환 계획

## 승인과 상태

- 상태: 0단계: 리뷰 지적 반영 중, GitHub CI `backend` 첫 성공 대기
- 승인 근거: 2026-11-28 사용자 결정과 [T230 아키텍처 방향 ADR](../../02-design/features/T230-architecture-direction.adr.md).
- 목표: 수동 트랜잭션을 걷어내고 기존 원자성, 잠금, 실패 처리 의미를 유지한다.

## 범위와 경계

트랜잭션 경계는 boot·services adapter의 원자적 port 메서드에 `@Transactional`로 선언한다. ADR의 예외가 실제 적용되기 전까지 `backend/modules/*`에는 트랜잭션 어노테이션을 두지 않는다. 컨텍스트 간 port와 인가 및 outbox/inbox 계약은 ADR을 따른다.

범위 밖은 저장 도구 전면 교체, 메시지 조회 N+1·색인 개선(T224-1~2), JPA entity 도입이다. JdbcClient·MyBatis·JPA 선택은 파일럿 뒤 결정한다.

## 관찰과 변경 파일

게이트 추가 전 `backend` job은 `postgres:17`과 접속 환경 변수를 제공했지만, `Backend tests` 단계에 DB 테스트 실행 변수가 없어 조건부 테스트 파일 26개가 PR CI에서 건너뛰어졌다.

0단계 변경 범위는 이 계획서, T230 ADR, [T224 복원 계획서](T224-jpa-persistence-migration.plan.md), `.github/workflows/ci.yml`이다. 새 환경 변수로 기존 문자열 단언이 깨지는 경우에만 관련 CI 계약을 조정한다.

## 구현 및 검증

### 0단계. CI PostgreSQL 테스트 실행

이 게이트를 켜면 조건부 PostgreSQL 테스트 파일 26개가 PR CI에서 처음 실행되므로 선행 실패가 드러날 수 있다.

코디네이터가 로컬 PostgreSQL 17에서 게이트를 켜고 전체 백엔드 스위트를 실행했다. DB 이름이 `discord`일 때 404개 중 9개가 실패했으며, 원인은 데이터 삭제가 포함된 테스트가 공용 DB를 보호하기 위해 `discord_runtime_guild_`로 시작하는 DB 이름만 허용하는 안전장치였다. 이름을 `discord_runtime_guild_t230`으로 바꾸고 다시 실행한 `PostgresGuildStoreContractTest`, `PostgresModerationServiceContractTest`, `PostgresGatewayEventLogContractTest`, `PostgresGuildServiceTest`는 모두 통과했다. 같은 DB(`discord_runtime_guild_t230`) 하나로 전체 백엔드 스위트(`gradlew test --continue`)를 다시 실행한 결과는 BUILD SUCCESSFUL(3분 20초), 테스트 422개, 실패 0개였다. 건너뛴 22개는 Postgres 게이트가 아니라 별도 환경 변수와 외부 서비스(Redis, Kafka)가 필요한 테스트다(`CentralKafkaGatewayEventBusSmokeTest`, `CentralRedisGatewayFanoutSmokeTest`, `CentralRedisGatewaySessionRegistrySmokeTest`, `CentralRedisConnectivitySmokeTest`, `MessageOutboxLeaseFailureIntegrationTest`, `MessagePublicationFailureIntegrationTest`)이며 이 단계의 범위 밖이다. 따라서 Postgres 게이트로 드러난 실패는 위 DB 이름 안전장치 하나뿐이었다. 로컬 검증에는 별도 포트로 띄운 일회용 PostgreSQL 컨테이너를 사용했다. 개발 PC의 다른 프로젝트 DB는 사용하지 않았다.

0단계 변경은 `Backend tests` 단계의 `DISCORD_RUN_POSTGRES_TESTS: "true"` 추가와 `backend` job의 DB 이름을 `discord_runtime_guild_ci`로 바꾸는 작업이다. 서비스 DB 이름, 헬스 체크와 JDBC URL에 같은 이름을 사용한다.

0단계 완료 기준은 **게이트가 켜지고 모든 실패가 원인별로 분류된 상태**다.

1단계 진입 조건은 **게이트를 켠 GitHub CI `backend` job의 첫 성공**이다. 실패 분류는 0단계 완료 기준에 해당한다. 현재 `qa-runtime`의 Ubuntu 26.04와 Playwright 1.60.0 비호환은 코드와 무관하며 이 진입 조건에서 제외한다.

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
7. Guild·Auth 계열 등 게이트 테스트 일부는 공유 DB에서 조건 없는 `DELETE`로 정리한다. `rg -n --hidden 'maxParallelForks|junit[.]jupiter[.]execution[.]parallel' -g '*.gradle.kts' -g '*.gradle' -g 'gradle.properties' -g 'junit-platform.*'`로 검색한 결과 일치 항목은 없었다. 코디네이터의 로컬 실행에서는 같은 DB로 422개가 통과했다. 정리가 모든 클래스 실행 순서에서 안전한지는 확인하지 못했다. 첫 GitHub CI 결과와 CI 재실행 한 번에서 순서 의존 실패가 나타나는지 확인한다.

0단계의 GitHub CI 실행 결과는 PR에서 확인하고 이 계획서에 반영한다(로컬 검증은 위와 같다).
