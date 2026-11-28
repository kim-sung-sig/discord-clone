# T230 저장 계층 아키텍처 방향 ADR

- 상태: 결정됨(사용자, 2026-11-28)
- 작성일: 2026-11-28
- 범위: `backend/modules/*`, `backend/boot`, `backend/services/*`의 저장 계층과 트랜잭션 경계

## 배경

수동 트랜잭션을 먼저 `@Transactional`로 옮긴다. JPA 전면 전환은 하지 않으며, JdbcClient·MyBatis·JPA 중 어떤 도구를 쓸지는 작은 저장소 파일럿 뒤 결정한다.

2026-11-28 `main` 기준 측정 결과는 다음과 같다.

| 항목 | 측정값 |
| --- | --- |
| 도메인 모듈의 `InMemory*` 구현 | 21개, 3,301줄 |
| boot·services의 `Jdbc*` 구현 | 21개, 5,345줄 |
| 수동 트랜잭션 (`setAutoCommit(false)`) | 13개 클래스, 22곳 |
| 기존 `@Transactional` 적용 | `JdbcAuthorizationProjectionStore` 3벌과 `AuthorizationProjectionOutboxRelay`. 모두 `JdbcTemplate` 사용 |
| 테스트 의존성 | 테스트 153개 중 26개가 `InMemory`에 의존하고, PostgreSQL 테스트 파일 26개는 `DISCORD_RUN_POSTGRES_TESTS=true`에서만 실행 |

PR CI의 `backend` job은 이미 `postgres:17`과 접속 환경 변수를 제공한다. 테스트 실행 조건인 `DISCORD_RUN_POSTGRES_TESTS`가 빠져 있어 PostgreSQL 테스트는 건너뛴다.

## 결정

1. **도메인 모듈의 프레임워크 import 금지에 좁은 예외를 허용한다.** 첫 예외는 `org.springframework.transaction.annotation.Transactional`이다. 해당 어노테이션이 모듈에서 처음 필요해지는 작업에서 `ArchitectureTest.backendModulesDoNotImportSpringOrJakartaFrameworks`를 완화한다. 필요한 경계에서 선언적 트랜잭션을 쓸 수 있는 대신 Spring 의존이 들어오고, 이후 예외의 범위를 검토해야 한다. 도메인 모듈에 JPA entity를 둘지는 파일럿 뒤 결정한다.
2. **새 코드에서는 저장소 port와 `InMemory*` 구현을 쌍으로 만들지 않는다.** 기존 쌍은 저장소를 전환할 때 PostgreSQL 테스트가 그 동작을 검증하는지 확인한 뒤 제거한다. 동일한 동작을 두 구현에서 관리하는 비용을 줄일 수 있다. 대신 해당 동작을 확인하는 데 PostgreSQL 실행 환경과 DB 테스트 관리가 필요하다.
3. **컨텍스트 사이의 port를 유지한다.** Message의 `MessageAuthorDirectory`처럼 호출하는 컨텍스트가 자기 언어로 경계를 정의한다. [CONTEXT-MAP.md](../../../CONTEXT-MAP.md)의 다른 컨텍스트 테이블 직접 join 금지 규칙과 [T171-C0 MSA 분리 결정](T171-C0-msa-target-state.adr.md)의 데이터 소유권을 지킨다. 경계에서 계약과 데이터 변환을 관리하는 비용은 계속 든다.

인가 판정, 상태 변경과 outbox 기록의 원자성, inbox 처리와 projection 반영의 원자성, 원본 `eventId` 보존은 전환 중에도 유지한다.

## 고려한 다른 안

- **전면 레이어드 전환**은 한 번에 많은 경계를 재구성해야 한다. 인가와 이벤트 처리의 회귀를 검증할 범위가 넓어지고, 컨텍스트별 데이터 소유권을 지키는 비용도 커진다.
- **구조 유지와 트랜잭션만 AOP 적용**은 초기 변경이 작지만 저장소 port와 `InMemory*` 쌍의 관리 비용이 계속 든다. PostgreSQL 검증을 확보한 저장소부터 점진적으로 줄이는 쪽을 선택했다.

## 기존 결정의 변경 범위

ARCH01의 보존 결정은 “메모리/JDBC adapter가 둘 다 있는 seam은 단일 구현이라는 이유로 제거하지 않는다”였다. 이 보존 결정에서 바뀌는 범위는 결정 2의 저장소 port와 `InMemory*` 쌍뿐이다. 전환하는 저장소의 PostgreSQL 테스트가 해당 동작을 덮는 경우에만 제거한다.

## 후속 작업

[T230 선언적 트랜잭션 전환 계획](../../01-plan/features/T230-declarative-transactions.plan.md)에서 CI 게이트, 트랜잭션 기반, 파일럿, 묶음 전환의 순서와 완료 기준을 정한다.
