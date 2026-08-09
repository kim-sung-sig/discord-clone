# T171-C3 이벤트·Outbox·Kafka 계약 조사

상태: 조사 완료 (구현·설계 문서 수정 없음)
작성일: 2026-08-09
조사 기준: `fe6807d17b9266e87c74c4e67f0ec9b1d7e95785` + working tree의 T171-C0 ADR
조사 범위: MessagePublished, Message outbox/relay, KafkaGatewayEventBus, RedisGatewayEventBus, migration, 단위·중앙 smoke 테스트, Docker Compose

## 1. 결론

현재 구현은 `MessagePublished` 도메인 이벤트와 `GatewayBusEvent` 전송 이벤트가 분리되어 있다. `MessagePublished.eventId`가 Gateway 이벤트까지 전달되지 않고 Gateway bus가 새 ID를 생성하므로, 현재 상태는 T171-C0의 “eventId 불변·Outbox→Kafka→consumer inbox” 계약을 구현할 수 없다. 또한 Message service 실행 모듈에는 현재 boot의 relay/DB adapter/Kafka 설정이 연결되어 있지 않다.

따라서 C3 구현의 첫 단계는 Kafka 코드를 추가하는 것이 아니라 다음 계약을 먼저 고정하는 것이다.

1. 원본 `eventId`를 Message transaction에서 생성해 envelope과 Gateway event까지 그대로 전달한다.
2. `schemaVersion`, `producerService`, `aggregateId`, `routingKey`, `traceId`, `region`을 가진 공통 envelope을 도입한다.
3. Outbox row는 broker ACK가 확인된 뒤에만 발행 완료로 표시한다.
4. Consumer는 `(consumerName, eventId)` inbox unique 제약과 projection 변경을 한 transaction으로 처리한다.
5. Redis Stream은 Message의 durable transport가 아니다. Gateway 전환 전까지의 호환 adapter로만 취급하고, 최종 Message delivery 경로에서는 Redis Pub/Sub wake-up만 허용한다.

## 2. 실제 현재 흐름

```text
Client
  -> DefaultPublishMessageUseCase
     -> idempotency 조회
     -> publish guard/content policy
     -> Message + MessagePublished(eventId) 생성
     -> MessagePublicationStore.savePublished()
        (JDBC에서는 message row, idempotency row, outbox row를 transaction 처리)
  -> MessagePublicationRelayWorker (1초 fixed delay, 기본 batch 50)
     -> MessagePublicationOutboxQueue.claimPendingPublications()
     -> MessagePublishedDispatcher.dispatch()
        -> MessageLookupPort로 원본 Message 재조회
        -> InMemoryGatewayService.publish("MESSAGE_CREATE", ...)
           -> GatewayEventBus(InMemory | Redis Stream | Kafka)
     -> dispatch가 정상 반환되면 outbox.markPublished()
```

현재 `MessageConfiguration`의 dispatcher가 직접 `InMemoryGatewayService`를 주입받는다. 이 dispatcher는 Kafka `MessagePublished` consumer가 아니며, Message service와 Gateway service 사이의 독립 서비스 계약도 아니다.

현재 독립 실행 모듈의 경계도 주의해야 한다.

- `backend/services/message`는 `modules:message`를 의존하지만 `backend/boot`의 `MessageConfiguration`, `JdbcMessageStore`, `MessagePublicationRelayWorker`, `MessageOutboxController`를 자동 스캔하지 않는다.
- `backend/services/message/build.gradle.kts`에는 직접적인 Spring Kafka/Redis 의존성이 없다. Kafka/Redis adapter는 현재 `backend/boot`에 있다.
- `infra/docker/docker-compose.yml`의 `message-service`에는 Kafka bootstrap, topic prefix, Redis profile 환경변수가 설정되어 있지 않다.

즉 현재 monolith/boot 경로의 테스트가 통과해도 독립 Message service에서 outbox relay가 실제로 실행된다는 증거는 아니다. C3 acceptance에는 service application context에서 relay와 Kafka publisher가 등록되는 검증이 필요하다.

