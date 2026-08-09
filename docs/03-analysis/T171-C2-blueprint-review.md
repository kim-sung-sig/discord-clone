# T171-C2 Blueprint 독립 검수

## 판정

- 결과: **반려**
- Plan Review: **82/100** (기준 85 미달)
- Security Review: **86/100** (기준 90 미달)
- P0/P1/P2: **P0 0 / P1 3 / P2 2**
- Blocking ambiguity: **있음**
- 검수 기준 revision: `a404197`, working tree에 미추적 계획/규칙 문서가 있는 상태에서 대상 blueprint를 정적 검수했다.

## Plan Review Preset

| 범주 | 점수 | 근거 |
|---|---:|---|
| 목표와 범위 | 18/20 | 동기 authz 호출, Redis 권한 세션, JWT 권한 삽입 제외가 명확하다. |
| 계약 완성도 | 20/30 | envelope, inbox, conditional upsert 방향은 맞지만 stale 판정 기준과 projection audience가 비어 있다. |
| 코드 적합성 | 16/25 | 현재 permission/guild 코드를 읽었지만 실제 transaction 소유자와 service dependency 변경 범위가 덜 구체적이다. |
| 장애/운영 검증 | 18/25 | duplicate/out-of-order/DLQ 검증은 있으나 outbox ACK 경계와 rollback flag scope가 구현 명령 수준까지 내려오지 않았다. |
| 합계 | **82/100** | 기준 85 미달 |

Security Review: **86/100**. 권한 비트 자체와 subject/resource 식별자가 민감 projection 데이터인데, 현재 privacy invariant는 message body/token/cookie/header만 금지한다.

## Blocking Findings

### P1 - stale fail-closed가 실제로 판정될 수 없다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:64`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:65`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:138`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:140`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:148`
- 근거: plan은 consumer가 "기존 version보다 작거나 같은 payload"를 no-op 처리한다고만 하고, request-time `can()`이 무엇과 비교해 `version current`를 판단하는지 정의하지 않는다. C0는 `authzVersion`을 보정 게이트에 넣었지만 `docs/03-analysis/T171-C0-wave-acceptance.md:20`, C2 envelope/JWT/read path에는 그 version source 연결이 없다.
- 영향: projection row가 존재하지만 최신 권한 철회 이벤트를 아직 못 받은 서비스는 stale인지 알 수 없어 기존 allow를 계속 허용할 수 있다. 이 경우 plan의 fail-closed 목표가 user-facing 보호 경로에서 성립하지 않는다.
- 요구 수정: `AuthorizationDecision` 입력에 비교 가능한 freshness source를 넣어야 한다. 예: JWT `authzVersion`, guild별 high-water mark table, 또는 source-issued required version 중 하나를 선택하고 missing/behind/unknown을 deny로 명시한다.

### P1 - authz event audience/privacy가 과도하게 넓다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:48`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:60`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:147`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:151`
- 근거: envelope는 `guildId`, `subjectId`, `resourceType`, `resourceId`, `permissionBits`를 전 서비스 공통 topic에 싣는다. Privacy invariant는 message body/token/cookie/header만 금지하고, 권한 비트와 subject/resource 관계 자체의 민감도를 다루지 않는다.
- 영향: Notification, Gateway Control 같은 소비자가 필요 이상의 guild membership/channel visibility projection을 받을 수 있다. 이는 권한 누출 및 내부 lateral access 위험이다.
- 요구 수정: consumer별 최소 projection scope를 정하라. 예: topic ACL, service-specific topic/projection filtering, DLQ metadata에서 subject/resource 식별자 hash 처리 여부, retention, 접근권한을 acceptance 조건으로 추가한다.

### P1 - Guild 저장과 authz outbox append의 원자성이 구현 지점까지 닫히지 않았다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:94`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:102`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:170`, `backend/boot/src/main/java/com/example/discord/guild/PersistentGuildService.java:16`, `backend/boot/src/main/java/com/example/discord/guild/PersistentGuildService.java:18`, `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:67`, `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:74`
- 근거: 현재 `PersistentGuildService`는 in-memory mutation 후 `snapshots.save(guild)`만 호출하고, `JdbcGuildSnapshotStore.save`가 내부에서 connection/transaction을 열고 commit한다. plan의 "동일 owner를 확인"은 outbox append가 같은 JDBC transaction에 들어가는지, `GuildSnapshotStore` API를 바꾸는지, outbox writer를 store 내부로 넣는지 확정하지 않는다.
- 영향: guild 권한 변경 commit 후 outbox append 실패, 또는 outbox append 후 snapshot rollback 같은 split-brain을 막는 구현 경계가 부족하다.
- 요구 수정: `JdbcGuildSnapshotStore.save` transaction 안에서 source version increment와 outbox insert까지 수행하도록 repository/API 변경을 expected files와 tests에 명시한다.

## Non-blocking Findings

### P2 - expected changed files가 현재 service build layout을 덜 반영한다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:160`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:161`, `backend/services/message/build.gradle.kts:12`, `backend/services/message/build.gradle.kts:19`, `backend/services/websocket/build.gradle.kts:12`, `backend/services/websocket/build.gradle.kts:19`
- 근거: message service는 현재 `modules:permission` 의존성이 없고, websocket service도 `modules:permission` 의존성이 없다. Kafka/JDBC consumer 구현에 필요한 dependency/config/migration 위치도 expected files에 없다.
- 요구 수정: 서비스별 `build.gradle.kts`, datasource/Flyway 또는 service-owned migration 위치, Kafka consumer 설정 파일을 expected changed files에 포함한다.

### P2 - `AuthzProjectionRemoved`를 `permissionBits=0`으로만 표현하면 row lifecycle 검증이 모호하다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:68`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:75`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:83`
- 근거: zero-bit tombstone을 유지할지, retention 후 삭제할지, 삭제 후 duplicate replay를 어떻게 처리할지 명시가 없다.
- 요구 수정: tombstone retention과 replay behavior를 acceptance test로 추가한다.

## 승인 조건

반려한다. 재승인 전 최소 수정:

1. request-time stale 판정 source를 하나로 고정하고 fail-closed test를 명시한다.
2. authz projection event/topic/DLQ의 service audience와 민감 식별자 처리 정책을 추가한다.
3. Guild snapshot 저장, `permissionVersion` 증가, authz outbox insert가 같은 DB transaction임을 구현 파일/API 수준으로 확정한다.
4. service build/config/migration expected files를 현재 Gradle layout에 맞춘다.

## 검수 범위

- `docs/01-plan/features/T171-C2-rbac-projection.plan.md`
- `docs/03-analysis/T171-C0-wave-acceptance.md`
- `backend/modules/permission/src/main/java/com/example/discord/permission/*`
- `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`
- `backend/boot/src/main/java/com/example/discord/guild/*`
- `backend/boot/src/main/java/com/example/discord/message/*`
- `backend/boot/src/main/java/com/example/discord/gateway/*`
- `backend/services/message`, `backend/services/websocket`, `backend/services/community`
