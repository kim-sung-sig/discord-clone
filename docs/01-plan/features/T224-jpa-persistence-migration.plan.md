# T224 저장 계층 JPA 전환 계획

- 상태: 방향 결정됨: 트랜잭션 AOP 우선 + 아키텍처 점진 전환(ADR T230). JPA 전면 전환은 하지 않으며 도구 결정은 파일럿 뒤.
- 요약
  - 10월 조사는 connection pool 부재, 조회 N+1, SQL 중복을 확인했다(8절). connection pool은 T226에서 완료했다.
  - 선언적 트랜잭션 전환은 T230에서 진행하며 조회 개선은 T224-1~2에 남긴다.
- 범위: 백엔드(`backend/boot`, `backend/services/*`)만 다룬다. 프런트엔드는 해당이 없다.
- 조사 기록: 2026-10-30 저장 계층 조사 결과를 8절에 보존한다.

## 1. 현재 상태 (2026-11-28, main 기준)

| 항목 | 값 |
| --- | --- |
| ORM | 없음. JPA, Hibernate, jOOQ, QueryDSL 모두 쓰지 않는다. |
| SQL 실행 방식 | 손으로 쓴 SQL 문자열에 `DataSource`와 `PreparedStatement`를 쓴다. boot·services의 `Jdbc*` 구현은 21개, 5,345줄이다. |
| 스키마 관리 | Flyway. boot 21개, services/message 2개, services/websocket 1개 migration이 있고, table은 약 61개다. |
| 트랜잭션 | 수동 `setAutoCommit(false)`는 13개 클래스, 22곳이다. |
| Postgres 전용 문법 | `ON CONFLICT` upsert 35곳(멱등 처리), `FOR UPDATE` 10곳, `FOR UPDATE SKIP LOCKED` 3곳(outbox 임대), `RETURNING` 9곳, `jsonb` 2곳 |
| 대체 구현 | 도메인 모듈의 `InMemory*` 구현은 21개, 3,301줄이다. 많은 저장소가 in-memory 구현과 Postgres 구현을 profile(`postgres`)로 바꿔 쓴다. 기본·test profile은 in-memory다. |
| 계약 시험 | `DISCORD_RUN_POSTGRES_TESTS=true`일 때만 도는 Postgres 계약 시험이 있다(예: `JdbcMessageStoreTest`, `PostgresGuildStoreContractTest`). |
| 모듈 경계 | `ArchitectureTest.backendModulesDoNotImportSpringOrJakartaFrameworks`가 `backend/modules/*`의 Spring·Jakarta import를 금지한다. JDBC 구현은 boot·services에 있다. |

아래 저장소별 규모와 PostgreSQL 전용 문법 사용량은 10월 boot 조사 기록이다. 위 표에서 구현 규모와 트랜잭션 외의 수치도 10월 기준이다.

| 저장소 | 줄 | upsert | `FOR UPDATE` | `SKIP LOCKED` |
| --- | ---: | ---: | ---: | ---: |
| `message/JdbcMessageStore` | 885 | 1 | 0 | 0 |
| `guild/JdbcGuildStore` | 533 | 7 | 2 | 0 |
| `auth/JdbcAuthStore` | 514 | 3 | 0 | 0 |
| `notification/JdbcNotificationInboxService` | 431 | 2 | 0 | 0 |
| `moderation/JdbcModerationService` | 420 | 0 | 2 | 0 |
| `event/JdbcServerEventService` | 347 | 2 | 0 | 0 |
| `message/JdbcMessagePublicationOutbox` | 325 | 0 | 1 | 1 |
| `social/JdbcDirectMessageService` | 302 | 4 | 0 | 0 |
| `gateway/JdbcGatewayEventLog` | 254 | 0 | 0 | 0 |
| `gateway/JdbcGatewaySessionCursorStore` | 214 | 1 | 1 | 0 |
| `experience/JdbcEntitlementStore` | 156 | 1 | 0 | 0 |
| `presence/JdbcChannelReadService` | 120 | 1 | 0 | 0 |
| `thread/JdbcThreadService` | 117 | 0 | 0 | 0 |
| `invite/JdbcInviteService` | 116 | 0 | 1 | 0 |
| `authorization/JdbcAuthorizationProjectionStore` | 80 | 3 | 0 | 0 |
| `expression/JdbcExpressionService` | 75 | 1 | 0 | 0 |
| `message/JdbcMessagePublicationInbox` | 38 | 1 | 0 | 0 |

