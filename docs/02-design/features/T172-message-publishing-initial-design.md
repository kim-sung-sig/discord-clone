# T172 메시지 발행 초기 설계

작성일: 2026-06-03
상태: 사용자 검토용 초안
언어: 한국어
범위: 메시지 도메인 모델과 메시지 주요 흐름 전체 리뉴얼

## 1. 다시 잡은 방향

이번 설계는 전역 코어, 플러그인 플랫폼, 범용 CommandBus를 만들지 않는다.

먼저 하나의 실제 행위만 본다.

```text
메시지 발행
```

여기서 추상화할 것은 "백엔드 전체"가 아니라 메시지 발행에 필요한 도메인 경계다.

```text
누가 보내는가?        -> MessageAuthor
어디로 보내는가?      -> MessageTarget
무엇을 보내는가?      -> MessageContent
보낼 수 있는가?       -> MessagePublishGuard
내용이 허용되는가?    -> MessageContentPolicy
어디에 저장하는가?    -> MessageStore
발행 사실을 남기는가? -> MessagePublicationOutbox
```

## 2. 기존 모델에서 시작

현재 이미 존재하는 도메인 모델은 `Message`다.

```java
public record Message(
    UUID id,
    MessageAuthor author,
    MessageTarget target,
    MessageContent content,
    List<MessageMentionTarget> mentions,
    boolean pinned,
    boolean deleted,
    List<MessageEdit> editHistory,
    Instant createdAt,
    Instant updatedAt
) {
}
```

초기 설계는 새 generic core를 만들지 않고 이 모델을 중심으로 한다.

기존 `Message`가 `guildId`, `channelId`, `authorId`, `String content`를 직접 들고 있었다면, 이번 설계에서는 `MessageAuthor`, `MessageTarget`, `MessageContent`를 직접 품는 쪽으로 바꾼다.

```text
기존:
Message(id, guildId, channelId, authorId, content, ...)

수정:
Message(id, author, target, content, ...)
```

이 선택은 첫 구현 범위를 키운다. 대신 도메인 모델이 처음부터 "누가, 어디로, 무엇을" 보냈는지 명확히 말할 수 있다.

첫 목표는 기존 메시지 모델과 메시지 주요 흐름을 새 도메인 언어 기준으로 다시 세우는 것이다.

이번 리뉴얼에서는 기존 `guildId/channelId/authorId/String content` 중심 설계를 보존하지 않는다. create, read, edit, delete, pin/unpin을 새 `Message`, `MessageAuthor`, `MessageTarget`, `MessageContent`, `MessageMentionTarget` 기준으로 모두 맞춘다.

현재 controller가 아는 것:

```text
인증 사용자
guild 조회
발송 권한
요청 검증
moderation
메시지 생성
응답 매핑
```

새 구조에서 컨트롤러는 HTTP 어댑터만 맡고, 메시지 생성/수정/삭제/고정 판단과 저장은 유스케이스가 맡는다.

## 3. 목표 흐름

```text
HTTP Adapter
  -> PublishMessageUseCase
  -> EditMessageUseCase
  -> DeleteMessageUseCase
  -> PinMessageUseCase
  -> ChannelMessageReader
  -> MessagePublishGuard
  -> MessageContentPolicy
  -> MessageStore
  -> MessagePublicationOutbox
  -> MessagePublished
```

각 이름의 의미:

| 이름 | 역할 |
| --- | --- |
| `PublishMessageUseCase` | 메시지 발행 행위 |
| `EditMessageUseCase` | 메시지 수정 행위 |
| `DeleteMessageUseCase` | 메시지 삭제 행위 |
| `PinMessageUseCase` | 메시지 고정/고정 해제 행위 |
| `ChannelMessageReader` | 채널 메시지 조회 경계 |
| `MessageAuthor` | 메시지를 발행하는 주체 |
| `MessageTarget` | 메시지가 도착할 수신처 |
| `MessageContent` | 메시지 내용 값 |
| `MessagePublishGuard` | 대상에 메시지를 보낼 수 있는지 판단 |
| `MessageContentPolicy` | 내용 검증, automod, 금지어 등 발행 가능 여부 판단 |
| `MessageStore` | 메시지 저장 구현 경계 |
| `MessagePublicationOutbox` | 메시지 발행 이벤트 저장 경계 |
| `MessagePublished` | 메시지가 발행됐다는 도메인 이벤트 |

`MessagePublisher`라는 이름은 쓰지 않는다. "발행 행위"와 "발행 주체"가 충돌하기 때문이다.

## 4. 패키지 초안

처음에는 `backend/modules/message` 안에서 작게 시작한다.

