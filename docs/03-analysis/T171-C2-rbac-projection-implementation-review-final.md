# T171-C2 RBAC Projection 구현 최종 검수

## 판정

- 결과: **승인(조건부) / 93점**
- P0: 0건
- P1: 0건
- P2: 2건
- 검수 기준: 90점 이상이며 P0/P1이 없어야 task branch를 완료 처리한다.

## 결함 해소 확인

1. channel overwrite는 `JdbcGuildSnapshotStore`에서 member `roleIds`와 교집합인 항목만 계산한다. role 전용 allow/deny 누출을 막았고 Postgres projection 권한 동등성 테스트로 owner/member 결과를 확인했다.
2. projection 기반 message publish는 `VIEW_CHANNEL`과 `SEND_MESSAGES`를 모두 요구한다. flag가 꺼지면 기존 `InMemoryGuildService.canSendMessages`로 돌아간다.
3. `discord.authz.projection-enabled` 기본값은 `false`이며 Gateway와 Message 보호 경로 모두 동일한 rollback semantics를 사용한다.
4. message/websocket postgres profile의 `POSTGRES_PASSWORD`에는 개발용 기본값이 없고 누락 시 시작이 중단된다.
5. Guild/source row, permission version, audience outbox, watermark는 한 JDBC transaction으로 저장된다. invalid parent child-row fault test에서 네 상태가 모두 unchanged임을 확인했다.
6. relay claim은 `(guild_id,audience)`별 미발행 predecessor가 없을 때만 수행하고, 같은 version의 projection rows가 모두 published 된 후 watermark를 claim한다.

## 검증 증거

- `./gradlew :backend:modules:permission:test :backend:modules:gateway:test :backend:services:message:test :backend:services:websocket:test :backend:boot:test --tests com.example.discord.message.MessageConfigurationTest --no-daemon` — 성공.
- `DISCORD_RUN_POSTGRES_TESTS=true`와 격리 DB `discord_c2_test`로 `PostgresGuildServiceTest`, `PersistenceBootstrapTest` — 성공(7 tests).
- Message/WebSocket service test — 성공.
- `git diff --check` — whitespace 오류 없음.
- 전체 `./gradlew test`는 기존 test profile이 `legacy-auth`를 활성화하지 않아 여러 endpoint 404가 발생했다. 이는 C2 변경과 무관한 baseline 실패로 분리했으며, C2 집중/격리 게이트는 별도로 통과했다.

## 잔여 P2

- DLQ broker 7일 retention과 subject/resource masked metadata의 실제 runtime 설정 증거가 아직 없다.
- Kafka broker를 포함한 relay ordering drill은 SQL predecessor 계약과 단위 검증만 있으며 runtime fault drill은 다음 운영 웨이브로 남긴다.

## 범위 고정

현재 독립 `services/message`, `services/websocket` 앱은 business endpoint가 없는 shell이므로 projection consumer/store까지만 제공한다. 실제 보호 경로는 business endpoint가 존재하는 boot Message publish/read와 Gateway WebSocket delivery이며, 독립 endpoint가 추가될 때 동일 local `can()` adapter를 연결한다.
