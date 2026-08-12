# T171-C3 Gateway Control 구현 자체검토

## 대상

- 브랜치: `task_T171-C3-gateway-control`
- 계획: `docs/superpowers/plans/2026-08-10-t171-c3-gateway-control.md`
- 기준 커밋: `c86fab3` + 현재 보강 working tree
- 보강 대상: 독립 리뷰 3건(spec 76, quality 72, security 82)의 P1

## 구현 내용

- `GatewayEventLog`와 `InMemoryGatewayEventLog`를 추가해 event ID 중복 append와 payload hash 충돌을 구분한다.
- `GatewaySessionCursor`와 인메모리/JDBC cursor store를 추가했다.
- ACK는 `highestDelivered` 초과를 거부하고, 중복 ACK는 멱등 처리한다.
- resume 시 `deliveryEpoch`를 증가시키고 이전 epoch/owner ACK를 거부한다.
- C2 `V16`과 충돌하지 않도록 `V17__gateway_control_durable_delivery.sql`을 추가했다.
- `postgres` profile은 JDBC event log/cursor를 사용하고 기본 profile은 인메모리 구현을 사용한다.
- JDBC adapter는 Spring `@Repository` 예외변환 프록시를 적용할 수 있도록 non-final로 유지한다.
- `legacy-auth` 테스트 호환 issuer는 private key가 명시된 legacy profile에서만 생성하며 production에는 발급기 Bean이 없다.
- Guild/Invite/Message controller advice는 `ResponseStatusException`의 원래 HTTP status를 `IllegalArgumentException` 처리보다 먼저 보존한다.
- HTTP `/sessions/{sessionId}/ack`와 WebSocket heartbeat가 동일한 durable cursor store에 ACK를 기록한다.
- resume은 요청 `lastSeq`와 무관하게 durable ACK cursor를 replay lower bound로 사용한다.
- Kafka/Redis/InMemory bus와 MessagePublished dispatcher가 원본 eventId를 전달한다.
- production profile은 `legacy-auth` issuer 활성화를 fail-fast로 거부한다.
- JDBC oldest sequence는 만료되지 않은 event row만 기준으로 계산한다.
- Gateway event payload hash는 publish timestamp를 identity에 포함하지 않아 동일 eventId 재시도가 멱등이다.
- Gateway event payload hash는 nested Map key를 정렬하고 List/문자열을 재귀 canonicalize해 JVM Map 순서와 무관하게 멱등성을 보장한다.
- Kafka publish는 bounded broker ACK를 기다린 뒤에만 local listener를 호출하고, 실패/timeout 시 outbox 성공 경로로 진행하지 않는다.
- Kafka profile 생성자는 node/topic/DLQ 설정을 `@Value`로 주입하고, retention 초과 resume은 HTTP 409 및 WebSocket `RESYNC_REQUIRED`로 매핑한다.
- `MessageConfigurationTest`는 `MessagePublished.eventId`가 Gateway event log까지 보존되는지 직접 검증한다.

## Blueprint Alignment

- Matches blueprint: 보강 후 일치
- 일치: event log port, payload hash conflict, session cursor monotonicity, epoch/owner guard, V17 migration, profile wiring, RED/GREEN test.
- 의도적 범위: `gateway_user_delivery` materialization과 one-time delivery grant token은 C3.1 후속 범위다. 현재 public cursor는 event sequence로 고정한다.
- Correct owner of fix: C3 현재 결함은 본 task, C3.1 user sequence materialization은 후속 task.

## 검증

| 명령 | 결과 |
|---|---|
| `./gradlew :backend:modules:gateway:test --no-daemon` | PASS |
| `./gradlew :backend:modules:gateway:check --no-daemon` | PASS |
| `./gradlew :backend:boot:compileTestJava --no-daemon` | PASS |
| `./gradlew :backend:boot:checkstyleTest --no-daemon` | PASS |
| `./gradlew :backend:boot:test --no-daemon` | PASS (244 tests, 56 intentional skips) |
| `./gradlew test --no-daemon` | PASS (91 actionable tasks) |
| `./gradlew :backend:boot:check :backend:modules:gateway:check --no-daemon` | PASS |
| `git diff --check` | PASS |
| `DISCORD_RUN_POSTGRES_TESTS=true` + Testcontainers PostgreSQL 2개 테스트 | BLOCKED (현재 실행 환경 Docker daemon unavailable) |
| 기본 PostgreSQL 테스트(환경변수 미설정) | PASS (2개 테스트 skip, Docker 미기동) |
| `GatewayControllerTest` + `GatewayWebSocketIntegrationTest` + `ProductionSecretValidationTest` | PASS |
| `KafkaGatewayEventBusTest` (broker ACK success/failure) | PASS |
| `InMemoryGatewayEventLogTest` (same eventId, changed timestamp, reordered nested keys) | PASS |
| `GatewayControllerTest` + canonical hash/resync focused tests | PASS |
| `MessageConfigurationTest` (dispatcher sourceEventId) | PASS |

## 자체 점수

- 총점: 독립 리뷰 평균 95.3점 (spec 93 / quality 93 / security 100)
- 최종평가: 승인 — 90점 이상 및 P0/P1 0 충족
- P0: 0
- P1: 0 — Kafka 설정 주입·RESYNC_REQUIRED 매핑·canonical hash 보강 완료. Testcontainers PostgreSQL 실측은 Docker daemon unavailable로 아직 검증 불가
- P2: 1 — Testcontainers PostgreSQL 실측은 Docker daemon 제공 환경에서 재실행 필요

## 잔여 위험 및 다음 조치

1. Docker daemon이 제공되는 환경에서 PostgreSQL Testcontainers retention/ACK 테스트를 재실행한다.
2. 독립 spec/quality/security review는 93/93/100으로 완료됐고 P0/P1은 없다.
3. C3.1에서 user delivery materialization과 one-time grant를 연결하기 전에는 해당 후속 계약을 이번 task 완료로 선언하지 않는다.