```text
backend/modules/message/src/main/java/com/example/discord/message/
  Message.java
  MessageEdit.java
  MessagePage.java

  MessageAuthor.java
  UserMessageAuthor.java
  BotMessageAuthor.java
  WebhookMessageAuthor.java
  SystemMessageAuthor.java
  MessageTarget.java
  ChannelMessageTarget.java
  DirectMessageTarget.java
  ThreadMessageTarget.java
  MessageContent.java
  IdempotencyKey.java
  MessageMentionTarget.java
  UserMentionTarget.java
  RoleMentionTarget.java
  ChannelMentionTarget.java
  SpecialMentionTarget.java
  SpecialMentionKind.java

  PublishMessageUseCase.java
  PublishMessageRequest.java
  PublishMessageResult.java
  EditMessageUseCase.java
  EditMessageRequest.java
  EditMessageResult.java
  DeleteMessageUseCase.java
  DeleteMessageRequest.java
  DeleteMessageResult.java
  PinMessageUseCase.java
  PinMessageRequest.java
  PinMessageResult.java
  ChannelMessageReader.java
  ChannelMessageQuery.java

  MessagePublishGuard.java
  MessageMutationGuard.java
  MessageContentPolicy.java
  MessagePublishRejectedException.java
  MessageStore.java
  MessagePublicationOutbox.java
  MessagePublished.java
  DefaultPublishMessageUseCase.java
```

boot adapter 쪽:

```text
backend/boot/src/main/java/com/example/discord/message/
  MessageController.java
  MessagePublishingConfiguration.java
  GuildMessagePublishGuard.java
  ModerationMessageContentPolicy.java
  JdbcMessageStore.java
  JdbcMessagePublicationOutbox.java
```

아직 별도 `backend/core` 모듈은 만들지 않는다.

메시지 저장/아웃박스는 첫 구현부터 DB 기반 adapter를 기준으로 한다. 기존 메시지 서비스는 새 설계의 기준이나 호환 대상이 아니다. 테스트용 fake는 테스트 코드 안에서만 둔다.

## 5. 발행자, 수신처, 내용

메시지 발행의 핵심 도메인 값은 세 개로 시작한다.

```text
MessageAuthor  = 누가 보내는가
MessageTarget  = 어디로 보내는가
MessageContent = 무엇을 보내는가
```

### 5.1 MessageAuthor

```java
public sealed interface MessageAuthor
    permits UserMessageAuthor, BotMessageAuthor, WebhookMessageAuthor, SystemMessageAuthor {
}
```

초기에는 일반 사용자 발행자만 controller에 연결한다.

```java
public record UserMessageAuthor(UUID userId) implements MessageAuthor {
    public UserMessageAuthor {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
```

Bot, webhook, system actor는 타입으로만 열어둔다.

```java
public record BotMessageAuthor(UUID botId) implements MessageAuthor {
    public BotMessageAuthor {
        Objects.requireNonNull(botId, "botId must not be null");
    }
}
```

```java
public record WebhookMessageAuthor(UUID webhookId) implements MessageAuthor {
    public WebhookMessageAuthor {
        Objects.requireNonNull(webhookId, "webhookId must not be null");
    }
}
```

```java
public record SystemMessageAuthor(String reason) implements MessageAuthor {
    public SystemMessageAuthor {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
```

첫 구현에서 실제 메시지 발행 endpoint는 `UserMessageAuthor`만 만든다. 다른 발행자는 타입 계약만 먼저 잡고 endpoint 연결은 다음 슬라이스로 미룬다.

### 5.2 MessageTarget

수신처는 처음부터 sealed hierarchy로 둔다.

```java
public sealed interface MessageTarget
    permits ChannelMessageTarget, DirectMessageTarget, ThreadMessageTarget {
}
```

채널 메시지:

```java
public record ChannelMessageTarget(
    UUID guildId,
    UUID channelId
) implements MessageTarget {
    public ChannelMessageTarget {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
    }
}
```

DM 메시지:

```java
public record DirectMessageTarget(
    UUID conversationId,
    UUID recipientId
) implements MessageTarget {
    public DirectMessageTarget {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(recipientId, "recipientId must not be null");
    }
}
```

Thread 메시지:

```java
public record ThreadMessageTarget(
    UUID guildId,
    UUID channelId,
    UUID threadId
) implements MessageTarget {
    public ThreadMessageTarget {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");
    }
}
```

첫 구현은 `ChannelMessageTarget`만 controller에서 만들 수 있다. `DirectMessageTarget`, `ThreadMessageTarget`은 타입으로만 열어두고 실제 endpoint 연결은 다음 슬라이스에서 한다.

### 5.3 MessageContent

```java
public record MessageContent(String value) {
    public MessageContent {
        Objects.requireNonNull(value, "value must not be null");

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("message content must not be blank");
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("message content must not exceed 2000 characters");
        }

        value = normalized;
    }
}
```

### 5.4 IdempotencyKey

`idempotencyKey`도 문자열이 아니라 값 객체로 다룬다.

```java
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("idempotency key must not exceed 128 characters");
        }

        value = normalized;
    }
}
```

같은 `IdempotencyKey`는 같은 발행 의도를 의미해야 한다.

`IdempotencyKey`는 메시지 내용으로 만든 해시가 아니다. 백엔드가 생성하는 값도 아니다. 화면/클라이언트가 발송 버튼을 누르는 발송 시도마다 새로 생성해서 요청에 포함한다.

```text
나쁜 방식:
idempotencyKey = hash(content)

올바른 방식:
idempotencyKey = 클라이언트가 발송 시도마다 새로 생성한 고유 key
```

따라서 같은 본문이라도 다른 발송 시도라면 다른 `IdempotencyKey`를 가진다. 재시도할 때만 같은 `IdempotencyKey`를 재사용한다. 서버는 key를 만들지 않고, 전달받은 key를 검증하고 보존 기간 안에서 중복/충돌을 판정한다.