## 3. MessagePublished 도메인 이벤트 계약

실제 타입은 다음 필드만 가진다.

```java
record MessagePublished(
    UUID eventId,
    UUID messageId,
    MessageAuthor author,
    MessageTarget target,
    List<MessageMentionTarget> mentions,
    String correlationId,
    Instant occurredAt)
```

세부 값은 다음과 같다.

| 필드 | 현재 의미 | 현재 저장 위치 |
|---|---|---|
| `eventId` | 발행 이벤트 식별자, `DefaultPublishMessageUseCase`에서 UUID 생성 | `message_publication_outbox.event_id` PK |
| `messageId` | 메시지 식별자 | outbox FK 및 메시지 row |
| `author` | 현재 User/Bot/Webhook/System sealed subtype | outbox의 `author_type`, `author_id` (현재 JDBC는 USER만 복원) |
| `target` | Channel/Thread/Direct subtype | outbox의 target/guild/channel 컬럼 (현재 JDBC는 CHANNEL만 복원) |
| `mentions` | 타입화된 mention 목록 | `message_publication_outbox_mentions` |
| `correlationId` | 요청 상관관계 식별자 | outbox column |
| `occurredAt` | 이벤트 발생 시각 | outbox column |

현재 이벤트 자체에는 `content`, `chatRoomId`, `visibilityVersion`, `schemaVersion`, `producerService`, `traceId`, `region`이 없다.

현재 Gateway dispatcher는 이벤트의 원본 eventId를 쓰지 않고 `MessageLookupPort`로 메시지를 다시 읽어 다음 payload를 만든다.

```text
id, guildId, channelId, authorId, content, mentions,
pinned, deleted, edited, createdAt, updatedAt
```

따라서 T171-C0 ADR의 `MessagePublished.payload.contentSnapshot` 요구와 현재 T172 설계의 “MessagePublished는 본문을 포함하지 않는다” 결정이 충돌한다. C3 구현 전에 하나를 선택해야 한다.

- 권장: Message transaction에서 표시용 `contentSnapshot`을 event payload에 포함하고, immutable snapshot을 Outbox에 저장한다.
- 대안: Message consumer가 원본 Message를 읽는 별도 authenticated snapshot API를 두되, 서비스 DB 직접 접근 금지와 snapshot version/hash 검증을 계약으로 추가한다.

이 결정을 하지 않은 채 Kafka consumer를 구현하면 event replay 시 현재 메시지와 과거 이벤트의 내용이 달라질 수 있다.

## 4. JDBC Outbox의 실제 상태 전이

### 4.1 DDL

`V9__message_clean_persistence_ports.sql`은 `message_publication_outbox`를 만든다.

- `event_id` PK, `event_type`, `message_id`, author/target, `guild_id`, `channel_id`, `correlation_id`, `occurred_at`, `published_at`
- mention은 `message_publication_outbox_mentions(event_id, position)`로 별도 저장
- unpublished index는 `(occurred_at, event_id)`

`V10__message_outbox_claims.sql`은 `claim_token`, `claimed_at`, `claim_expires_at`, `attempts`, `last_error`, `dead_lettered_at`를 추가한다.

### 4.2 Relay

`JdbcMessageStore.claimPendingPublications()`는 `FOR UPDATE SKIP LOCKED`로 미발행·미 DLQ·lease 만료 row를 최대 100개 가져오고 한 batch에 동일 `claimToken`과 30초 lease를 기록한다.

`DefaultMessagePublicationRelay`는 각 row에 대해:

1. dispatcher를 호출한다.
2. dispatcher가 정상 반환하면 `markPublished(eventId, claimToken, now)`를 호출한다.
3. 예외가 나면 `releaseFailed()`로 attempts를 1 증가시키고 5초 후 재시도한다.
4. JDBC 구현은 attempts가 10 이상이면 `dead_lettered_at`을 설정하고 더는 claim하지 않는다.

