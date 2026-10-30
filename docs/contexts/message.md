# Message

메시지의 발행, 수정, 삭제, 고정과 그 사실을 다른 context에 알리는 일을 소유하는 context다. 사람은 Identity의 `UserId`로, 수신처는 Community의 `ChannelId`로만 안다. 이 파일은 2026-10-30 이전의 루트 `CONTEXT.md` 내용을 이어받았다.

## Language

### 메시지와 수신처

**Message**:
작성자가 수신처 하나에 발행한 커뮤니케이션 기록이다. 본문과 구조화된 멘션 대상을 함께 가진다. 수정, 삭제, 고정 같은 상태 바뀜은 Message 자신이 수행한다.
_Avoid_: post, chat, 유스케이스가 생성자를 직접 불러 상태를 바꾸는 것

**MessageAuthor**:
메시지를 발행한 주체다. 사용자뿐 아니라 봇, 웹훅, 시스템 같은 발행 주체를 구분할 수 있어야 한다. 사용자 작성자는 `UserId`를 가진다.
_Avoid_: sender, writer, user(작성자를 뜻할 때)

**AuthorDisplayName**:
응답에 함께 실리는 작성자의 표시 이름이다. Identity의 DisplayName을 번역해 온 값이고, 작성자가 없으면 비어 있다.

**MessageTarget**:
메시지가 발행되는 수신처다. Guild 채널, DirectMessageChannel, GroupDm, Thread처럼 메시지가 도착할 수 있는 목적지를 표현한다. 수신처가 Guild에 속하면 `GuildId`를, 언제나 `ChannelId`를 줄 수 있어야 한다.
_Avoid_: destination, room, 바깥에서 instanceof로 종류를 판별하는 것

**MessageStorageShape**:
도메인 모델은 `MessageAuthor`, `MessageTarget` 같은 타입으로 표현하지만, DB 저장은 조회와 권한 필터링에 필요한 값을 명시 컬럼으로 둔다. 메시지 저장소는 author type/id, target type, guild/channel/thread/conversation 식별자를 컬럼으로 관리한다.

**MessageContent**:
메시지의 정규화된 본문 값이다. 텍스트 제약은 검증하지만, 멘션을 직접 파싱하지는 않는다.

### 멘션

**MessageMentionTarget**:
메시지에서 언급된 구조화 대상이다. 서버가 본문 문자열에서 추출하는 값이 아니라 편집기가 메타데이터로 제출하는 값이며, 사용자, 역할, 채널, `@everyone`, `@here`를 포함한다. 알림, 권한 검증, 발행 이벤트에서 멘션으로 취급되는 기준은 본문 문자열이 아니라 이 구조화 값이다.
_Avoid_: mention string, 멘션을 문자열 목록으로 다루는 것

**MessageMentionAnchor**:
멘션 대상이 본문 어느 위치에 표시되는지 나타내는 위치 정보다. 첫 메시지 발행 구현에는 포함하지 않고, 숨은 멘션 방지나 rich editor 재현이 필요해지는 후속 단계에서 추가한다.

### 유스케이스와 정책

**PublishMessageUseCase**:
발행자, 수신처, 본문, 멘션 대상을 받아 메시지를 생성하고 발행 이벤트를 기록하는 애플리케이션 유스케이스다. Guild 채널, DM 채널, 스레드 메시지 모두 이 유스케이스로 발행한다.
_Avoid_: DM·스레드 메시지를 별도 table과 SQL로 저장하는 것(지금 코드는 그렇다. U4에서 바꾼다)

**EditMessageUseCase**:
기존 메시지의 본문과 멘션 대상을 변경하는 애플리케이션 유스케이스다. 메시지 작성자와 수신처 권한, 수정 가능 상태, 수정 이력을 함께 다룬다.

**DeleteMessageUseCase**:
기존 메시지를 삭제 상태로 전환하는 애플리케이션 유스케이스다. 삭제 권한과 삭제 가능 상태를 검증한다.

**PinMessageUseCase**:
메시지의 고정 상태를 변경하는 애플리케이션 유스케이스다. 고정 권한과 채널 수신처 조건을 검증한다.

**MessagePublishGuard**:
메시지 발행자가 특정 수신처에 메시지를 발행할 수 있는지 판단하는 정책이다.

**MessageMutationGuard**:
기존 메시지를 수정, 삭제, 고정, 고정 해제할 수 있는지 판단하는 정책이다. mutation 종류별 권한이 다를 수 있으므로 행위별 검증 메서드를 가진다.

