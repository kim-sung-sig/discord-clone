# Context Map

discord-clone의 도메인 용어집 진입점이다. 서비스(bounded context)마다 용어집이 하나씩 있고, 서비스 경계는 `docs/02-design/features/T171-C0-msa-target-state.adr.md`를 따른다. 이름 결정의 근거는 `docs/02-design/features/T225-ubiquitous-language.adr.md`에 있다.

## Contexts

- [Identity](./docs/contexts/identity.md): 계정, 로그인, 토큰, 계정 전체 권한(GlobalRole), MFA
- [Community](./docs/contexts/community.md): Guild, 채널, 멤버, 역할·권한, 초대, 스레드, DirectMessageChannel·GroupDm, 친구·차단, 모더레이션, experience(Stage 등)
- [Message](./docs/contexts/message.md): 메시지 발행·수정·삭제·고정, 멘션, 멱등성, 발행 outbox, 읽음 위치
- [Gateway](./docs/contexts/gateway.md): 실시간 이벤트 순서, 연결 세션, 재개·ACK
- [Presence](./docs/contexts/presence.md): 온라인 상태, 입력 중 표시
- [Notification](./docs/contexts/notification.md): 사용자별 알림함
- [Media](./docs/contexts/media.md): 첨부 파일, 음성 상태, LiveKit 토큰

## Relationships

- **Identity → 모든 context**
  - 다른 context는 사람을 `UserId`로만 참조한다.
  - 이름·프로필이 필요하면 자기 쪽에 port를 두고 번역한다(예: Message의 `MessageAuthorDirectory`). users table을 직접 join하지 않는다.
  - 지금 그 port는 boot 안의 어댑터가 Identity 저장소를 읽어 구현한다. 서비스를 나누면 Identity의 profile 변경 이벤트로 사본을 유지한다(ADR §6).
- **Community → Message, Gateway, Media**: Community가 Guild·Channel·권한의 원본이다. 다른 context는 `GuildId`, `ChannelId`로 참조하고, 권한은 Community가 내보내는 `AuthorizationProjection` 이벤트로 받아 자기 저장소에서 판정한다.
- **Message → Gateway, Notification**
  - Message는 저장과 같은 트랜잭션으로 `MessagePublished`를 outbox에 남기고, Kafka로 내보낸다.
  - Gateway는 이를 `MESSAGE_CREATE` 이벤트로 바꿔 전달한다.
  - Notification Worker는 멘션 알림을 만든다.
  - 읽음 위치(ReadMarker)가 바뀌면 그 이벤트로 관련 알림을 읽음 처리한다.
- **Community·Message → Notification**
  - 알림을 일으키는 쪽은 자기 outbox로 이벤트를 내보낸다. Notification Worker가 그것을 소비한다(ADR §1.3).
  - `notification_items` table을 직접 쓰지 않는다.
  - 지금은 4곳이 직접 다루고 있어 U5에서 정리한다.
- **Presence → Gateway**: Presence 변화는 Gateway 이벤트로 전달된다.

## 공통 식별자

| 이름 | 원본 context | 뜻 |
| --- | --- | --- |
| `UserId` | Identity | 계정 하나 |
| `GuildId` | Community | Guild 하나 |
| `ChannelId` | Community | Guild 채널 하나 |
| DirectMessageChannel id | Community | 1:1 대화 하나(코드의 `conversationId`) |
| GroupDm id | Community | 그룹 대화 하나 |
| `MessageId` | Message | 메시지 하나 |

## 전체에 걸친 이름 결정 (요약)

- 서버의 공식 이름은 **Guild**다. 코드·API·DB에서 `server`, `workspace`, `community`(서비스 이름 말고)는 쓰지 않는다. 화면 문구만 "서버"로 번역한다.
- 계정은 **User**, Guild 소속은 **GuildMember**다. 메서드 인자는 `userId`로 쓰고 `memberId`는 쓰지 않는다.
- 행위자는 역할별 이름을 쓴다. `sender`, `requester`, `memberId`는 버린다.
  - **Author**: 메시지 작성. Message의 MessageAuthor다.
  - **Owner**: Guild·Thread·GroupDm 소유
  - **Creator**: Invite·Webhook·GuildEvent를 만든 사람
  - **Reporter**: 신고한 사람
  - **Actor**: 감사 기록·요청을 수행한 사람
  - 예외: FriendRequest는 Requester와 Addressee라는 역할 이름을 쓴다.
- `Session`과 `Sequence`는 단독으로 쓰지 않는다. 각 context 용어집에 정한 이름(`RefreshSession`, `GatewaySession`, `StageSession`, `ChannelSequence`, `UserEventSequence` 등)을 쓴다.
