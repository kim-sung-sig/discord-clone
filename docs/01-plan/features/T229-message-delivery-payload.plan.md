# T229 Message 전달 payload 통합

## 승인 및 진행 상태

- 상태는 **구현 완료, 독립 리뷰 수정 반영 중**이다.
- 기준은 `main`의 `d493e72`와 작업 브랜치 `message-delivery-payload`다.

## 백엔드 작업 범위

- 목표는 멘션 토큰 변환과 Gateway `MESSAGE_CREATE` payload 조립을 각각 한 곳에 두는 것이다.
- `services/message`와 `boot`의 outbox·dispatcher 병렬 구현, `MessageTarget`의 `instanceof` 판별, DM·Thread 발행 경로는 범위 밖이다.
- 참고한 Wiki는 `index.md`, `Backend Architecture.md`, `QA Infra Operations.md`, `Agent Development Guide.md`다.

## 관찰과 근거

`mentionToken(MessageMentionTarget)`의 동일한 분기가 여섯 곳에 있다. 아래 줄 번호는 기준 커밋에 해당한다.

| 파일 | 문자열 표현이 쓰이는 곳 |
| --- | --- |
| [MessageReadModel.java](../../../backend/modules/message/src/main/java/com/example/discord/message/MessageReadModel.java#L54) | 읽기 모델 |
| [JdbcMessageStore.java](../../../backend/boot/src/main/java/com/example/discord/message/JdbcMessageStore.java#L652) | DB의 `mention_tokens` |
| [MessageController.java](../../../backend/boot/src/main/java/com/example/discord/message/MessageController.java#L331) | REST 응답 |
| [MessageOutboxController.java](../../../backend/boot/src/main/java/com/example/discord/message/MessageOutboxController.java#L116) | dead-letter 운영 응답 |
| [MessageConfiguration.java](../../../backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java#L179) | 로컬 relay |
| [KafkaMessagePublishedConsumer.java](../../../backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java#L144) | Kafka 소비 |

`gatewayPayload(Message, String)` 조립도 [MessageConfiguration.java](../../../backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java#L159)와 [KafkaMessagePublishedConsumer.java](../../../backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java#L124)에 중복돼 있다. 로컬 relay는 [Gateway 발행 후 projector를 호출](../../../backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java#L95)하고, Kafka 경로의 멘션 투영은 [KafkaMessageMentionConsumer.java](../../../backend/boot/src/main/java/com/example/discord/notification/KafkaMessageMentionConsumer.java#L37)가 담당한다.

## 설계 결정

1. `backend/modules/message`에 변환 전용 `MessageMentionTokens`를 두고 정적 `token(MessageMentionTarget)`과 `tokens(List<MessageMentionTarget>)`를 제공한다. [Message 용어집](../../contexts/message.md)의 `MessageMentionTarget` 항목은 멘션을 문자열 목록으로 다루는 것을 `_Avoid_`로 정의한다. 도메인은 구조화 대상을 사용하고 문자열은 저장·응답·이벤트 경계에서 변환한다.
2. 같은 모듈의 `MessageDeliveryPayload`가 정적 `messageCreate(Message, String authorDisplayName)`로 payload를 조립한다. `MESSAGE_CREATE`의 형태는 이벤트를 발행하는 Message가 소유하고, Gateway는 전달받은 payload의 필드 의미를 해석하지 않는다.

## 계약과 처리 순서

- User는 `userId`, Role은 `roleId`, Channel은 `channelId`의 UUID 문자열을 쓴다. Special은 `kind().name()`을 `Locale.ROOT`로 소문자화한다. 토큰 목록은 입력 순서를 유지한다. 형식 변경은 DB의 기존 `mention_tokens`와 호환성을 깨뜨린다.
- `MESSAGE_CREATE` 키 집합은 `id`, `guildId`, `channelId`, `authorId`, `authorDisplayName`, `content`, `mentions`, `pinned`, `deleted`, `edited`, `createdAt`, `updatedAt`이다. 표시 이름이 null이면 해당 키를 생략한다. [GatewayEvent](../../../backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayEvent.java#L24)의 `Map.copyOf`는 null 값을 허용하지 않고 키 순서를 보장하지 않는다.
- Kafka 소비 경로의 inbox 중복 확인 → `hasAppended` 확인 → 필요한 경우 Gateway 발행 및 기존 충돌 처리 → inbox 완료 기록 순서를 유지한다. 변경 범위는 payload 조립 호출이다. [KafkaMessagePublishedConsumer.java](../../../backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java#L55)
- 로컬 dispatcher가 Gateway 발행 후 `MessageMentionProjector.project`를 호출하는 흐름과 Kafka의 별도 멘션 소비 흐름을 유지한다.
- REST `mentions` 응답의 문자열 배열 및 [OpenAPI 계약](../../api/openapi.json), 원본 `eventId`, outbox/inbox의 기존 트랜잭션과 원자성 경계를 유지한다.

## 예상 변경 파일

| 단계 | 파일 |
| --- | --- |
| 1 | `backend/modules/message/src/test/java/com/example/discord/message/MessageReadModelMentionTokenTest.java`, `backend/boot/src/test/java/com/example/discord/message/` 아래 `MessageConfigurationTest.java`와 `KafkaMessagePublishedConsumerTest.java` |
| 1 | 이 계획서와 `docs/contexts/message.md` |
| 2 | `backend/modules/message/src/main/java/com/example/discord/message/MessageMentionTokens.java` 및 토큰 변환 근거 표의 여섯 파일 |
| 3 | 같은 모듈의 `MessageDeliveryPayload.java`와 `MessageConfiguration.java`, `KafkaMessagePublishedConsumer.java` |

## 구현 및 검증 순서

1. 현재 코드에서 사용할 특성화 테스트를 추가하고 계획서와 용어집을 작성한다. 읽기 모델은 모든 Special 값을 포함해 토큰 형식과 순서를 검증한다. 두 Boot 테스트는 표시 이름 유무에 따른 12개·11개 키 집합과 네 종류의 멘션 토큰 목록을 검증한다.
2. `MessageMentionTokens`를 추가하고 기존 변환 여섯 곳을 교체한다.
3. `MessageDeliveryPayload`를 추가하고 로컬 relay와 Kafka 소비 경로의 조립 코드를 교체한다.
4. 코디네이터가 Gradle로 특성화 테스트와 백엔드 회귀를 검증한다. 백엔드 payload 호환성의 근거는 키 집합과 값을 고정하는 특성화 테스트다. `apps/web/tests/components/shell-contracts.test.ts`는 백엔드가 실제로 만든 payload를 사용하지 않으므로 프런트 회귀 확인에 사용한다.
5. 독립 리뷰가 구현과 검증 근거를 수용하면 PR을 진행하고, 수용된 경계와 계약을 Wiki에 게시한다.

## 위험과 검증 한계

- 토큰은 종류 접두사 없이 저장된다. 기존 UUID와 소문자 이름을 그대로 유지한다.
- `JdbcMessageStoreTest`는 `DISCORD_RUN_POSTGRES_TESTS=true`와 PostgreSQL을 요구한다. 기본 회귀에서의 실행 여부는 아래 검증 결과에 기록한다.
- 프런트 변경은 예정돼 있지 않으며, 프런트 테스트 결과는 회귀 확인 범위로 해석한다.

## 검증 결과 (코디네이터 실행)

- 전체 `gradlew test checkstyleMain checkstyleTest` 결과는 BUILD SUCCESSFUL(2분 31초)이다.
- 특성화 테스트 세 클래스는 변경 전 코드에서 통과했다. `MessageConfigurationTest` 9/9, `KafkaMessagePublishedConsumerTest` 10/10, `MessageReadModelMentionTokenTest` 1/1이다. 새 단위 테스트는 `MessageMentionTokensTest` 3개와 `MessageDeliveryPayloadTest` 1개가 통과했다.
- `apps/web` 전체 Vitest는 241개 통과, 8개 skip, 실패 0개다. 이 테스트는 백엔드 payload를 사용하지 않으므로 프런트 회귀 확인에 한정된다.
- `JdbcMessageStoreTest` 11개는 모두 skipped다. PostgreSQL과 `DISCORD_RUN_POSTGRES_TESTS=true`가 필요하며, 로컬에 Docker가 없고 CI도 이 변수를 설정하지 않는다. 따라서 `mention_tokens`의 저장·조회 왕복은 자동 테스트로 검증되지 않았다. 독립 리뷰어는 `JdbcMessageStore.java`의 저장 경로(`mentionTokens(Message)`에서 text 배열 바인딩)와 조회 경로(`mentionTokensFrom`)를 읽고 기존과 동일한 text 배열 바인딩과 읽기를 확인했다. 이 평가는 코드 읽기에 근거하며, 리뷰어는 저장 형식 회귀 위험을 낮게 평가했다.
- 독립 리뷰 점수는 명세 97, 품질 95이며 P0/P1은 없었다. P2 1건(특성화 테스트의 전체 payload 비교)과 nit 1건(용어집에서 형식 정의 소유권 명시)은 이 라운드에서 반영했다.