## 2. 왜 바꾸려 하는가

- 같은 SELECT 열 목록과 `ResultSet` 변환 코드가 저장소마다 되풀이된다. 열 하나를 추가하면 여러 곳을 함께 고쳐야 한다.
- 수동 트랜잭션(`setAutoCommit`, commit, rollback, close)이 업무 코드와 섞여 들여쓰기와 catch 블록이 깊다.
- 조회 화면이 늘수록 조건을 붙여 가며 SQL 문자열을 조립하게 되어, 실수와 주입 위험이 커진다.
- 단순 CRUD(초대, 스레드, 표현, 서버 이벤트, 권한 부여 등)까지 SQL을 손으로 쓰는 비용이 크다.

## 3. 그대로 두는 편이 나은 곳 (전환 반대 근거)

> 아래 근거는 10월 조사 에이전트의 결론이며 재검증하지 않았다. 핵심 제약인 도메인 모듈의 프레임워크 import 금지는 JPA 사용 자체를 막지 않는다. 저장용 entity를 모듈 외부에 둘 때 entity와 도메인 모델 사이의 변환 비용이 생긴다.

전부 JPA로 옮기면 오히려 위험한 부분이 있다. 이유는 다음과 같다.

- **outbox·inbox 임대**(`FOR UPDATE SKIP LOCKED`, 임대 시간·세대 번호): JPA로 표현하면 native query가 된다. 장애 재현 시험(T216)으로 검증된 동작을 다시 증명해야 한다.
- **멱등 upsert 35곳**(`ON CONFLICT`): JPA에는 upsert가 없다. "조회 후 insert"로 바꾸면 경합 때 중복 키 예외가 나므로, 결국 native query가 필요하다.
- **T212 잠금 경합 설계**: moderation·guild 행 잠금 순서와 범위를 의도해서 정했다. ORM의 지연 로딩과 자동 flush가 끼어들면 잠금 순서가 바뀔 수 있다.
- **Gateway 이벤트 기록**과 **권한 projection**: append·projection 성격이라 entity 모델이 잘 맞지 않는다.
- **순수 도메인 모듈 규칙**: entity에 JPA annotation을 붙이면 `backend/modules/*`에 framework가 들어온다. 도메인 record와 저장용 entity를 따로 두어야 하고, 그만큼 변환 코드가 생긴다.

## 4. 제안: 혼합 방식 (결정 필요)

> 4~7절은 10월 당시의 대안과 조사 과제다. 현재 결정과 실행 계획은 [T230 ADR](../../02-design/features/T230-architecture-direction.adr.md)과 [T230 계획](T230-declarative-transactions.plan.md)을 따른다.

| 선택지 | 내용 | 판단 |
| --- | --- | --- |
| A. 전부 JPA | 모든 저장소를 Spring Data JPA로 옮긴다. upsert·잠금은 native query로 처리한다. | 비추천. 위험한 곳까지 다시 증명해야 하고, native query가 많이 남아 얻는 것이 적다. |
| B. **혼합(추천)** | 단순 CRUD와 집합 단위 쓰기는 JPA로 옮긴다. 조건이 많은 조회는 QueryDSL을 쓴다(필요하면 Blaze-Persistence). outbox·inbox·upsert 중심·Gateway 기록은 JDBC로 남기되 `JdbcClient`로 정리한다. | 위험한 곳은 두고, 손이 많이 가는 곳만 줄인다. |
| C. JPA 없이 정리 | 전부 JDBC로 두고 `JdbcClient`, 공통 mapper, SQL 상수로 중복만 없앤다. 조회만 jOOQ나 QueryDSL-SQL을 쓴다. | 변경이 가장 작다. 다만 "JPA로 가자"는 방향과 다르다. |

## 5. 해야 할 일 (TODO)