멘션은 `MessageContent` 내부에서 추출하지 않는다.

```text
MessageContent = 문자열 검증과 정규화
mentions = 화면/편집기가 갈무리해서 제출한 MessageMentionTarget 목록
```

## 6. 멘션 대상

멘션은 문자열 목록이 아니라 target 목록이다.

```java
public sealed interface MessageMentionTarget
    permits UserMentionTarget, RoleMentionTarget, ChannelMentionTarget, SpecialMentionTarget {
}
```

유저 멘션:

```java
public record UserMentionTarget(UUID userId) implements MessageMentionTarget {
    public UserMentionTarget {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
```

역할 멘션:

```java
public record RoleMentionTarget(UUID roleId) implements MessageMentionTarget {
    public RoleMentionTarget {
        Objects.requireNonNull(roleId, "roleId must not be null");
    }
}
```

채널 멘션:

```java
public record ChannelMentionTarget(UUID channelId) implements MessageMentionTarget {
    public ChannelMentionTarget {
        Objects.requireNonNull(channelId, "channelId must not be null");
    }
}
```

특수 멘션:

```java
public enum SpecialMentionKind {
    EVERYONE,
    HERE
}
```

```java
public record SpecialMentionTarget(SpecialMentionKind kind) implements MessageMentionTarget {
    public SpecialMentionTarget {
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
```

첫 구현에서 클라이언트/편집기가 제출할 수 있는 mention target:

```text
<@userId>     -> UserMentionTarget
<@&roleId>    -> RoleMentionTarget
<#channelId>  -> ChannelMentionTarget
@everyone     -> SpecialMentionTarget(EVERYONE)
@here         -> SpecialMentionTarget(HERE)
```

동일 target이 여러 번 제출되면 서버가 첫 등장 순서만 남기고 중복은 제거한다. 알림 전파에서 같은 대상에게 중복 알림을 만들 필요가 없기 때문이다.

`@everyone`, `@here`는 포함한다. 이 둘은 단순 문자열이 아니라 권한 검증, 알림 전파, 속도 제한에서 별도 정책 대상이 될 수 있으므로 `SpecialMentionTarget`으로 명시한다.

서버는 첫 구현에서 content 문자열을 파싱해 mention을 찾지 않는다. 서버 책임은 제출된 mention target이 유효하고 허용되는지 검증하는 것이다.

불일치 처리 기준:

```text
mentions에 있으면:
  -> 알림 대상
  -> 권한 검증 대상
  -> MessagePublished에 실리는 대상

content에만 있고 mentions에 없으면:
  -> 일반 텍스트
  -> 알림 없음
  -> 멘션 권한 검증 없음
```

즉 알림, 권한, 이벤트의 진실은 `mentions`이고, 화면 표시의 진실은 `content`다. 첫 구현에서는 서버가 content와 mentions를 엄격히 대조하지 않는다.

대신 `MessageContentPolicy`는 제출된 mentions에 대해 다음 검증을 반드시 수행한다.

```text
mentions 수 제한
target 존재 여부
target 접근 권한
사용자/역할/채널 mention 가능 여부
@everyone/@here 권한
```

## 7. 책임 분리

메시지 발행에서 mention 관련 책임은 다음처럼 나눈다.

| 영역 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| 프론트엔드/편집기 | 사용자가 선택한 사용자, 역할, 채널, 특수 멘션을 구조화해서 제출한다. | 권한 최종 판단 |
| HTTP 어댑터 | 요청 DTO를 `MessageContent`, `MessageMentionTarget` 목록으로 변환한다. | 도메인 정책 판단 |
| `PublishMessageUseCase` | author, target, content, mentions를 하나의 발행 흐름으로 저장하고 outbox 이벤트를 남긴다. | content 문자열 파싱, 권한 세부 판단 |
| `MessagePublishGuard` | author가 target에 메시지를 발행할 수 있는지 판단한다. | 본문/멘션 정책 판단 |
| `MessageContentPolicy` | content와 mentions가 target 안에서 허용되는지 검증한다. | 저장, 이벤트 발행 |
| `MessageStore` | `Message`를 저장한다. | 발행 가능 여부 판단 |
| `MessagePublicationOutbox` | `MessagePublished` 이벤트를 저장한다. | 이벤트 전파 직접 실행 |

이 결정으로 `MessageMentionExtractor`는 첫 구현 범위에서 제거한다. 나중에 legacy client, paste fallback, markdown import가 필요해지면 별도 parser adapter로 추가한다.

## 8. 메시지 발행 API

핵심 use case는 이것 하나다.

```java
public interface PublishMessageUseCase {
    PublishMessageResult publish(PublishMessageRequest request);
}
```

요청:

```java
public record PublishMessageRequest(
    MessageAuthor author,
    MessageTarget target,
    MessageContent content,
    List<MessageMentionTarget> mentions,
    IdempotencyKey idempotencyKey,
    String correlationId
) {
    public PublishMessageRequest {
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
```

결과:

```java
public record PublishMessageResult(Message message) {
    public PublishMessageResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}
```

범용 command envelope는 아직 필요 없다.

## 9. 발행 정책

수신처 권한과 내용 검사는 분리한다.

