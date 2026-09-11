---
slug: T171-C-msa-scaleout-implementation
ticket: T171
phase: plan
hub: "[[T171-C-msa-scaleout-implementation]]"
---
> 🧭 [[T171-C-msa-scaleout-implementation]] · PDCA: **plan** → ~~design~~ → ~~analysis~~ → ~~report~~ → ~~feedback~~

# T171-C 분산 런타임 구현계획

> 이 문서는 task branch에서 순서대로 구현한다. 각 task는 RED → GREEN → 독립 spec/quality/security review(각 90점 이상, P0/P1 없음) → 해당 task branch push 순서를 지킨다. 구현·리뷰가 통과하지 않으면 같은 branch에서 수정 루프를 반복하며 merge하지 않는다.

## 목적·의존성

목적은 Gateway protocol truth를 socket runtime에서 분리하고, durable ACK·broker ACK·workload mTLS를 Kubernetes scale-out에 안전하게 맞추는 것이다.

`T204 capability admission → T205 durable Gateway cursor → T206 broker ACK → T207 gateway-service extraction (internal route disabled) → T210 shared internal auth/mTLS → T208 websocket transport/routing → T209 durable inbox/DLQ → T211 Kubernetes rollout/drill`

모든 Gradle 명령은 repository root에서 실행한다. 이 저장소에는 `backend/settings.gradle.kts`가 없으므로 절대 `cd backend && ./gradlew`를 사용하지 않는다.

## 공통 구현 규칙

- 공개 ingress는 `/api/**`, `/auth/**`, `/messages/**`, `/channels/**`, `/community/**`, `/ws/**`만 가진다. `/internal/**`와 operator route는 ingress rule에 추가하지 않는다.
- JWT user subject는 websocket-service가 검증하고 Gateway에는 UUID subject·correlation ID만 전달한다. user JWT의 재전달은 금지한다.
- `eventId`는 immutable UUID, `sequence`는 Gateway event log가 발급한 bigint다. sequence를 node-local map/counter로 만들지 않는다.
- 공유 PostgreSQL 전환기간의 production Flyway 단일 owner는 boot다. gateway-service는 validate-only이며 migration을 실행하지 않는다. 독립 migration Job/DB 분리는 별도 task다.
- 연결 실패·TLS 검증 실패·event authorisation 실패는 fail closed다. DLQ/metric/log에는 raw message body·JWT·certificate·source address를 기록하지 않는다.
- Gateway client cursor는 global event `sequence`가 아니라 materialized `user_sequence`다. `gateway_user_delivery`의 `(user_id,user_sequence)` cursor가 모든 session ACK/replay의 유일한 기준이다.
- 모든 Flyway migration(V1~V17)은 `backend/shared/persistence-migrations/src/main/resources/db/migration/`에만 둔다. boot만 migrate하고 gateway-service는 schema validation 실패 시 readiness false다.
- Message의 유일한 Gateway 경로는 `KafkaMessagePublishedDispatcher → discord.message.published.v1 → gateway-service consumer → gateway_user_delivery → Redis Pub/Sub wake-up`이다. Redis Stream/PEL consumer·XACK·reclaim은 삭제 범위다.

## T204 — Kubernetes capability admission

**목적:** target cluster 값 없이 runtime manifest를 추정하지 않도록 production admission을 기계적으로 차단한다.

**변경 파일:**

- Create `qa/verify-kubernetes-runtime-capabilities.sh`
- Create `qa/render-kubernetes-runtime-manifest.sh`
- Create `qa/fixtures/kubernetes-runtime-capabilities.valid.env`
- Create `infra/kubernetes/runtime/capabilities.example.env`
- Create `docs/runbooks/kubernetes-runtime-capability-admission.md`