현재 `markPublished`는 dispatcher가 반환한 시점만 확인한다. dispatcher가 내부적으로 비동기 Kafka `send()`를 호출하고 send future를 기다리지 않으면 broker ACK 전에 outbox를 published로 바꿀 수 있다. C3에서는 publisher adapter가 동기적으로 broker ACK를 확인하거나 명시적 ACK future를 relay에 반환해야 한다.

### 4.3 InMemory 차이

`InMemoryMessageService`도 동일한 개념의 pending/claim/retry/DLQ를 가지지만 프로세스 메모리 상태다. process restart와 다중 pod 중복 claim을 검증할 수 없다. 단위 테스트 전용으로 유지하고 production acceptance 근거로 사용하지 않는다.

## 5. 현재 Gateway 전송 adapter

### 5.1 공통 GatewayBusEvent

`GatewayBusEvent`는 `eventId(String)`, `type`, `guildId`, 선택적 `channelId`, sanitized `payload`, `createdAt`를 가진다. `GatewayBusPublishCommand`에는 eventId 필드가 없으므로 모든 adapter가 `UUID.randomUUID()`로 eventId를 생성한다.

결과적으로 다음 ID가 달라진다.

```text
MessagePublished.eventId = A
GatewayBusEvent.eventId   = B (현재 InMemory/Redis/Kafka 모두 새 생성)
```

이 구조에서는 `(consumerName, A)` inbox dedup과 Gateway event log dedup을 연결할 수 없다. `GatewayBusPublishCommand` 또는 새 versioned event command가 원본 `eventId`를 필수로 받아야 한다.

### 5.2 KafkaGatewayEventBus

- profile: `kafka`
- topic: `${DISCORD_KAFKA_TOPIC_PREFIX:discord}.gateway.events`
- publish key: `guildId`
- value: `{sourceNodeId,eventId,type,guildId,channelId,payload,createdAt}`
- publish 시 `kafka.send(...)`를 호출하지만 future를 기다리지 않는다.
- publish 직후 local listeners를 호출한다.
- listener group id는 `discord.gateway.node-id`로 node별 group을 만든다.
- 같은 node의 source record는 무시하고, 다른 node record만 listener로 전달한다.
- malformed envelope은 `.dead-letter` topic에 reason, node/event metadata, message size/hash prefix만 기록한다.
- listener exception도 DLQ metadata를 발행하지만 listener별 실패를 재시도하지 않는다.

현재 Kafka topic에는 `schemaVersion`, `producerService`, `aggregateId`, `routingKey`, `traceId`, `region`, delivery recipient 정보가 없다. 또한 Spring Kafka listener container의 retry/ack mode를 애플리케이션 코드에서 명시하지 않는다. `handleMessage`가 정상 반환하는 malformed/listener failure 경로는 broker 관점에서 처리 완료로 간주될 수 있다.

### 5.3 RedisGatewayEventBus

- profile: `redis & !kafka`
- channel event stream: `gateway:channel:{channelId}`
- guild-wide event stream: `gateway:guild:{guildId}`
- global stream: `gateway:global`
- record fields: `eventId,type,guildId,channelId,payload,createdAt,sourceNodeId`
- stream max length 기본값: 10,000
- node-scoped consumer group: `{consumerGroupPrefix}:{nodeId}`
- poll은 pending(`0-0`)과 `lastConsumed()`를 함께 읽는다.
- 자신의 source record는 local delivery 중복을 제거하지만 항상 ACK한다.
- decode 오류와 listener failure를 `gateway:dead-letter`에 metadata/hash로 기록한 뒤 원 stream record를 ACK한다.