```java
public interface MessagePublishGuard {
    void requireCanPublish(MessageAuthor author, MessageTarget target);
}
```

```java
public interface MessageContentPolicy {
    void review(
        MessageAuthor author,
        MessageTarget target,
        MessageContent content,
        List<MessageMentionTarget> mentions
    );
}
```

mutation 권한은 하나의 guard로 둔다.

```java
public interface MessageMutationGuard {
    void requireCanEdit(MessageAuthor actor, Message message);

    void requireCanDelete(MessageAuthor actor, Message message);

    void requireCanPin(MessageAuthor actor, Message message);
}
```

`MessageEditGuard`, `MessageDeleteGuard`, `MessagePinGuard`로 나누지 않는다. edit/delete/pin은 모두 기존 `Message`를 변경하는 행위이고, 권한 조건만 행위별 메서드로 분리한다.

초기 구현 연결:

```text
GuildMessagePublishGuard
  -> UserMessageAuthor + ChannelMessageTarget일 때 GuildPermissionPort.canSendMessages(...)
  -> BotMessageAuthor, WebhookMessageAuthor, SystemMessageAuthor는 아직 명시적 거절
  -> DirectMessageTarget, ThreadMessageTarget은 아직 명시적 거절

ModerationMessageContentPolicy
  -> ModerationPolicyPort.evaluateMessage(...)
  -> mentions target 유효성/권한 검증
```

이렇게 하면 `DefaultPublishMessageUseCase`는 guild service나 moderation service를 직접 모른다.

미지원 author/target 조합을 거절하는 책임도 use case가 아니라 guard가 가진다. use case는 "검사가 통과한 request는 발행할 수 있다"는 전제만 가진다.

거절 예외는 도메인 예외를 사용한다.

```java
public final class MessagePublishRejectedException extends RuntimeException {
    public MessagePublishRejectedException(String message) {
        super(message);
    }
}
```

`UnsupportedOperationException`은 쓰지 않는다. 미지원 조합도 API 관점에서는 명확한 발행 거절 사유여야 한다.

```java
throw new MessagePublishRejectedException("message target is not supported yet");
```

## 10. 저장 경계

메시지를 어디에 저장하는지는 `MessageStore`가 결정한다.

```java
public interface MessageStore {
    Message save(Message message, IdempotencyKey idempotencyKey);

    Optional<Message> find(UUID messageId);

    Message update(Message message);

    Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    );
}
```

초기 구현:

```text
JdbcMessageStore
```

테스트:

```text
FakeMessageStore
```

`FakeMessageStore`는 테스트 코드 전용이다. 운영/부트 adapter로 메모리 기반 메시지 저장소를 만들지 않는다.

`Message`는 이제 `MessageTarget`을 직접 가진다. 따라서 저장소는 channel message만 저장하는 구조가 아니라 target을 가진 message를 저장할 수 있어야 한다.

DB는 도메인 타입을 그대로 직렬화해서 숨기지 않는다. 조회와 권한 필터링에 필요한 shape를 컬럼으로 둔다.

```text
messages
  id
  author_type
  author_id
  target_type
  guild_id
  channel_id
  thread_id
  conversation_id
  content
  pinned
  deleted
  created_at
  updated_at

message_mentions
  message_id
  mention_type
  user_id
  role_id
  channel_id
  special_kind
  position

message_idempotency
  author_type
  author_id
  target_type
  guild_id
  channel_id
  thread_id
  conversation_id
  idempotency_key
  message_id
  request_hash
```

`position`은 첫 구현에서 anchor로 쓰지 않는다. 중복 제거 후 순서 보존을 위한 정렬값으로만 둔다.

`IdempotencyKey`는 첫 구현부터 실제 중복 방지에 사용한다.

```text
중복 방지 범위:
MessageAuthor + MessageTarget + IdempotencyKey
```

`IdempotencyKey`는 화면/클라이언트가 만든 발송 시도 ID다. 서버는 메시지 내용으로 key를 만들지 않고, 요청에 포함된 key를 저장/조회해서 재시도 여부를 판정한다.

같은 범위에서 이미 저장된 메시지가 있으면 새 메시지를 만들지 않고 기존 메시지를 반환한다. 이 경우 outbox에도 새 `MessagePublished`를 추가하지 않는다.

단, 같은 범위의 `IdempotencyKey`인데 요청 내용이 다르면 재시도로 보지 않는다.

```text
같은 author + target + IdempotencyKey + 같은 요청 내용
  -> 기존 Message 반환

같은 author + target + IdempotencyKey + 다른 요청 내용
  -> MessagePublishRejectedException
  -> HTTP 409 Conflict
```

요청 내용 비교 기준은 첫 구현에서 다음 값으로 제한한다.

```text
MessageContent
List<MessageMentionTarget>
```

`request_hash`는 `IdempotencyKey`를 만들기 위한 값이 아니다. 같은 `IdempotencyKey`로 서로 다른 요청 내용이 들어온 클라이언트 오용을 감지하기 위한 서버 측 검증값이다.

`message_idempotency`는 영구 보관하지 않는다.

```text
권장 보존 기간:
7일
```

보존 기간은 재시도/네트워크 장애 복구를 감당할 정도로 충분해야 하지만, 무제한으로 커지면 저장소와 인덱스 비용이 불필요하게 증가한다.