**계약:** env 파일은 `K8S_NAMESPACE`, `INGRESS_CONTROLLER_CIDRS`, `OIDC_ISSUER`, `OIDC_JWKS_URL`, `INTERNAL_AUDIENCE`, `NETWORK_POLICY_EVIDENCE_REF`, `CERT_ISSUER_API_VERSION`, `CERT_ISSUER_KIND`, `CERT_ISSUER_NAME`, `GATEWAY_SERVER_CERT_SECRET`, `WEBSOCKET_CLIENT_CERT_SECRET`, `CA_BUNDLE_SECRET`, `CERT_KEY_PATH`, `CERT_CHAIN_PATH`, `CA_PATH`, `CERT_ROTATION_MODE`, `CERT_ROTATION_EVIDENCE_REF`, `WORKLOAD_CERT_SUBJECT_FORMAT`, `GATEWAY_SERVICE_DNS`, `PROMETHEUS_ADAPTER_EVIDENCE_REF`, `KAFKA_BOOTSTRAP_DNS`, `REDIS_DNS`, `POSTGRES_DNS`, `IMAGE_REGISTRY`, `EGRESS_ENFORCEMENT_MODE`, `GATEWAY_MIN_REPLICAS`, `GATEWAY_MAX_REPLICAS`, `GATEWAY_PDB_MIN_AVAILABLE`, `GATEWAY_CPU_REQUEST`, `GATEWAY_MEMORY_REQUEST`, `GATEWAY_HPA_METRIC_TYPE`, `GATEWAY_HPA_METRIC`, `GATEWAY_HPA_TARGET_TYPE`, `GATEWAY_HPA_TARGET`, `GATEWAY_SCALE_DOWN_SECONDS`, `WEBSOCKET_MIN_REPLICAS`, `WEBSOCKET_MAX_REPLICAS`, `WEBSOCKET_PDB_MIN_AVAILABLE`, `WEBSOCKET_CPU_REQUEST`, `WEBSOCKET_MEMORY_REQUEST`, `WEBSOCKET_HPA_METRIC_TYPE`, `WEBSOCKET_HPA_METRIC`, `WEBSOCKET_HPA_TARGET_TYPE`, `WEBSOCKET_HPA_TARGET`, `WEBSOCKET_SCALE_DOWN_SECONDS`, `TERMINATION_GRACE_SECONDS`, `DRAIN_SECONDS`를 모두 non-empty로 제공한다. `GATEWAY_SERVICE_DNS`는 정확히 `gateway-control.${K8S_NAMESPACE}.svc.cluster.local`이어야 한다. `EGRESS_ENFORCEMENT_MODE`는 `INGRESS_ONLY`, `CNI_FQDN`, `EGRESS_PROXY` 중 하나다: `CNI_FQDN`은 `CNI_PROVIDER_VERSION`,`EGRESS_FQDN_ALLOWLIST`; `EGRESS_PROXY`는 `EGRESS_PROXY_DNS`,`EGRESS_PROXY_CA_SECRET`; `INGRESS_ONLY`는 platform-approved `NETWORK_POLICY_EVIDENCE_REF`가 있어야 하고 egress deny 생성을 금지한다. `CERT_ROTATION_MODE`는 `RELOAD` 또는 `ROLLOUT_RESTART`이며 최대 허용 certificate expiry와 restart executor evidence를 ref에 포함한다. `*.example`, `CHANGE_ME`, wildcard, PEM, token/password, `http://` OIDC/JWKS URL은 거부한다. capability 파일은 Git 비밀값 저장소가 아니며 Secret name/reference와 immutable evidence URI/CI artifact reference만 허용한다.

**구현·검증:**

1. 빈 example env로 `bash qa/verify-kubernetes-runtime-capabilities.sh infra/kubernetes/runtime/capabilities.example.env`가 `missing required capability`로 실패하는 test를 먼저 작성한다.
2. 위 fixture로 성공, `OIDC_JWKS_URL=http://...`, wildcard SA, PEM 삽입 fixture로 실패하도록 구현한다.
3. verifier는 어떤 `*_EVIDENCE_REF`도 실행하지 않고 URI/approved CI artifact reference 형식만 검사한다. runbook에 사람이 실행하는 고정 명령을 둔다: platform owner의 `kubectl auth can-i`, OIDC discovery/JWKS HTTPS fetch, isolated pod NetworkPolicy deny probe, cert Secret mount/CA/SAN/client-subject/rotation/expiry-alert probe, Prometheus adapter query. 표준 NetworkPolicy로 egress destination IP/CIDR를 표현할 수 없는 Kafka·Redis·PostgreSQL·OIDC/DNS는 `EGRESS_ENFORCEMENT_MODE=INGRESS_ONLY|CNI_FQDN|EGRESS_PROXY` capability로 명시한다. `INGRESS_ONLY`는 egress-deny manifest 생성을 금지한다. connected cluster 결과는 `docs/runbooks/evidence/`에 넣지 않고 CI artifact/approved change record로 링크한다.
4. renderer는 `bash qa/verify-kubernetes-runtime-capabilities.sh "$1"` 성공 뒤에만 실행하고, approved env의 non-secret values를 temporary generated Kustomize overlay의 namespace, image registry, certificate Secret refs/paths, Gateway/WebSocket resources/HPA/PDB fields에 mapping한다. output은 stdout/temp directory만 사용하며 Git 파일을 수정하지 않는다. env에 secret pattern이 있으면 실패한다.
5. `bash qa/verify-kubernetes-runtime-capabilities.sh qa/fixtures/kubernetes-runtime-capabilities.valid.env`와 `bash qa/render-kubernetes-runtime-manifest.sh qa/fixtures/kubernetes-runtime-capabilities.valid.env test > runtime.yaml`를 GREEN으로 만든다.