Redis Streams는 현재 Gateway node fanout/replay adapter로 사용되지만 durable Message event log가 아니다. trim으로 데이터가 사라지고, listener failure가 원 record 재처리 없이 ACK된다. 최종 C3 경로에서 Redis를 wake-up 용도로만 남기려면 `RedisGatewayEventBus`를 MessagePublished transport로 재사용하지 않는 명시적 feature flag와 drain 계획이 필요하다.

## 6. 현재 테스트와 실행 증거

| 테스트 | 검증하는 것 | 검증하지 않는 것 |
|---|---|---|
| `DefaultMessagePublicationRelayTest` | claim한 이벤트 dispatch, 정상 mark, 실패 release의 단위 흐름 | 실제 DB lease 경합, broker ACK |
| `JdbcMessageStoreTest` | JDBC message/outbox 저장, claim, mark, retry/DLQ | Kafka broker와의 원자성 |
| `KafkaGatewayEventBusTest` | topic/key, sanitizer, source node 억제, malformed/listener DLQ metadata | send future 대기, consumer retry/inbox, 실제 broker |
| `RedisGatewayEventBusTest` | stream key/fields, group pending read, own source, trim, malformed/listener DLQ | PEL reclaim 장애, durable event log, Redis 장애 후 recovery |
| `CentralKafkaGatewayEventBusSmokeTest` | opt-in 실제 Kafka producer/consumer와 두 node payload 전달 | Message outbox부터 독립 Message service까지의 end-to-end |
| `CentralRedisGatewayFanoutSmokeTest` | central Redis를 통한 두 node stream fanout/resume | Message outbox와 Gateway Control의 durable ACK |

중앙 Kafka smoke는 `DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE=true`일 때만 실행된다. Compose의 Redpanda는 `ms-kafka`로 `127.0.0.1:29092`에 advertise되며, 서비스 컨테이너 환경에는 이 endpoint가 자동 주입되지 않는다.

## 7. C3 구현 전 해결해야 하는 결함

| 우선순위 | 결함 | 영향 | 최소 해결 기준 |
|---|---|---|---|
| P1 | MessagePublished eventId가 Gateway에서 새 UUID로 교체됨 | end-to-end dedup, replay, audit 불가 | command/envelope에 원본 eventId를 필수 전달하고 모든 consumer에서 보존 |
| P1 | Kafka producer future를 기다리지 않음 | broker 저장 전 outbox published 가능, 유실 | `acks=all` + idempotence + bounded timeout ACK 성공 후에만 `published_at` 갱신 |
| P1 | Message service에 relay/Kafka wiring이 없음 | 독립 pod에서 이벤트가 발행되지 않음 | service application context, dependency, profile/env, compose wiring을 실제로 검증 |
| P1 | Consumer inbox/retry가 없음 | 중복 처리·listener 실패 유실·재처리 불가 | `(consumer_name,event_id)` unique inbox와 projection transaction, 3회 retry 후 DLQ |
| P1 | 현재 event에 schema/routing/trace metadata가 없음 | 계약 호환·partition order·관측성 불가 | versioned `EventEnvelope`와 topic별 key 계약 추가 |
| P1 | contentSnapshot/MessageLookup 정책 충돌 | replay 시 과거 표시 내용 불변성 보장 불가 | snapshot 포함 또는 versioned authenticated snapshot API 중 하나를 ADR로 확정 |
| P2 | 현재 Kafka/Redis DLQ는 metadata만 있고 replay/drain 계약이 없음 | 운영자가 poison event를 복구할 수 없음 | operator-only list/replay, 원본 payload 입력 금지, audit/retention 정의 |
| P2 | Kafka listener ack/retry 설정이 프레임워크 기본값에 의존 | 실패가 재처리인지 skip인지 불명확 | container ack/retry/error handler를 설정 파일·테스트에 고정 |
| P2 | Redis Stream ACK가 listener 실패 후에도 실행됨 | Redis 경로는 at-most-once에 가까움 | 최종 경로에서 Redis Stream 제거 또는 retry/PEL 정책을 별도 고정 |

## 8. T171-C0 목표에 맞는 최소 공통 계약