다만 첫 구현에서 실제 endpoint와 권한 검증은 다음으로 제한한다.

```text
ChannelMessageTarget만 발행 연결
DirectMessageTarget은 타입만 정의
ThreadMessageTarget은 타입만 정의
```

채널 목록/검색/조회 응답도 새 `Message` 모델을 기준으로 변환한다. 단, DM/스레드 조회 모델은 타입은 열어두되 실제 endpoint 연결은 후속으로 둔다.

기존 메시지 서비스는 폐기 대상으로 본다. create/edit/delete/pin/unpin/list/search 책임은 새 유스케이스와 reader로 분해한다. 이전 서비스의 API를 보존하기 위한 adapter 변환은 목표가 아니다.

## 11. 발행 이벤트

메시지 발행 이벤트는 하나로 시작한다.

```java
public record MessagePublished(
    UUID eventId,
    UUID messageId,
    MessageAuthor author,
    MessageTarget target,
    List<MessageMentionTarget> mentions,
    String correlationId,
    Instant occurredAt
) {
}
```

이 이벤트의 후속 흐름:

```text
MessagePublished
  -> notification
  -> gateway 전파
  -> 감사 기록
  -> 읽지 않음 조회 모델 갱신
```

`MessagePublished`는 `MessageContent`를 포함하지 않는다. 이벤트는 발행 사실과 전파/조회 모델 갱신에 필요한 최소 메타데이터만 담고, 본문은 메시지 저장소와 조회 모델의 책임으로 둔다.

이 선택은 outbox 이벤트 크기를 작게 유지하고, 본문이 내부 이벤트나 로그 경로로 과도하게 복제되는 것을 줄인다.

본문이 필요한 소비자는 `messageId` 기준으로 `ChannelMessageReader`를 사용한다. 이 읽기 경계는 권한이 적용된 조회 모델을 바라본다.

외부 웹훅, 감사 로그, 운영 로그로 내보낼 때도 `MessagePublished`를 그대로 재사용하지 않고 별도 투영 모델과 본문 마스킹 정책을 거친다.

하지만 첫 구현에서는 subscriber를 만들지 않는다. 먼저 이벤트가 저장되는지만 본다.

## 12. Outbox

이벤트 유실 방지는 메시지 발행 전용 outbox로 시작한다.

```java
public interface MessagePublicationOutbox {
    void append(MessagePublished event);
}
```

초기 구현:

```text
JdbcMessagePublicationOutbox
```

테스트:

```text
FakeMessagePublicationOutbox
```

`FakeMessagePublicationOutbox`는 테스트 코드 전용이다. 처음부터 Kafka를 호출하지 않고, 먼저 DB transaction 안에서 메시지 저장과 이벤트 저장을 같은 흐름에 묶는다.

향후 DB transaction:

```text
transaction begin
  MessageStore.save(message, idempotencyKey)
  MessagePublicationOutbox.append(event)
commit
```

## 13. DefaultPublishMessageUseCase

구현체 형태는 이 정도다.

```java
public final class DefaultPublishMessageUseCase implements PublishMessageUseCase {
    private final MessagePublishGuard publishGuard;
    private final MessageContentPolicy contentPolicy;
    private final MessageStore messages;
    private final MessagePublicationOutbox outbox;
    private final Clock clock;

    @Override
    public PublishMessageResult publish(PublishMessageRequest request) {
        MessageAuthor author = request.author();
        MessageTarget target = request.target();
        MessageContent content = request.content();
        List<MessageMentionTarget> mentions = distinctMentions(request.mentions());
        IdempotencyKey idempotencyKey = request.idempotencyKey();

        Optional<Message> existing = messages.findByIdempotencyKey(author, target, idempotencyKey);
        if (existing.isPresent()) {
            Message previous = existing.get();
            requireSamePayload(previous, content, mentions);
            return new PublishMessageResult(previous);
        }

        publishGuard.requireCanPublish(author, target);
        contentPolicy.review(author, target, content, mentions);

        Instant now = clock.instant();

        Message message = new Message(
            UUID.randomUUID(),
            author,
            target,
            content,
            mentions,
            false,
            false,
            List.of(),
            now,
            now
        );

        Message saved = messages.save(message, idempotencyKey);

        outbox.append(new MessagePublished(
            UUID.randomUUID(),
            saved.id(),
            saved.author(),
            saved.target(),
            saved.mentions(),
            request.correlationId(),
            now
        ));

        return new PublishMessageResult(saved);
    }
}
```

초기 구현에서 `DefaultPublishMessageUseCase`는 `MessageAuthor`와 `MessageTarget`의 구체 타입을 switch하지 않는다. 미지원 조합은 `MessagePublishGuard`가 발행 전에 거절한다.

`distinctMentions(...)`는 동일 `MessageMentionTarget`의 중복만 제거한다. mention target이 실제로 존재하는지, 이 author가 mention할 수 있는지는 `MessageContentPolicy`가 판단한다.

`IdempotencyKey` 값 객체는 null/blank 값을 거절한다. 메시지 발행은 재시도 가능한 mutation이므로 첫 구현부터 중복 방지 키를 필수로 본다.