**완료 조건:** capability report의 실제 승인 값 없이 T210/T211 apply/dry-run/drill을 실행하지 않는다. production은 `bash qa/render-kubernetes-runtime-manifest.sh <admitted.env> production | kubectl apply --dry-run=server -f -` 순서이며 platform owner가 실행한다.

## T205 — durable Gateway ACK·event log

**목적:** socket write, client ACK, replay cursor를 재시작과 pod 이동에서도 안전하게 분리한다.

**변경 파일:**

- Modify `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewaySession.java`
- Modify `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java`
- Create `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayCommandService.java`
- Create `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayEventLog.java`
- Create `backend/boot/src/main/resources/db/migration/V16__gateway_event_log.sql`
- Create `backend/boot/src/main/java/com/example/discord/gateway/JdbcGatewayEventLog.java`
- Create `backend/boot/src/main/java/com/example/discord/gateway/JdbcGatewayDeliveryCursorStore.java`
- Test `backend/modules/gateway/src/test/java/com/example/discord/gateway/InMemoryGatewayServiceTest.java`
- Test `backend/boot/src/test/java/com/example/discord/gateway/JdbcGatewayEventLogTest.java`
- Test `backend/boot/src/test/java/com/example/discord/gateway/JdbcGatewayDeliveryCursorStoreTest.java`

**포트와 DB:**

```java
public interface GatewayCommandService {
  GatewayIdentifyResult identify(UUID userId);
  GatewayResumeResult resume(UUID sessionId, UUID userId, String transportInstanceId, long clientLastAckedSequence);
  GatewayDeliveryPage poll(UUID sessionId, UUID userId, long deliveryEpoch, String transportInstanceId, int limit);
  GatewayDeliveryResult markDelivered(UUID sessionId, UUID userId, long deliveryEpoch, long sequence, UUID eventId, String deliveryToken, String transportInstanceId);
  GatewayAckResult ack(UUID sessionId, UUID userId, long deliveryEpoch, long sequence, String transportInstanceId);
  GatewayHeartbeatResult heartbeat(UUID sessionId, UUID userId, long deliveryEpoch, long clientLastAckedSequence, String transportInstanceId);
}
```

`V16`에는 immutable `gateway_event_log(sequence bigint generated always as identity primary key, event_id uuid unique, ordering_key, scope_type, scope_id, payload_ref, payload_hash, expires_at)`, `gateway_user_delivery(user_id,user_sequence,event_id,event_sequence,stream_key,visibility_version,status,expires_at,primary key(user_id,user_sequence),unique(user_id,event_id))`, `gateway_session_delivery(session_id,user_id,acknowledged_user_sequence,highest_granted_user_sequence,highest_delivered_user_sequence,delivery_epoch,owner_instance_id,owner_lease_expires_at,version,updated_at,expires_at,primary key(session_id,user_id))`, `gateway_delivery_grant(session_id,user_id,delivery_epoch,user_sequence,event_id,delivery_token_hash,position,expires_at,unique(session_id,user_id,delivery_epoch,user_sequence))`를 만든다. cursor CHECK는 `0 <= acknowledged <= highest_delivered <= highest_granted`다. `GatewayDeliveryCursorStore`는 `version` CAS와 `(user_id,user_sequence)` bounded selection을 사용한다.

**RED → GREEN:**

1. 다음 failing test를 쓴다: write 실패 시 `markDelivered` 없음, ACK가 `highestDelivered` 초과면 `ACK_OUT_OF_RANGE`, stale/duplicate ACK는 no-op, concurrent `markDelivered`/ACK가 단조, reconnect/pod 이동은 epoch 교체와 durable ACK부터 replay, old owner/epoch·poll cursor 조작·high sequence mark·wrong grant token 거부, retention 밖 resume은 `RESYNC_REQUIRED`, visibility 상실 event는 replay 제외.
2. `./gradlew :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest`를 RED로 확인한다.
3. `append(eventId, ...)`의 duplicate same hash는 original sequence, different hash는 conflict audit/no overwrite; `poll`은 client cursor를 받지 않고 current epoch/owner에 1..100 grant를 발급하며 256 KiB cap과 delivery-time authorization을 강제한다.
4. `resume`은 epoch와 owner lease를 CAS로 교체하고 prior grants를 삭제한다. `markDelivered`는 current epoch/owner의 next grant와 one-time token만 허용한다. `ack`/heartbeat는 current epoch에서 `sequence <= highestDelivered`만 허용한다.
5. `./gradlew :backend:modules:gateway:test :backend:boot:test --tests com.example.discord.gateway.JdbcGatewayEventLogTest --tests com.example.discord.gateway.JdbcGatewayDeliveryCursorStoreTest`를 GREEN으로 만든다.