### 0단계: 결정과 사전 확인
- [ ] 선택지 A/B/C 중 하나를 정한다. 사용자가 결정한다.
- [ ] Spring Boot 4.1이 기본으로 쓰는 Hibernate 버전(7.x 예상, 미확인)과 Jakarta Persistence 버전을 확인한다.
- [ ] QueryDSL을 확인한다. 원조 5.x는 유지보수가 멈춰 있어 OpenFeign fork(`io.github.openfeign.querydsl`)의 Hibernate 7 지원과 annotation processor 설정을 봐야 한다. 미확인이다.
- [ ] Blaze-Persistence가 Hibernate 7을 지원하는지 확인한다. 미확인이다. 지원하지 않으면 QueryDSL만 쓴다.
- [ ] 저장 계층 조사 결과(중복 SQL, N+1, 동적 조회 위치)로 조회 경로 목록을 확정한다.
- [ ] 이 결정을 ADR로 남긴다(`docs/02-design/features/T224-persistence-strategy.adr.md`).

### 1단계: 기반 (작업 하나)
- [ ] `spring-boot-starter-data-jpa`와 QueryDSL 의존성을 추가한다. 버전은 Spring Boot BOM을 따른다.
- [ ] JPA 설정
  - `ddl-auto=validate`: 스키마 주인은 Flyway로 둔다.
  - `open-in-view=false`
  - 명명 규칙: 기존 snake_case와 맞춘다.
  - `jdbc.time_zone=UTC`
  - batch size 설정
- [ ] 트랜잭션 관리자 하나로 JPA와 남는 JDBC 코드가 같은 트랜잭션을 쓰는지 확인한다(`JpaTransactionManager`와 같은 `DataSource`).
- [ ] entity 위치 규칙을 정한다. entity는 boot의 `persistence` 쪽에 두고, 도메인 record로 바꾸는 변환 규칙을 정한다. 순수 도메인 모듈 규칙은 지킨다.
- [ ] in-memory 대체 구현의 처리 방침을 정한다. 계속 둘지, test를 Testcontainers Postgres로 옮기고 지울지 정한다.
- [ ] ArchitectureTest에 규칙을 더한다. 예: "도메인 모듈에 `jakarta.persistence` 금지"

### 2단계: 파일럿 (작업 하나)
- [ ] 작고 잠금이 단순한 저장소 하나를 옮긴다. 후보는 `thread/JdbcThreadService`(117줄, upsert·잠금 없음)나 `invite/JdbcInviteService`(116줄, `FOR UPDATE` 1개)다.
- [ ] 기존 Postgres 계약 시험이 그대로 통과하는지 확인한다. 바뀐 SQL 수와 N+1이 없는지도 확인한다(Hibernate statistics 또는 query count 시험).
- [ ] 결과를 보고 2단계 이후의 형식을 확정한다. entity·repository·변환 규칙, 시험 방식이다.

### 3단계: 도메인별 전환 (도메인마다 작업 하나, 작은 것부터)
- [ ] `expression`(75줄)
- [ ] `experience/JdbcEntitlementStore`(156줄)
- [ ] `event/JdbcServerEventService`(347줄)
- [ ] `social/JdbcDirectMessageService`(302줄, upsert 4개: native 유지 여부 판단)
- [ ] `notification/JdbcNotificationInboxService`(431줄, 조회가 많음: QueryDSL 적용 1순위)
- [ ] `moderation/JdbcModerationService`(420줄, T212 잠금 설계 보존 필수)
- [ ] `guild/JdbcGuildStore`(533줄, upsert 7개, 잠금 2개)
- [ ] `auth/JdbcAuthStore`(514줄, 세션·토큰 회전·`findProfilesByIds`)
- [ ] `message/JdbcMessageStore`(885줄, 발행 멱등성과 조회 page): 마지막에 한다. 조회만 QueryDSL로 옮길지 판단한다.

각 작업의 공통 완료 조건은 다음과 같다.
- 기존 Postgres 계약 시험이 통과한다.
- 수동 트랜잭션을 `@Transactional`로 옮긴다.
- 조회 경로에 N+1이 없다.
- 쓰기 경로는 가독성과 중복 제거만 본다. 최적화는 하지 않는다(사용자 기준).