**MessageContentPolicy**:
본문과 제출된 멘션 대상이 허용되는지 검증하는 정책이다. 모더레이션, 멘션 권한, 대상 존재 여부, 특수 멘션 권한을 다룬다.

### 발행과 전달

**IdempotencyKey**:
동일한 발행 요청이 재시도될 때 중복 메시지가 생성되지 않도록 클라이언트가 제출하는 발송 시도 식별자다.
- 메시지 내용으로 만든 해시가 아니다.
- 화면/클라이언트가 전송 버튼을 누르는 발송 시도마다 새로 생성해서 요청에 포함한다. 서버는 이 값을 생성하지 않는다.
- 메시지 발행에서는 `MessageAuthor`, `MessageTarget`, `IdempotencyKey` 조합으로 같은 발송 시도의 재시도인지 식별한다.
- 같은 키는 같은 발행 의도를 의미해야 한다.

_Avoid_: clientEventId(같은 값을 다른 이름으로 함께 보내는 것), request id

**MessagePublished**:
메시지가 성공적으로 발행됐다는 도메인 이벤트다. 발행된 메시지의 식별자, 작성자, 수신처, 멘션 대상을 표현하며, 본문은 메시지 저장소/조회 모델의 책임으로 둔다.

**MessagePublication**:
MessagePublished를 Kafka로 보내기 위해 메시지와 같은 트랜잭션에 저장하는 outbox 행이다. 릴레이가 임대(lease)하고, 발행에 성공하면 완료로 표시하며, 실패하면 재시도하다가 한도를 넘으면 실패 보관함(dead letter)으로 보낸다.
_Avoid_: outbox event(단독), queue item

**ChannelMessageReader**:
채널 메시지 본문과 상세 정보를 조회하는 읽기 경계다. `MessagePublished` 같은 이벤트 소비자가 본문이 필요할 때 쓰며, 채널 가시성, 권한 필터링, 본문 마스킹 정책을 적용할 수 있어야 한다.
_Avoid_: 이벤트 소비자가 가시성 규칙 없이 `requireMessage`로 원본을 읽는 것. 지금 코드에는 이 이름의 타입이 없고, 기능이 `ChannelMessageQueryService`, `MessageLookupPort` 등에 흩어져 있다(U2·U4에서 정리한다).

### 순서와 읽음 위치

**ChannelSequence**:
채널 하나 안에서 메시지마다 서버가 매기는 증가 번호다. 메시지 순서와 읽음 위치의 기준이다.
_Avoid_: sequence(단독), version, serverVersion, Gateway의 **UserEventSequence**

**ReadMarker**:
User 한 명이 채널 하나에서 마지막으로 읽은 **ChannelSequence**다. 줄어들지 않으며, 그 채널에 저장된 최신 번호를 넘을 수 없다.
_Avoid_: last seen, 화면이 붙인 임시 번호(localSequence)로 읽음 위치를 보내는 것

## Relationships

- **Message** 하나는 **MessageAuthor** 하나와 **MessageTarget** 하나를 가진다.
- **Message**는 **MessageMentionTarget**을 0개 이상 가진다.
- **Message**가 저장될 때 같은 트랜잭션으로 **MessagePublication**이 하나 생긴다.
- **ReadMarker**는 (User, Channel)마다 하나다.

## Example dialogue

> **Dev:** "Gateway가 MessagePublished를 받으면 본문은 어디서 가져와요?"
> **Domain expert:** "이벤트에는 본문이 없습니다. **ChannelMessageReader**로 읽어야 해요. 그래야 받는 사람에게 보여도 되는지, 가려야 하는지를 같은 규칙으로 판단합니다."
> **Dev:** "DM은 따로 table이 있으니 거기 바로 넣으면 되죠?"
> **Domain expert:** "DM도 **MessageTarget**의 한 종류입니다. **PublishMessageUseCase**로 발행해야 멘션, 자동 모더레이션, **IdempotencyKey**가 똑같이 적용됩니다."

## Flagged ambiguities

- `sequence`가 다섯 가지 뜻으로 쓰였다. Message에서는 **ChannelSequence**와 **ReadMarker**만 쓴다. Gateway의 사용자별 번호는 **UserEventSequence**, DM 채널의 `last_sequence`도 **ChannelSequence**다.
- 화면은 `idempotencyKey`와 `clientEventId`에 같은 값을 보낸다. 이름은 **IdempotencyKey** 하나로 정리한다.
- 화면의 `ShellMessage.author`는 작성자 자체가 아니라 표시 문자열이다. 화면 타입은 `authorId`와 **AuthorDisplayName**을 함께 가진다.