**완료 조건:** production wiring에서 process-local sequence/window과 Redis Stream/PEL consumer는 제거 대상이며, migration은 gateway-service로 이동하지 않는다.

## T206 — Outbox → Kafka broker ACK

**목적:** Kafka durable acceptance 전에는 Outbox가 published가 되지 않게 한다.

**변경 파일:**

- Modify `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java`
- Modify `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java`
- Modify `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayBusPublishCommand.java`
- Create `backend/modules/message/src/main/java/com/example/discord/message/MessagePublishedRecord.java`
- Create `backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedDispatcher.java`
- Modify `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java`
- Test `backend/boot/src/test/java/com/example/discord/gateway/KafkaGatewayEventBusTest.java`
- Test `backend/modules/message/src/test/java/com/example/discord/message/DefaultMessagePublicationRelayTest.java`

**record contract:** `MessagePublishedRecord` v1 is `{eventId,messageId,guildId,channelId,correlationId,occurredAt,payloadRef,payloadHash}`. `payloadRef` is the immutable message ID read through new `GatewayPayloadReader` (authorized JDBC `MessageLookupPort` adapter); raw message body is neither Kafka header nor DLQ data. Kafka topic is `discord.message.published.v1`, record key is `channelId`, and unknown schema version/deserialization failure is safe quarantine.

**RED → GREEN:** failed/timed-out `send()` future가 relay error를 만들고 `markPublished`가 호출되지 않는 test, retry가 original `MessagePublished.eventId`를 그대로 전달하는 test, record codec version/unknown version behavior, payloadRef reader authorization, duplicate ID가 second client event를 만들지 않는 test를 먼저 작성한다. `KafkaMessagePublishedDispatcher` replaces the direct `InMemoryGatewayService.publish()` call; producer result를 finite configured timeout으로 기다리고 failure면 claim을 retry delay와 함께 반납한다. `GatewayBusPublishCommand`은 caller-supplied `deliveryEventId`를 가지며 payload hash conflict를 거부한다.

**검증:** `./gradlew :backend:modules:message:test :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest --tests com.example.discord.message.DefaultMessagePublicationRelayTest`.

## T207 — gateway-service extraction (internal route disabled)

**목적:** Gateway protocol truth와 Redis/Kafka adapters를 boot에서 독립 control-plane runtime으로 이동한다. 이 task는 control route를 등록하거나 Kubernetes에 배포하지 않는다.

**변경 파일:**