### 4단계: JDBC로 남기는 것 정리 (작업 하나)
- [ ] `JdbcMessagePublicationOutbox`, `JdbcMessagePublicationInbox`, `JdbcGatewayEventLog`, `JdbcGatewaySessionCursorStore`, `JdbcAuthorizationProjectionStore`, `JdbcChannelReadService`
- [ ] 위 저장소를 `JdbcClient`, 공통 row mapper, SQL 상수로 정리한다. 동작은 바꾸지 않는다.
- [ ] `backend/services/*`에 복사된 `JdbcAuthorizationProjectionStore` 3벌을 공통 모듈로 모을지 판단한다.

### 5단계: 마무리
- [ ] in-memory 대체 구현을 정리한다(1단계 방침에 따라).
- [ ] 위키(Backend Architecture)와 `backend/AGENTS.md`에 저장 계층 규칙을 반영한다.
- [ ] 성능을 비교한다. 메시지 목록·알림함 조회의 응답 시간과 SQL 수를 전환 전후로 잰다.

## 6. 위험과 확인할 것

- **Spring Boot 4.1 / Hibernate 7 / QueryDSL 호환성**: 아직 확인하지 않았다. 0단계에서 확인하지 않고 시작하지 않는다.
- **잠금 순서 변화**: 자동 flush 시점 때문에 T212가 막은 경합이 되살아날 수 있다. 잠금이 있는 저장소는 기존 경합 시험을 반드시 통과해야 한다.
- **멱등성**: upsert를 "조회 후 insert"로 바꾸면 경합 때 중복이 생긴다. 멱등이 필요한 곳은 native upsert를 유지한다.
- **변환 코드 증가**: entity와 도메인 record를 따로 두면 변환 코드가 생긴다. 파일럿에서 비용을 잰다.
- **MSA 서비스**: `backend/services/*`는 DB가 따로다. boot와 같은 방식으로 옮길지 따로 정한다.

## 7. 결정 받을 질문

1. A(전부 JPA), B(혼합, 추천), C(JDBC 정리만) 중 어느 쪽인가?
2. 조건이 많은 조회 도구로 QueryDSL만 쓸지, Blaze-Persistence도 쓸지?
3. in-memory 대체 구현을 계속 둘지, test를 Postgres(Testcontainers)로 통일할지?
4. 파일럿은 `thread`와 `invite` 중 어느 쪽으로 할지?

## 8. 조사 결과 반영 (2026-10-30, 저장 계층 조사 에이전트)

### 새로 확인한 사실
- **Spring Data와 `@Query`가 아예 없다.** 그래서 "쿼리 어노테이션 과다"는 이 코드에 해당하지 않는다. 문제는 손으로 쓴 SQL이 흩어진 데 있다.
- **connection pool이 없다.**
  - `persistence/PostgresPersistenceConfiguration.java:17-26`이 `PGSimpleDataSource`를 쓴다. 그래서 `getConnection()`을 부를 때마다 DB 연결을 새로 맺는다. 호출은 약 105곳이다.
  - JPA와 상관없이 **가장 먼저 고칠 것**이다(Hikari로 교체).
  - `services/message`와 `services/websocket`이 Hikari를 자동 구성하는지는 아직 확인하지 않았다.
- **SQL 작성 방식은 위험하지 않다.** text block과 위치 기반 `?`를 쓰고, 사용자 입력을 SQL 문자열에 이어 붙이는 곳은 없었다. 주입 위험은 없다.
  - 동적 조건은 약 3곳뿐이다: 메시지 cursor, 알림 표시 조건, 감사 기록 필터.
  - `JdbcMessageStore:234`, `:292`의 `LIKE '%'+q+'%'`는 `%`와 `_`를 escape하지 않는다(P3).
- **조회 경로 N+1**
  - 메시지 page와 검색: id만 조회한 뒤 메시지마다 `findById`를 부르고, 그때 멘션과 수정 기록을 따로 읽는다(`JdbcMessageStore:474-485`, `:353-370`). 100개짜리 page 하나에 쿼리가 약 200번 나간다.
  - 서버 채널: 채널마다 권한 덮어쓰기를 따로 조회한다(`JdbcGuildStore.loadChannels:377-402`).
  - 서버 이벤트: 행마다 관심 멤버를 따로 조회한다(`JdbcServerEventService.visibleEvents:128-153`). 전역 조회 `signals()`에는 제한이 없다.
  - 알림함: `inbox()`에 `LIMIT`이 없다(`JdbcNotificationInboxService:91-111`).
