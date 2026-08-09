# T171-C3 Gateway Control 구현 자체검토

## 대상

- 브랜치: `task_T171-C3-gateway-control`
- 계획: `docs/superpowers/plans/2026-08-10-t171-c3-gateway-control.md`
- 구현 커밋: `b2a204a`
- 계획 진행 커밋: `28f12dd`

## 구현 내용

- `GatewayEventLog`와 `InMemoryGatewayEventLog`를 추가해 event ID 중복 append와 payload hash 충돌을 구분한다.
- `GatewaySessionCursor`와 인메모리/JDBC cursor store를 추가했다.
- ACK는 `highestDelivered` 초과를 거부하고, 중복 ACK는 멱등 처리한다.
- resume 시 `deliveryEpoch`를 증가시키고 이전 epoch/owner ACK를 거부한다.
- C2 `V16`과 충돌하지 않도록 `V17__gateway_control_durable_delivery.sql`을 추가했다.
- `postgres` profile은 JDBC event log/cursor를 사용하고 기본 profile은 인메모리 구현을 사용한다.

## Blueprint Alignment

- Matches blueprint: 부분 일치
- 일치: event log port, payload hash conflict, session cursor monotonicity, epoch/owner guard, V17 migration, profile wiring, RED/GREEN test.
- 미완료: `gateway_user_delivery` materialization과 one-time delivery grant token은 schema만 추가했고 transport/control 후속 범위로 남겼다.
- Correct owner of fix: 후속 T208 transport/control task.

## 검증

| 명령 | 결과 |
|---|---|
| `./gradlew :backend:modules:gateway:test --no-daemon` | PASS |
| `./gradlew :backend:modules:gateway:check --no-daemon` | PASS |
| `./gradlew :backend:boot:compileTestJava --no-daemon` | PASS |
| `git diff --check` | PASS |
| PostgreSQL opt-in 테스트 | 실행 보류: 지정된 JDBC URL이 격리 DB인지 확인되지 않아 공유 DB 변경 위험 |
| 기존 boot 전체 테스트 | 기존 `legacy-auth` 프로필 미활성으로 다수 404 실패; C3 컴파일/모듈 테스트와 별개 |

## 자체 점수

- 총점: 84/100
- 최종평가: 조건부 승인(독립 리뷰 전)
- P0: 0
- P1: 1 — PostgreSQL 실측 검증 증거가 아직 없음
- P2: 1 — user delivery/grant transport 통합은 후속 task

## 잔여 위험 및 다음 조치

1. 격리된 PostgreSQL 인스턴스에서 `V17`, duplicate hash, cursor CAS/제약 테스트를 실행한다.
2. 독립 spec/quality/security review에서 90점 이상·P0/P1 0을 확인한다.
3. 후속 transport task에서 user delivery materialization과 one-time grant를 연결하기 전에는 이 브랜치를 main에 병합하거나 push하지 않는다.