- Modify `settings.gradle.kts` (`:backend:services:gateway`, `:backend:shared:persistence-migrations` include/projectDir)
- Create `backend/shared/persistence-migrations/build.gradle.kts`
- Move `backend/boot/src/main/resources/db/migration/*` to `backend/shared/persistence-migrations/src/main/resources/db/migration/`
- Modify `backend/boot/build.gradle.kts`
- Modify `backend/boot/src/main/java/com/example/discord/persistence/PostgresPersistenceConfiguration.java`
- Create `backend/services/gateway/build.gradle.kts`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayServiceApplication.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayControlFacade.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayWakeUpPublisher.java`
- Move `backend/boot/src/main/java/com/example/discord/gateway/{GatewayConfiguration,GatewaySessionMaintenance,RedisGatewaySessionRegistry,RedisGatewayEventBus,KafkaGatewayEventBus,JdbcGatewayEventLog,JdbcGatewayDeliveryCursorStore,GatewayController,GatewayControllerAdvice}.java` to `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/`
- Modify `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java`
- Test `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/GatewayControlFacadeTest.java`
- Test `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/GatewayResumeIntegrationTest.java`

`backend/shared/persistence-migrations`는 V1~V16의 유일한 classpath resource owner다. boot와 gateway-service 모두 이 resource dependency와 동일 PostgreSQL/Flyway version을 사용한다. boot는 기존 schema migration을 계속 실행하고 gateway-service는 `flyway.validate-on-migrate=true`로 history/schema를 validation한 뒤 기동한다. T207은 gateway DB를 분리하지 않는다. `GatewayControlFacade`는 T205 포트를 그대로 호출하며 아직 HTTP mapping을 갖지 않는다. internal server는 `discord.gateway.internal.enabled=false`가 기본값이고 T210이 mTLS/filter와 함께 true profile을 만든 뒤에만 시작한다. 따라서 static token/unauthenticated `/internal/**` route가 일시적으로라도 존재하지 않는다.

`backend/services/gateway/build.gradle.kts`에는 `:backend:modules:gateway`, `:backend:modules:message`, `:backend:shared:common`, `:backend:shared:persistence-migrations`, Spring Boot Web/Validation/Actuator, JDBC, Flyway, Redis, Kafka, PostgreSQL driver, Micrometer와 corresponding test starter를 명시한다. moved class package는 `com.example.discord.gatewayservice`로 바꾸고 component scan은 그 package와 module ports만 포함한다. boot의 `GatewayController`/`GatewayControllerAdvice`와 Gateway beans는 제거한다; `/api/gateway/**`는 진단용으로도 남기지 않으며 T208 전 public Gateway handler만 boot에 남는다.

**message event handoff:** boot `MessagePublishedDispatcher`는 Gateway bean 직접 호출을 제거하고 Outbox relay의 Kafka topic `discord.message.published.v1`에 `key=channelId`, header/body `eventId`를 넣어 broker ACK 뒤 publish한다. gateway-service consumer group `gateway-service-v1`은 그 record의 original `eventId`·payload hash를 `GatewayEventLog.append`에 전달하고 owner-specific wake-up을 발행한다. 이 consumer만 Message→Gateway의 최종 경로다.

**wake-up:** Gateway는 Redis lease에서 live session owner를 찾고 durable event append 뒤 payload 없이 `{sessionId}`를 `gateway.wake.{ownerInstanceId}`에 publish한다. 이 이벤트는 hint이며, delivery/replay source가 아니다.

**RED → GREEN:** same eventId append의 wake-up one-per-owner, 두 Gateway runtime instance의 durable session resume, boot context에서 Gateway controller/bean 부재, boot migration 후 gateway Flyway history validation, Outbox→Kafka record→Gateway append까지 unchanged eventId, disabled profile에서 `/internal/**` mapping 부재 test를 먼저 쓴다. `./gradlew :backend:services:gateway:test :backend:boot:test :backend:services:gateway:bootJar`와 `git diff --check`를 GREEN으로 만든다. T210 전에는 gateway control runtime을 Docker/Kubernetes에 기동·노출하지 않는다.

## T210 — shared internal authentication·mTLS foundation

**목적:** 독립 runtime 사이의 내부 호출이 static header나 plaintext 없이 인증되게 하고, T207의 disabled facade를 인증된 internal API로만 활성화한다.

**변경 파일:**

- Create `backend/shared/common/src/main/java/com/example/discord/common/auth/WorkloadIdentity.java`
- Create `backend/shared/common/src/main/java/com/example/discord/common/auth/WorkloadIdentityVerifier.java`
- Create `backend/shared/common/src/main/java/com/example/discord/common/auth/InternalWorkloadAuthenticationFilter.java`
- Create `backend/shared/common/src/main/java/com/example/discord/common/tls/InternalTlsProperties.java`
- Modify `backend/shared/common/build.gradle.kts`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayControlController.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayInternalSecurityConfiguration.java`
- Create `backend/services/gateway/src/main/resources/application.yml`
- Modify `backend/services/websocket/build.gradle.kts`
- Create `backend/services/websocket/src/main/java/com/example/discord/websocketservice/GatewayControlClientTlsConfiguration.java`
- Create `backend/services/websocket/src/main/resources/application.yml`
- Create `backend/services/websocket/src/test/java/com/example/discord/websocketservice/InternalGatewayClientTlsTest.java`
- Create `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/GatewayInternalMutualTlsTest.java`
- Test `backend/shared/common/src/test/java/com/example/discord/common/auth/WorkloadIdentityVerifierTest.java`

**인증 계약:** projected SA JWT는 exact issuer·`aud=discord.internal`·exp·namespace·SA·route allowlist를 검증한다. T204의 `WORKLOAD_CERT_SUBJECT_FORMAT`은 websocket/operator certificate SAN 또는 SPIFFE URI를 SA subject와 일대일로 정한다. Gateway filter는 peer certificate SAN과 JWT `sub=system:serviceaccount:{namespace}:{sa}`가 같은 allowlist 행일 때만 수락한다. `gateway-service` allowlist는 websocket SA의 `POST /internal/gateway/{identify,resume,poll,delivered,ack,heartbeat}`와 operator SA의 `GET/POST /internal/operator/gateway-delivery-dlq/**`만이다. user JWT, missing token, wrong issuer/audience/namespace/SA/route, 다른 workload certificate+websocket JWT 조합은 401/403이다. TokenReview wildcard RBAC는 만들지 않는다.

**mTLS 계약:** T204가 승인한 issuer가 gateway server certificate/key와 CA bundle, websocket client certificate/key Secret을 제공한다. gateway application은 `server.ssl.enabled=true`, `server.ssl.client-auth=need`와 trust bundle을 사용한다. websocket `GatewayControlClient`는 `https://{GATEWAY_SERVICE_DNS}`만 사용하고 same CA·client cert를 제시하며 DNS SAN을 검증한다. TLS verification, workload JWT 어느 하나가 실패해도 fallback HTTP를 사용하지 않는다. certificate rotation은 admission report가 명시한 mounted-file reload 또는 controlled restart 한 방식만 채택하고 integration test와 cluster drill로 검증한다.

**RED → GREEN:** malformed/expired/wrong audience/wrong SA token reject, valid websocket SA only route accept, user JWT reject test를 먼저 쓴다. test CA로 mutual TLS success, no client cert reject, wrong CA/SAN reject, plaintext URI fail, websocket cert+operator JWT and operator cert+websocket JWT reject test를 작성한다. `./gradlew :backend:shared:common:test :backend:services:gateway:test :backend:services:websocket:test --tests com.example.discord.websocketservice.InternalGatewayClientTlsTest --tests com.example.discord.gatewayservice.GatewayInternalMutualTlsTest`를 GREEN으로 만든다.

## T208 — websocket transport extraction·routing repair

**목적:** websocket-service만 `/ws/gateway` socket을 소유하고 Gateway control API와 안전하게 연결한다.

**변경 파일:**

- Create `backend/services/websocket/src/main/java/com/example/discord/websocketservice/GatewayTransportWebSocketHandler.java`
- Create `backend/services/websocket/src/main/java/com/example/discord/websocketservice/GatewayControlClient.java`
- Create `backend/services/websocket/src/main/java/com/example/discord/websocketservice/GatewayWakeUpSubscriber.java`
- Create `backend/services/websocket/src/main/java/com/example/discord/websocketservice/GatewayTransportConfiguration.java`
- Modify `backend/services/websocket/src/main/java/com/example/discord/websocketservice/WebsocketServiceApplication.java`
- Modify `infra/gateway/nginx.conf`
- Modify `infra/docker/docker-compose.yml`
- Delete `backend/boot/src/main/java/com/example/discord/gateway/GatewayWebSocketConfiguration.java`
- Delete `backend/boot/src/main/java/com/example/discord/gateway/GatewayWebSocketHandler.java`
- Test `backend/services/websocket/src/test/java/com/example/discord/websocketservice/GatewayTransportWebSocketHandlerTest.java`
- Test `backend/services/websocket/src/test/java/com/example/discord/websocketservice/GatewayTransportComposeIntegrationTest.java`

**routing 결정:** Nginx location을 `location /ws/ { proxy_pass http://websocket_service; ... }`로 바꿔 trailing slash URI rewrite를 제거한다. 그러므로 compose public URL은 `ws://localhost:18080/ws/gateway`이고 upstream handler path도 정확히 `/ws/gateway`다.

**동작:** transport는 JWT 검증 후 `identify/resume`하여 current `deliveryEpoch`를 받는다. 연결 map에는 socket, user/session ID, epoch, pending queue만 둔다. wake-up 수신 또는 최대 1초 timer가 cursor 없는 `poll(sessionId,epoch,limit)`을 호출하고, each completed socket write 뒤 eventId/one-time grant token을 포함한 `delivered`를 호출한다. client ACK 및 heartbeat는 같은 epoch의 Gateway `ack`/`heartbeat`로 전달한다. direct Redis event history/sequence는 금지한다. connection owner Redis lease는 transport instance ID로 등록/renew하고 종료 시 release한다.

**limits/drain:** `poll` response는 100 event/256 KiB, per socket write queue는 100 frame/256 KiB. 초과하면 `RECONNECT` 후 1013 close. readiness false는 새 upgrade 거부 → bounded flush → `RECONNECT` → close. durable ACK와 local map을 혼동하지 않는다.

**compose E2E:** `infra/docker/docker-compose.yml`에 gateway-service, PostgreSQL, Redis, Kafka healthcheck와 `websocket-service`의 `GATEWAY_SERVICE_DNS=https://gateway-service:8443`를 추가한다. `qa/fixtures/tls/`의 test-only CA/server/client certificate는 Git에 production credential 없이 fixture로 두고 compose volume mount한다. gateway-service TLS SAN은 `gateway-service`, websocket client trust/cert path는 T210 properties로 지정한다. `docker compose -f infra/docker/docker-compose.yml --profile gateway-e2e up --build --wait`, `ws://localhost:18080/ws/gateway` upgrade probe 및 authenticated control-call assertion, `docker compose -f infra/docker/docker-compose.yml --profile gateway-e2e down --volumes` 순서로 실행한다.

**RED → GREEN:** invalid user JWT가 identify 이전에 close, controller client가 plaintext URL 또는 invalid server/client cert를 거부, write failure가 delivered/ACK cursor를 바꾸지 않음, stale epoch/grant token이 reject, Redis wake-up loss 뒤 timer poll로 delivery, Nginx `ws://localhost:18080/ws/gateway` upgrade가 websocket-service handler에 도착하고 mTLS control call까지 성공함을 test한다. `./gradlew :backend:services:websocket:test`와 compose upgrade test가 GREEN인 뒤 boot handler를 삭제한다.

## T209 — durable inbox·PEL reclaim·operator DLQ

**목적:** retry가 process restart를 넘어 안전하고, quarantine/replay가 최소 권한으로 감사 가능하게 한다.

**변경 파일:**

- Create `backend/services/gateway/src/main/resources/db/migration/V17__gateway_delivery_inbox.sql`
- Create `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayDeliveryInbox.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/JdbcGatewayDeliveryInbox.java`
- Modify `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/RedisGatewayEventBus.java`
- Modify `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/KafkaGatewayEventBus.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayDeliveryDlqController.java`
- Create `backend/services/gateway/src/main/java/com/example/discord/gatewayservice/GatewayDeliveryDlqOperatorCommand.java`
- Test `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/JdbcGatewayDeliveryInboxTest.java`
- Test `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/RedisGatewayEventBusTest.java`
- Test `backend/services/gateway/src/test/java/com/example/discord/gatewayservice/GatewayDeliveryDlqControllerTest.java`

`V17` creates `gateway_delivery_inbox(event_id, consumer_name, processed_at, expires_at, primary key(event_id,consumer_name))` and `gateway_delivery_replay_audit(id,event_id,caller_subject,outcome,created_at)`, with expiry index. Each consumer commits inbox marker with its side effect. retryable Redis errors remain pending; dead consumer PEL entries are reclaimed; malformed record is quarantined as safe metadata then XACKed.

operator endpoint is `GET /internal/operator/gateway-delivery-dlq?cursor=&limit=` and `POST /internal/operator/gateway-delivery-dlq/{eventId}/replay`. It has no Ingress, requires T210 operator SA/mTLS, max limit 100, returns no raw payload, accepts no replay body, rehydrates source by eventId, and audits every result. The only caller is `GatewayDeliveryDlqOperatorCommand` executed by the T211 `discord-operator` Job; a human uses the Job parameter/approved runbook, never exposes this endpoint externally. Replay atomically claims `(event_id,status=REPLAYING)` with a unique constraint; concurrent/in-progress/already-success/unknown requests return idempotent safe outcome and audit eventId/result code only.

**RED → GREEN:** restart duplicate has one side effect, PEL dead consumer reclaim, retry remains pending, malformed safe quarantine, no user/non-operator caller, `limit>100`, unknown/already-successful event, body-bearing replay reject, and double replay tests. Run `./gradlew :backend:services:gateway:test --tests com.example.discord.gatewayservice.JdbcGatewayDeliveryInboxTest --tests com.example.discord.gatewayservice.RedisGatewayEventBusTest --tests com.example.discord.gatewayservice.GatewayDeliveryDlqControllerTest`.

## T211 — Kubernetes runtime manifests·HPA·failure drill

**선행 조건:** T204 report의 모든 production 값과 T210 internal TLS tests가 GREEN이다. 없으면 manifest rendering/contract까지만 하고 server dry-run/apply를 하지 않는다.

**변경 파일:**

- Create `infra/kubernetes/runtime/base/{namespace,kustomization,default-deny-network-policy}.yaml`
- Create `infra/kubernetes/runtime/base/{gateway,websocket,operator}-serviceaccount.yaml`
- Create `infra/kubernetes/runtime/base/{gateway,websocket,operator}-network-policy.yaml`
- Create `infra/kubernetes/runtime/base/gateway-control-service.yaml`
- Create `infra/kubernetes/runtime/base/{gateway-server,websocket-client,operator-client}-certificate.yaml`
- Create `infra/kubernetes/runtime/base/{gateway,websocket}-deployment.yaml`
- Create `infra/kubernetes/runtime/base/websocket-service.yaml`
- Create `infra/kubernetes/runtime/base/{gateway,websocket}-hpa.yaml`
- Create `infra/kubernetes/runtime/base/{gateway,websocket}-pdb.yaml`
- Create `infra/kubernetes/runtime/base/operator-job.yaml`
- Create `infra/kubernetes/runtime/base/runtime-ingress.yaml`
- Create `infra/kubernetes/runtime/overlays/test/kustomization.yaml`
- Create `qa/verify-runtime-kubernetes-contract.sh`
- Create `qa/runtime-scaleout-failure-drill.md`

**exact boundary:** this task deploys only Gateway, WebSocket and operator Job. Generic API/worker manifests are deliberately excluded: their executable/image/consumer lifecycle is not yet a T171-C decision. `gateway-control-service` is `ClusterIP`; `websocket-service` is the only public-service backend. `runtime-ingress` contains no `gateway-control`, `/internal`, or operator backend/path. Standard Kubernetes `NetworkPolicy` selects a fixed websocket pod label `security.discord.io/gateway-control-caller=websocket` in the same namespace and TLS port only; it cannot select ServiceAccount or HTTP path. websocket Deployment separately sets `serviceAccountName: discord-websocket`. operator Job uses fixed label `security.discord.io/gateway-control-caller=operator`, `serviceAccountName: discord-operator`, projected token/client certificate, and a separate egress-to-Gateway TLS policy. mTLS+JWT application filter owns SA/route authorization. API/default/external namespace pods have no Gateway control ingress allow. Every SA uses `automountServiceAccountToken: false`; only websocket/operator mount an explicit projected `aud=discord.internal` token.

**manifest tests:** failing shell contract asserts no `LoadBalancer`/Ingress for gateway-control, no internal/operator path in ingress, exact websocket caller label (missing/different label fixture fails), `serviceAccountName` match, no `automount:true`, certificate Secret volumes/key paths, TLS ports, probes/resources/topology/PDB/HPA, `minReplicas >= pdb.minAvailable + 1`, HPA min/max/target/scale-down values, and only T204-approved metric names/values. If `EGRESS_ENFORCEMENT_MODE=INGRESS_ONLY`, no egress-deny policy is rendered; CNI FQDN/egress-proxy mode requires its admitted fixture. Render with `kubectl kustomize infra/kubernetes/runtime/overlays/test`; then run `bash qa/verify-runtime-kubernetes-contract.sh` GREEN. If connected-cluster admission exists, platform owner runs `kubectl apply --dry-run=server -k ...`; failure blocks rollout.

**drills:** runbook fixes each target, command, success signal and rollback. `kubectl delete pod -l app.kubernetes.io/component=websocket --wait=false` before/after delivered/ACK must show reconnect plus no ACK beyond delivered; `kubectl rollout restart deployment/gateway-service` must show readiness false→drain→available replicas; admitted certificate renewal must show new serial and no plaintext fallback; platform-approved Kafka/Redis fault command must show retry/PEL metrics without `published_at` advance; `kubectl scale deployment/websocket-service --replicas=N` then adapter metric query must show HPA scale-down only after configured stabilization. Each drill captures `kubectl get pods,hpa,pdb`, `kubectl describe`, safe service logs and declared metric query. Abort and rollback when readiness stays false past `DRAIN_SECONDS + TERMINATION_GRACE_SECONDS`, data-loss invariant fails, or certificate expiry is below admitted threshold: set readiness false, scale Gateway/WebSocket back to previous replica count, keep event store and audit records intact.

## 최종 수락 기준

각 task의 declared test, `git diff --check`, independent spec/quality/security review가 모두 90점 이상이고 P0/P1 없음이어야 한다. 이 기준 전에는 commit/push/merge 하지 않는다. 최종 merge는 task branch마다 pre-merge independent validation을 통과한 뒤 `main`에 no-ff merge하고 remote SHA 확인 후 branch를 제거한다.