- **index를 못 쓰는 조회**
  - cursor 비교가 `id::text < ?`라서 index를 쓰지 못한다(`JdbcMessageStore:417`, `:460`).
  - `readPage`는 `created_at`으로 정렬하는데, index는 `(channel_id, sequence)`에만 있다.
- **중복**
  - 메시지 조회용 SELECT 열 목록과 `(SELECT sequence ...)` subquery가 3~4번 복사돼 있다.
  - 알림 표시 조건 10줄이 2번 복사돼 있다.
  - `JdbcAuthorizationProjectionStore`가 3벌, `JdbcMessagePublicationOutbox`가 2벌 있고, 서로 내용이 달라졌다.
- **예외 처리**
  - `IllegalStateException("failed …")` 감싸기가 101곳이다.
  - 중복 키(SQLState `23505`)를 손으로 판별하는 곳이 4곳이다.
  - 같은 "찾을 수 없음"이 guild·moderation에서는 `IllegalArgumentException`이라 400이 되고, 다른 도메인에서는 404가 된다.

### 조사 에이전트의 권고
- **(a) JDBC 유지 + `JdbcClient` 정리**를 권한다. 공통 mapper, SQL 열 상수, `@Transactional`/`TransactionTemplate`, Spring 예외 변환, Hikari pool로 정리하는 방식이다.
- 근거: 아픈 곳 대부분이 N+1, 중복, pool 부재다. 이것들은 쿼리 도구를 바꾼다고 저절로 낫지 않는다.
- (b) 조회 경로에만 jOOQ를 쓰는 안은, (a) 뒤에 선택 사항으로 둔다. QueryDSL-SQL보다 jOOQ가 유지보수 면에서 낫다.
- (c) JPA(+Blaze/QueryDSL)는 이 코드와 맞지 않는다고 봤다. 이유는 다음과 같다.
  - entity가 없고, 도메인이 불변 record와 aggregate다.
  - Postgres 전용 문법(`ON CONFLICT`, `SKIP LOCKED`, CTE `UPDATE … RETURNING`, `ANY(?)`, `REPEATABLE READ READ ONLY`)이 많다.
  - `JdbcGuildStore`는 aggregate 전체를 다시 읽고 차이만 저장하는 방식이다.

### 이 계획에 미치는 영향 (코디네이터 의견)
- 4절의 선택지 평가를 다음처럼 다시 본다. **C(JDBC 정리)를 1순위로 하고, B(혼합)는 C를 끝낸 뒤 다시 판단한다.**
- 어느 선택지든 **먼저 할 일은 같다**. 아래 "공통 선행 작업"이다. JPA로 가더라도 버려지는 일이 아니다.
- 그래도 JPA로 가기로 하면, 5절 TODO 순서대로 진행한다. 0단계 스파이크에서 Hibernate 7·QueryDSL 호환과 entity 변환 비용을 숫자로 확인한 뒤 결정을 다시 본다.

### 공통 선행 작업 (선택지와 상관없이 먼저)
- [x] **T224-0** connection pool: T226(PR #24)으로 완료.
- [ ] **T224-1** 메시지 조회 N+1 제거
  - 멘션·수정 기록을 `= ANY(?)`로 한 번에 읽는다.
  - cursor를 `(created_at, id) < (?, ?::uuid)`로 바꾸고, 정렬에 맞는 index를 추가한다(Flyway).
  - 쓰지 않는 `ChannelMessagePagePort`와 `ChannelMessageSearchPort`를 정리한다.
- [ ] **T224-2** 서버 채널·서버 이벤트·알림함 조회 N+1 제거와 `LIMIT` 추가
- [ ] **T224-3** 수동 트랜잭션의 선언적 처리: [T230으로 이관](T230-declarative-transactions.plan.md). 예외 변환과 "찾을 수 없음" 예외 통일은 기존 조사 과제로 남긴다.
- [ ] **T224-4** 복사된 저장소(`JdbcAuthorizationProjectionStore` 3벌, outbox 2벌)를 `shared/common`으로 모은다.
