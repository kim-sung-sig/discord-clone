# T171-C2 RBAC Projection 구현 독립 품질 리뷰

STATUS: DONE_WITH_CONCERNS

총점: 70/100
최종평가: 반려
P0/P1/P2: P0 1 / P1 3 / P2 2
RECOMMENDATION: CHANGES_REQUESTED

FINDINGS:

1. P0 - Projection producer가 channel role overwrite를 멤버 역할으로 필터링하지 않아 권한 우회가 가능하다. `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:143`-`150`은 모든 `channel.overwrites()`를 모든 member projection 계산에 넣는다. 기존 source 판정은 `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java:330`-`332`에서 member role에 해당하는 overwrite만 넘긴다. `EffectivePermissionCalculator`는 받은 non-everyone overwrite를 그대로 deny/allow 적용한다(`backend/modules/permission/src/main/java/com/example/discord/permission/EffectivePermissionCalculator.java:43`-`54`). 따라서 특정 role에만 허용된 hidden channel allow가 role 없는 member projection에도 적용될 수 있다.
2. P1 - Projection-enabled message publish guard가 기존 `canSendMessages` 계약보다 약하다. 기존 계약은 `VIEW_CHANNEL && SEND_MESSAGES`다(`backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java:243`-`245`). 새 guard는 projection enabled일 때 `SEND_MESSAGES`만 확인한다(`backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java:136`-`139`). VIEW 없이 SEND bit만 fresh하면 hidden channel publish를 허용한다.
3. P1 - 독립 message/websocket service shell에 production secret/profile guard가 없다. 두 service의 postgres profile은 local DB와 `dev_password` 기본값을 둔다(`backend/services/message/src/main/resources/application-postgres.yml:5`-`7`, `backend/services/websocket/src/main/resources/application-postgres.yml:5`-`7`). boot에는 production guard가 있지만 service package에는 대응 guard/test가 검색되지 않았다.
4. P1 - transaction rollback acceptance가 테스트로 닫히지 않았다. 코드는 guild save/version/outbox/watermark를 한 JDBC transaction에 묶고 rollback한다(`backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:72`-`92`). 하지만 plan은 rollback 시 네 상태가 모두 unchanged여야 한다고 요구한다(`docs/01-plan/features/T171-C2-rbac-projection.plan.md:189`), 현재 Postgres test는 commit count와 audience selector만 확인한다(`backend/boot/src/test/java/com/example/discord/guild/PostgresGuildServiceTest.java:145`-`200`).
5. P2 - DLQ 계약은 부분 구현이다. malformed JSON은 mutation 없이 metadata-only DLQ로 가고 raw payload는 저장하지 않는다(`backend/services/message/src/main/java/com/example/discord/messageservice/AuthorizationProjectionKafkaConsumer.java:56`-`76`). 다만 plan의 7일 retention과 subject/resource masked metadata 계약(`docs/01-plan/features/T171-C2-rbac-projection.plan.md:72`)은 broker/topic 설정이나 테스트로 확인되지 않는다.
6. P2 - watermark-last Kafka 계약은 구현 의도는 있으나 relay ordering 테스트가 없다. claim query는 projection before watermark로 후보를 고르지만(`backend/boot/src/main/java/com/example/discord/authorization/AuthorizationProjectionOutboxRelay.java:68`-`88`), `UPDATE ... RETURNING` 순서 보존을 직접 검증하지 않는다. 깨져도 fail-closed 쪽이지만 deny spike 위험이 남는다.

SPEC_ALIGNMENT:

부분 정렬. Spring/JDBC/Kafka profile gating, service별 consumer group, inbox+projection transaction, watermark fail-closed, rollback flag 기본 false, audience selector는 반영됐다. 독립 service shell 범위도 consumer/store/resource profile까지로 제한되어 있고 business endpoint 확장은 보류되어 있다. 하지만 projection permission equivalence와 send guard 계약이 깨져 P0/P1 기준을 통과하지 못한다.

TEST_EVIDENCE:

- PASS: `./gradlew :backend:modules:permission:test :backend:services:message:test --tests com.example.discord.messageservice.AuthorizationProjectionConsumerTest --tests com.example.discord.messageservice.AuthorizationProjectionKafkaConsumerTest :backend:services:websocket:test --tests com.example.discord.websocketservice.AuthorizationProjectionConsumerTest --tests com.example.discord.websocketservice.AuthorizationProjectionKafkaConsumerTest :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest.localAuthorizationProjectionOverridesSourceGuildForWebsocketDelivery --no-daemon`
- PASS: `DISCORD_RUN_POSTGRES_TESTS=true POSTGRES_JDBC_URL=jdbc:postgresql://127.0.0.1:15432/discord ./gradlew :backend:boot:test --tests com.example.discord.guild.PostgresGuildServiceTest --tests com.example.discord.persistence.PersistenceBootstrapTest --no-daemon`
- PASS: `./gradlew :backend:boot:test --tests com.example.discord.message.MessageConfigurationTest --no-daemon`

RISKS:

Projection flag가 기본 false라 기본 runtime은 기존 path로 rollback 가능하다. 그러나 `discord.authz.projection-enabled=true` 전환 시 hidden channel read/write/gateway filtering이 source 권한과 달라질 수 있으므로 rollout gate를 열면 안 된다. Full `./gradlew test`와 service production profile guard 검증은 아직 확인하지 않았다.

RECOMMENDATION: CHANGES_REQUESTED