`requireSamePayload(...)`는 기존 메시지의 `MessageContent`, `List<MessageMentionTarget>`이 현재 요청과 같은지 확인한다. 다르면 `MessagePublishRejectedException`을 던지고 HTTP 어댑터가 `409 Conflict`로 매핑한다.

이것이 이번 설계의 중심이다.

## 14. Controller 변경 후 모습

현재 controller create는 이런 방향으로 줄인다.

```java
@PostMapping
ResponseEntity<MessageResponse> create(
    @PathVariable UUID channelId,
    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
    @RequestBody MessageContentRequest request
) {
    UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
    UUID guildId = guildIdFor(channelId);

    PublishMessageResult result = publishMessage.publish(new PublishMessageRequest(
        new UserMessageAuthor(requesterId),
        new ChannelMessageTarget(guildId, channelId),
        new MessageContent(request.content()),
        mentionTargetsFrom(request.mentions()),
        new IdempotencyKey(request.idempotencyKey()),
        correlationId()
    ));

    return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(result.message()));
}
```

controller에서 빠지는 것:

```text
canSendMessages 직접 호출
moderation evaluate 직접 호출
messageService.create 직접 호출
event/outbox 직접 처리
```

`MessageResponse.from(...)`는 새 `Message` 모델을 기준으로 응답을 만든다. 채널 endpoint에서는 `ChannelMessageTarget`과 `UserMessageAuthor` 조합을 기본으로 기대한다.

## 15. 예외 매핑

도메인 예외는 HTTP 어댑터에서 4xx로 매핑한다.

```text
MessagePublishRejectedException
  -> 기본 400 Bad Request

권한 없는 발행
  -> 403 Forbidden

같은 IdempotencyKey + 다른 요청 내용
  -> 409 Conflict
```

예외 메시지는 클라이언트 복구에 필요한 수준으로만 제공하고, 내부 저장소나 정책 구현 세부는 노출하지 않는다.

## 16. CQRS는 어디까지?

이번 리뉴얼에서 CQRS는 쓰기 유스케이스와 읽기 경계를 모두 명시한다.

Write:

```text
PublishMessageUseCase
EditMessageUseCase
DeleteMessageUseCase
PinMessageUseCase
```

Read:

```text
ChannelMessageReader
MessageSearchReader
ChannelTimelineBffReader
```

후속 target 확장:

```text
DirectMessageReader
ThreadMessageReader
```

즉 BFF 조회는 쓰기 유스케이스에 섞지 않고 별도 read boundary로 둔다.

## 17. SAGA와 Scheduler

메시지 발행 자체에는 SAGA가 과하다.

SAGA 후보는 다음 단계다.

```text
attachment upload + message attach
premium entitlement
invite accept workflow
```

Scheduler도 메시지 발행에는 필요하지 않다.

Scheduler 후보:

```text
outbox retry
message retention
attachment orphan cleanup
delayed notification
```

따라서 이번 초기 설계에서는 SAGA/Scheduler를 만들지 않는다.

## 18. 첫 구현 슬라이스

1차 구현은 아래만 한다.

```text
MessageAuthor
UserMessageAuthor
BotMessageAuthor
WebhookMessageAuthor
SystemMessageAuthor
MessageTarget
ChannelMessageTarget
DirectMessageTarget
ThreadMessageTarget
MessageContent
IdempotencyKey
MessageMentionTarget
UserMentionTarget
RoleMentionTarget
ChannelMentionTarget
SpecialMentionTarget
SpecialMentionKind
PublishMessageUseCase
PublishMessageRequest
PublishMessageResult
EditMessageUseCase
EditMessageRequest
EditMessageResult
DeleteMessageUseCase
DeleteMessageRequest
DeleteMessageResult
PinMessageUseCase
PinMessageRequest
PinMessageResult
ChannelMessageReader
ChannelMessageQuery
MessagePublishGuard
MessageMutationGuard
MessageContentPolicy
MessagePublishRejectedException
MessageStore
MessagePublicationOutbox
MessagePublished
DefaultPublishMessageUseCase
```

테스트:

```text
빈 본문은 거절한다
최대 길이를 넘은 본문은 거절한다
null author는 거절한다
null target은 거절한다
빈 IdempotencyKey는 거절한다
권한 없는 사용자의 채널 발행은 guard가 차단한다
bot author는 구현 전까지 거절한다
webhook author는 구현 전까지 거절한다
system author는 구현 전까지 거절한다
지원하지 않는 author/target 조합은 MessagePublishRejectedException으로 거절한다
moderation policy가 차단한 본문은 거절한다
mention target은 content에서 파싱하지 않고 요청에서 받는다
중복 mention target은 첫 등장만 남긴다
`MessageContentPolicy`가 제출된 mention target을 검증한다
성공한 채널 발행은 Message를 저장한다
저장된 Message는 UserMessageAuthor, ChannelMessageTarget, MessageContent를 가진다
성공한 채널 발행은 MessagePublished를 outbox에 추가한다
MessagePublished는 messageId, author, target, 타입화된 mentions, correlationId를 가진다
같은 author/target/IdempotencyKey 재시도는 기존 Message를 반환한다
idempotent 재시도는 outbox에 MessagePublished를 추가하지 않는다
같은 content라도 다른 IdempotencyKey면 새 Message를 저장한다
같은 IdempotencyKey에 다른 요청 내용이 들어오면 MessagePublishRejectedException으로 거절한다
같은 IdempotencyKey에 다른 요청 내용이 들어오면 HTTP 409 Conflict로 응답한다
작성자가 아닌 사용자의 수정은 거절한다
삭제된 메시지 수정은 거절한다
성공한 수정은 MessageContent, mentions, editHistory를 갱신한다
권한 없는 삭제는 거절한다
성공한 삭제는 Message를 deleted 상태로 갱신한다
권한 없는 pin/unpin은 거절한다
성공한 pin/unpin은 Message pinned 상태를 갱신한다
ChannelMessageReader는 ChannelMessageTarget 메시지만 반환한다
ChannelMessageReader는 deleted 메시지를 기본 조회에서 제외한다
direct target은 구현 전까지 거절한다
thread target은 구현 전까지 거절한다
```