아래는 C3 구현 시 사용할 최소 계약이다. 이 보고서는 코드 변경을 하지 않으므로 실제 타입 추가는 C3 branch에서 수행한다.

```json
{
  "eventId": "UUID",
  "eventType": "MessagePublished",
  "schemaVersion": 1,
  "occurredAt": "Instant",
  "producerService": "message",
  "aggregateType": "message",
  "aggregateId": "UUID(messageId)",
  "routingKey": "chatRoomId",
  "region": "kr-seoul-1",
  "traceId": "TRACE-ID",
  "payload": {}
}
```

고정 규칙:

- `eventId`는 Message DB transaction에서 한 번 생성하며 topic retry, Gateway inbox, Gateway event log, DLQ audit에서 동일하게 사용한다.
- `schemaVersion`은 JSON field 추가만 같은 버전에서 허용하고 required/nullability/의미 변경은 새 버전 topic으로 분리한다.
- `discord.message.published.v1`의 Kafka key는 `chatRoomId`다. 현재 코드의 `channelId`를 chat room으로 사용할지 thread 포함 별도 식별자로 승격할지는 C3에서 명시한다.
- event payload는 현재 `MessagePublished`에 없는 `contentSnapshot`과 `visibilityVersion`의 source/immutability를 확정한 뒤 작성한다. 임의로 Profile service를 동기 호출하지 않는다.
- producer는 outbox row를 claim하고 Kafka broker ACK를 기다린다. timeout/exception은 lease를 유지하지 않고 retry 가능한 상태로 되돌린다.
- consumer는 `(consumerName,eventId)` unique inbox insert와 projection mutation을 같은 DB transaction으로 수행한다. duplicate는 business side effect 없이 ACK한다.
- malformed, unknown schema, payload hash mismatch는 raw body 없이 error code, eventId, schema, source, size/hash prefix만 DLQ에 남긴다.
- Redis는 owner-specific wake-up과 lease만 담당하며 event log·ACK·replay의 source of truth가 아니다.

## 9. 구현 acceptance 체크리스트

1. `MessagePublished.eventId`를 지정해 publish한 값이 Kafka key/value, Gateway inbox, Gateway event log에 동일하게 존재한다.
2. Kafka broker가 ACK를 반환하지 않으면 outbox `published_at`이 채워지지 않고 lease 만료 후 재시도된다.
3. 동일 event가 두 번 소비되어도 inbox unique 제약으로 projection/message delivery가 한 번만 기록된다.
4. consumer 실패는 `1s → 10s → 60s` retry 후 metadata-only DLQ로 이동하며 raw payload/token/exception text가 남지 않는다.
5. `contentSnapshot`과 `visibilityVersion`의 source가 테스트로 고정되고, replay 시 원본 event와 payload hash가 다르면 격리된다.
6. `:backend:services:message:test` 또는 service context test에서 relay worker, DB outbox adapter, Kafka profile bean이 실제로 등록된다.
7. opt-in central Kafka smoke가 `ms-kafka`에 연결되어 두 logical consumer가 같은 eventId를 관찰한다.
8. Redis profile은 최종 Message delivery 경로로 선택되지 않거나, 선택될 경우 Stream retry/ACK/drain semantics를 별도 acceptance로 통과한다.

## 10. 권장 구현 순서

```text
C3-1 EventEnvelope + MessagePublished versioned payload/ID contract
  -> C3-2 Message service application wiring + JDBC outbox relay
  -> C3-3 Kafka producer ACK/partition/retry configuration
  -> C3-4 Gateway consumer inbox + eventId-preserving adapter
  -> C3-5 metadata-only DLQ/replay operator boundary
  -> C3-6 real central Kafka smoke + duplicate/failure/drain evidence
```

이 순서 전에는 Gateway ACK/resume나 Kubernetes HPA를 구현해도 Message event의 원자성과 중복 방지가 증명되지 않는다.
