# T171-C2 RBAC Projection Spec Compliance Review

## 결론

- 판정: 조건부 합격
- 점수: 91/100
- 결함: P0 0건, P1 0건, P2 2건
- 승인 조건: spec review 관점의 P0/P1은 없다. 남은 P2는 운영/테스트 증거 보강 사항이며, merge gate에서는 별도 quality/security review와 선언된 verification evidence가 함께 필요하다.

## 검토 범위

- 기준 문서: `docs/01-plan/features/T171-C2-rbac-projection.plan.md`
- 검토 대상: 현재 working tree의 C2 uncommitted 구현
- 중점 항목: outbox transaction/watermark 순서, envelope/topic, Message/WebSocket local projection 경로, inbox dedup/stale fail-closed

## 확인된 준수 항목

- `JdbcGuildSnapshotStore.save(guild)`는 guild snapshot 저장, permission version 증가, projection outbox append, watermark append를 같은 JDBC transaction 안에서 수행한다.
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:73`
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:79`
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:80`
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:82`
- audience별 resource selector가 추가되어 `MESSAGE`, `WEBSOCKET`, `GATEWAY`는 `CHANNEL`, `NOTIFICATION`은 `GUILD` row만 outbox에 기록한다.
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:168`
  - 근거: `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:192`
  - 근거: `backend/boot/src/test/java/com/example/discord/guild/PostgresGuildServiceTest.java:178`
- envelope contract는 최신 plan의 `kind`/`source` 필드를 반영하며 watermark payload null 규칙을 value object에서 검증한다.
  - 근거: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:48`
  - 근거: `backend/modules/permission/src/main/java/com/example/discord/permission/AuthorizationProjectionEventEnvelope.java:8`
  - 근거: `backend/modules/permission/src/main/java/com/example/discord/permission/AuthorizationProjectionEventEnvelope.java:38`
  - 근거: `backend/modules/permission/src/main/java/com/example/discord/permission/AuthorizationProjectionEventEnvelope.java:45`
- boot Message publish/read와 Gateway delivery는 `discord.authz.projection-enabled=false` 기본값에서 기존 경로로 fallback하고, true일 때 projection decision을 사용한다.
  - 근거: `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java:132`
  - 근거: `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java:136`
  - 근거: `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java:169`
  - 근거: `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java:191`
- message/websocket service store는 inbox insert와 projection/watermark update를 `@Transactional` method에 묶고, projection upsert는 SQL `WHERE authorization_projection.permission_version < EXCLUDED.permission_version` 조건을 사용한다.
  - 근거: `backend/services/message/src/main/java/com/example/discord/messageservice/JdbcAuthorizationProjectionStore.java:29`
  - 근거: `backend/services/message/src/main/java/com/example/discord/messageservice/JdbcAuthorizationProjectionStore.java:31`
  - 근거: `backend/services/message/src/main/java/com/example/discord/messageservice/JdbcAuthorizationProjectionStore.java:39`
  - 근거: `backend/services/websocket/src/main/java/com/example/discord/websocketservice/JdbcAuthorizationProjectionStore.java:29`
  - 근거: `backend/services/websocket/src/main/java/com/example/discord/websocketservice/JdbcAuthorizationProjectionStore.java:38`
- outbox relay는 같은 `(guild_id, audience)`의 미발행 predecessor가 있을 때 후보를 claim하지 않도록 보완되어, 같은 version의 projection row가 모두 published 된 뒤 watermark row가 선택되는 구조가 됐다.
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:69`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:74`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:77`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:79`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:81`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:84`
  - 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:85`

## P1

없음.

### 해소 확인: watermark-last relay ordering

이전 P1은 relay가 `claimed_until` 상태인 projection row를 건너뛰고 watermark를 먼저 claim할 수 있다는 점이었다. 최신 SQL은 candidate별로 같은 `(guild_id, audience)` 안의 미발행 predecessor를 검사한다. predecessor 조건은 낮은 `permission_version` 전체와, 같은 version에서 `PROJECTION_UPDATED`가 남아 있는 동안 `WATERMARK_ADVANCED`를 막는 조건을 포함한다.

- 기준: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:75`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:74`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:80`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:81`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:83`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:84`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:85`

판정: P1 해소.

## P2

### P2-1. DLQ metadata가 plan의 subject/resource masking 및 7일 보존 계약까지 검증하지 않는다

구현은 raw payload를 보관하지 않고 truncated payload hash만 남긴다. 비밀 본문 저장 방지는 충족하지만, plan은 subject/resource를 truncated SHA-256으로 마스킹하고 DLQ 보존 기간 7일을 요구한다.

- 근거: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:72`
- 근거: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:164`
- 근거: `backend/modules/permission/src/main/java/com/example/discord/permission/AuthorizationProjectionDeadLetter.java:7`
- 근거: `backend/modules/permission/src/main/java/com/example/discord/permission/AuthorizationProjectionDeadLetter.java:16`
- 근거: `backend/modules/permission/src/test/java/com/example/discord/permission/AuthorizationProjectionDeadLetterTest.java:9`

수정안:

- parse 가능한 malformed/invalid event는 `eventId`, `audience`, `subjectHash`, `resourceHash`, `reason` 같은 metadata-only DLQ record로 남긴다.
- Kafka DLQ topic retention 168h 설정 또는 운영 runbook/contract test를 추가한다.

### P2-2. Relay/JDBC failure drill evidence는 acceptance packet에 명시적으로 묶어야 한다

구현 코드는 watermark-last 조건을 만족하도록 보완됐다. 다만 최신 plan의 runtime gate에는 Kafka unavailable, projection DB unavailable, stale deny, rollback flag, duplicate/out-of-order/replay/failure drill이 포함된다. 이 리뷰어가 직접 재실행한 것은 focused JVM test이며, 사용자가 제공한 isolated Postgres outbox test 통과 evidence는 최종 acceptance packet에 명령/대상 SHA/결과로 고정되어야 한다.

- 근거: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:197`
- 근거: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:199`
- 근거: `backend/services/message/src/test/java/com/example/discord/messageservice/AuthorizationProjectionConsumerTest.java:66`
- 근거: `backend/services/websocket/src/test/java/com/example/discord/websocketservice/AuthorizationProjectionConsumerTest.java:54`
- 근거: `backend/boot/src/test/java/com/example/discord/guild/PostgresGuildServiceTest.java:146`
- 근거: `backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:74`

수정안:

- isolated Postgres outbox test의 명령, env, 대상 commit/working-tree revision, 성공 결과를 acceptance evidence에 기록한다.
- message/websocket JDBC store는 stale unique event가 projection을 덮지 않는 SQL-level test evidence를 acceptance packet에 포함한다.
- rollback flag는 projection disabled/enabled 양쪽 Message read/publish와 Gateway delivery evidence를 함께 묶는다.

## Verification

실행 명령:

```bash
./gradlew :backend:modules:permission:test :backend:modules:gateway:test :backend:services:message:test :backend:services:websocket:test :backend:boot:test --tests com.example.discord.message.MessageConfigurationTest
```

결과: 성공.

주의: 위 명령은 focused JVM test다. 사용자가 제공한 isolated Postgres boot test 통과 evidence는 이 리뷰에서 코드 근거와 테스트 파일로 확인했지만, 본 리뷰어가 별도 Postgres runtime을 직접 재실행하지는 않았다.