아직 하지 않음:

```text
JDBC
Kafka
Redis
전역 outbox
전역 command bus
SAGA
Scheduler
DM endpoint
Thread endpoint
content 문자열 기반 서버-side mention parsing
MessageMentionExtractor
전체 core/plugin 플랫폼
```

## 19. TDD 구현 순서

구현은 도메인 값 객체 테스트부터 시작한다.

원칙:

```text
한 번에 테스트 하나
RED -> GREEN -> REFACTOR
구현 세부가 아니라 공개 생성자/공개 메서드의 동작을 검증
값 객체 불변 조건이 닫힌 뒤 메시지 생성/수정/삭제/고정/조회 유스케이스 테스트로 이동
```

초기 TDD 순서:

```text
1. MessageContent
   - 빈 본문 거절
   - 앞뒤 공백 정규화
   - 최대 길이 초과 거절

2. IdempotencyKey
   - 빈 key 거절
   - 앞뒤 공백 정규화
   - 최대 길이 초과 거절

3. MessageAuthor
   - UserMessageAuthor null userId 거절
   - BotMessageAuthor null botId 거절
   - WebhookMessageAuthor null webhookId 거절
   - SystemMessageAuthor blank reason 거절

4. MessageTarget
   - ChannelMessageTarget null guildId/channelId 거절
   - DirectMessageTarget null conversationId/recipientId 거절
   - ThreadMessageTarget null guildId/channelId/threadId 거절

5. MessageMentionTarget
   - UserMentionTarget null userId 거절
   - RoleMentionTarget null roleId 거절
   - ChannelMentionTarget null channelId 거절
   - SpecialMentionTarget null kind 거절

6. 요청 객체
   - PublishMessageRequest null author/target/content/mentions/IdempotencyKey 거절
   - PublishMessageRequest mentions 방어 복사
   - EditMessageRequest null messageId/editor/content/mentions 거절
   - DeleteMessageRequest null messageId/requester 거절
   - PinMessageRequest null messageId/requester 거절
   - ChannelMessageQuery null channel target 거절
```

이후 메시지 유스케이스 테스트로 넘어간다.

```text
7. PublishMessageUseCase
   - 권한 없는 발행 거절
   - moderation 거절
   - 성공 발행 저장
   - 성공 발행 outbox append
   - idempotent 재시도는 기존 Message 반환
   - 같은 IdempotencyKey + 다른 요청 내용은 409 매핑 가능한 예외

8. EditMessageUseCase
   - 작성자가 아닌 사용자의 수정 거절
   - 삭제된 메시지 수정 거절
   - 성공 수정은 content, mentions, editHistory 갱신

9. DeleteMessageUseCase
   - 권한 없는 삭제 거절
   - 성공 삭제는 deleted 상태 갱신

10. PinMessageUseCase
   - 권한 없는 pin/unpin 거절
   - 성공 pin/unpin은 pinned 상태 갱신

11. ChannelMessageReader
   - ChannelMessageTarget 메시지만 반환
   - deleted 메시지는 기본 조회에서 제외
   - 권한 없는 조회는 거절 또는 빈 결과
```

## 20. 설계 판단

이 설계가 맞는지 보는 기준:

1. `Message`가 중심이다.
2. `MessageAuthor`, `MessageTarget`, `MessageContent`가 메시지 발행 언어를 만든다.
3. `Message` 모델 자체가 `MessageAuthor`, `MessageTarget`, `MessageContent`를 직접 품는다.
4. use case 이름은 `PublishMessageUseCase`로 행위를 표현한다.
5. 범용 core 용어가 없다.
6. 구현체는 `MessageStore`, `MessagePublicationOutbox`, policy adapter로만 분리된다.
7. 멘션은 문자열이 아니라 `MessageMentionTarget`으로 표현한다.
8. 멘션 target은 content 문자열에서 서버가 추출하지 않고 요청으로 받는다.
9. 제출된 mentions의 유효성/권한은 `MessageContentPolicy`가 검증한다.
10. 멘션 anchor는 첫 구현에 넣지 않고 후속 확장으로 둔다.
11. `idempotencyKey`는 `IdempotencyKey` 값 객체로 표현한다.
12. `IdempotencyKey`는 첫 구현부터 중복 방지에 사용한다.
13. 중복 방지 범위는 `MessageAuthor + MessageTarget + IdempotencyKey`다.
14. 같은 `IdempotencyKey`에 다른 요청 내용이 들어오면 `409 Conflict`로 거절한다.
15. 발행 거절은 `MessagePublishRejectedException`으로 표현한다.
16. `MessagePublished`는 `MessageContent`를 포함하지 않는다.
17. 이벤트는 `MessagePublished` 하나에서 시작한다.
18. 본문이 필요한 이벤트 소비자는 `ChannelMessageReader`를 사용한다.
19. 기존 메시지 흐름은 보존 adapter가 아니라 새 모델 기준으로 전체 리뉴얼한다.
20. create/read/edit/delete/pin/unpin을 모두 새 `Message` 모델에 맞춘다.
21. 기존 메시지 서비스는 폐기 대상으로 본다.
22. 메시지 저장/아웃박스는 첫 구현부터 DB 기반 adapter를 기준으로 한다.
23. edit/delete/pin 권한은 하나의 `MessageMutationGuard`에서 행위별 메서드로 나눈다.
24. `MessageTarget`은 sealed hierarchy지만 첫 구현은 channel publish/read/mutation만 연결한다.
25. `MessageAuthor`는 sealed hierarchy지만 첫 구현은 user author만 연결한다.
26. BFF, SAGA, Scheduler는 다음 필요 지점까지 미룬다.

## 21. Grill-me 질문

다음 결정을 먼저 닫아야 한다.

```text
메시지 DB 스키마에서 `MessageAuthor`와 `MessageTarget`을 어떻게 저장할 것인가?
```

멘션 anchor 결정: 첫 구현에서는 `List<MessageMentionTarget>`를 유지하고, 표시 위치를 포함하는 `MessageMention(target, anchor)`는 후속 확장으로 둔다.

IdempotencyKey 결정: 첫 구현부터 실제 중복 방지에 사용한다. 범위는 `MessageAuthor + MessageTarget + IdempotencyKey`다. key는 서버가 메시지 내용으로 만들지 않고, 클라이언트가 발송 시도마다 새로 생성한다.

IdempotencyKey 요청 내용 충돌 결정: 같은 IdempotencyKey로 내용이 다르게 들어오면 기존 Message를 반환하지 않고 `MessagePublishRejectedException`으로 거절한다. HTTP 어댑터는 이를 `409 Conflict`로 매핑한다. `request_hash`는 key 생성용이 아니라 충돌 검증용이다.

MessagePublished 본문 결정: 포함하지 않는다. 이벤트는 발행 사실과 전파/조회 모델 갱신에 필요한 최소 메타데이터만 담고, 본문은 메시지 저장소와 조회 모델의 책임으로 둔다.

본문 조회 경계 결정: 본문이 필요한 이벤트 소비자는 `ChannelMessageReader`를 사용한다. 이 조회 경계는 권한 필터링과 본문 마스킹을 적용할 수 있어야 한다.

TDD 구현 순서 결정: 도메인 값 객체 테스트부터 시작한다. `MessageAuthor`, `MessageTarget`, `MessageContent`, `MessageMentionTarget`, `IdempotencyKey`의 불변 조건이 먼저 닫혀야 `PublishMessageUseCase` 테스트가 안정된다.

첫 RED 테스트 결정: `MessageContent`의 빈 본문 거절로 시작한다. 이후 당연한 값 검증 질문은 별도 확인 없이 TDD 계획에 따라 진행한다.

전체 변경 결정: 기존 `Message` 모델과 기존 message service API를 보존 adapter로 유지하지 않는다. create/read/edit/delete/pin/unpin을 새 `Message` 모델과 새 유스케이스/read boundary 기준으로 모두 맞춘다.

Mutation guard 결정: 하나의 `MessageMutationGuard`로 통합하고, 메서드는 `requireCanEdit`, `requireCanDelete`, `requireCanPin`으로 행위별 분리한다.

메시지 저장 결정: 기존 메시지 서비스는 폐기 대상으로 보고, 새 메시지 저장/아웃박스는 첫 구현부터 DB 기반 adapter를 기준으로 한다. 테스트에서는 테스트 코드 전용 fake만 허용한다.

현재 추천은 관계형 컬럼 중심으로 저장하는 것이다. `author_type`, `author_id`, `target_type`, `guild_id`, `channel_id`, `thread_id`, `conversation_id`처럼 조회에 필요한 값을 컬럼화하고, sealed subtype 전체를 JSON 문서 하나로만 저장하지 않는다. 채널 타임라인 조회와 권한 필터링이 핵심 경로이기 때문이다.

결정: DB는 추상 타입을 그대로 저장하지 않고 조회/권한 필터링에 필요한 값을 명시 컬럼으로 둔다.

```text
도메인 코드:
  MessageAuthor
  MessageTarget
  MessageContent
  MessageMentionTarget

DB 저장:
  author_type
  author_id
  target_type
  guild_id
  channel_id
  thread_id
  conversation_id
  content
  mention_targets
```

이유:

```text
코드는 sealed type으로 추상화할 수 있다.
하지만 DB는 타임라인 조회, 권한 필터링, 인덱스, 마이그레이션이 필요하다.
따라서 DB에는 조회 가능한 shape를 명시한다.
```
