# Community

Guild와 그 안의 구조(채널, 멤버, 역할, 권한)를 소유하는 context다. 권한의 원본이며, 다른 context에는 `AuthorizationProjection`으로 권한을 내보낸다. 1:1·그룹 대화(DM 채널), 친구·차단, 초대, 스레드, 모더레이션도 이 context에 속한다.

## Language

### Guild와 구조

**Guild**:
사람들이 모이는 공간 하나다. **Owner** 한 명, **GuildMember**, **Role**, **Channel**을 가진다. 권한 판정의 단위다.
_Avoid_: server, workspace, community(코드·API·DB에서). 화면 문구만 "서버"로 번역한다.

**Owner**:
Guild를 소유한 User다. 모든 권한을 가진 것으로 판정한다.
_Avoid_: admin, creator(Guild에 대해서)

**GuildMember**:
User 한 명이 Guild 하나에 속해 있다는 사실과, 그가 가진 역할 목록이다. `UserId`로 식별한다.
_Avoid_: member id, participant

**Channel**:
Guild 안의 대화·음성 공간이다. **ChannelType**과 권한 덮어쓰기를 가진다. 메시지 순서의 단위다.
_Avoid_: chat room, room(음성 연동 이름 말고는)

**ChannelType**:
채널의 종류다: `GUILD_TEXT`, `GUILD_VOICE`, `GUILD_CATEGORY`, `GUILD_FORUM`.
_Avoid_: 문자열 종류 값을 직접 비교하는 것

### 권한

**Permission**:
허용 여부를 나타내는 권한 하나다(예: `VIEW_CHANNEL`, `SEND_MESSAGES`, `CONNECT`). `ADMINISTRATOR`는 모든 검사를 통과시킨다.

**Role**:
Guild 안에서 이름이 붙은 권한 묶음이다. `@everyone` 역할은 Guild를 만들 때 함께 생긴다.
_Avoid_: group, GlobalRole(플랫폼 권한은 Identity)

**PermissionOverwrite**:
한 채널에서 한 역할의 권한을 더하거나 빼는 규칙이다.

**EffectivePermission**:
`@everyone`, 그 사람의 역할, 채널 덮어쓰기를 차례로 적용한 최종 권한이다.
_Avoid_: 이 계산을 여러 곳에서 따로 하는 것(지금 3벌이다)

**AuthorizationProjection**:
Community가 내보낸 권한 변경 이벤트를 받은 서비스가 자기 DB에 보관하는, 버전 붙은 EffectivePermission 사본이다. 오래됐거나 없으면 거부한다.
_Avoid_: permission cache

### 초대와 대화

**Invite**:
Guild에 들어오게 하는 코드다. 최대 사용 횟수, 만료 시각, 함께 줄 역할을 가지며 취소할 수 있다.
_Avoid_: invitation link(화면 문구 말고는)

**Creator**:
Invite, 웹훅, **GuildEvent**를 만든 User다. 만든 사람일 뿐 소유자는 아니다.
_Avoid_: owner, inviter, sender

**Thread**:
채널 아래에서 갈라진 대화다. 일정 시간 활동이 없으면 보관된다. 포럼 채널의 글은 **ForumPost**라는 Thread다.

**DirectMessageChannel**:
Guild 밖의 1:1 대화 채널이다. 두 User를 가진다.
_Avoid_: DM(코드 이름으로), conversation, direct message(채널을 뜻할 때)

**GroupDm**:
Guild 밖의 여러 사람 대화 채널이다. **Owner**와 참여자 User 목록을 가진다.
_Avoid_: group conversation, group chat

**FriendRequest**:
두 User 사이의 친구 신청이다. 상태는 `PENDING`, `ACCEPTED`, `DECLINED` 중 하나다.

**Block**:
한 User가 다른 User를 차단한 관계다. DM과 알림이 막힌다.

**GuildEvent**:
Guild 안에서 일정이 잡힌 행사다. 관심을 표시한 User 목록을 가진다.
_Avoid_: ServerEvent, server event(지금 코드 이름은 `ServerEvent`이며 U3에서 바꾼다)

### 모더레이션

**MessageReport**:
User가 메시지 하나를 신고한 기록이다. `OPEN`에서 `RESOLVED` 또는 `DISMISSED`로 바뀐다.
_Avoid_: complaint, pending report(`OPEN`이 공식 상태 이름이다)

**Reporter**:
MessageReport를 만든 User다.
_Avoid_: author(신고된 메시지의 작성자와 헷갈림)

**AuditLogEntry**:
권한이 필요한 작업이 일어난 사실을 남긴, 추가만 하는 기록이다. **Actor**, 작업 종류, 대상을 가진다.

**Actor**:
요청이나 감사 대상 작업을 실제로 수행한 User다.
_Avoid_: requester, requesterId(지금 controller 인자 이름이며 U3에서 바꾼다)

**AutoModRule**:
Guild가 정한 자동 차단 규칙이다(금지어, 스팸).

**SecurityAlert**:
보안상 주의가 필요한 사건 기록이다. 종류와 심각도를 가진다.
_Avoid_: 종류·심각도를 문자열로 쓰는 것(enum으로 바꾼다)

**Onboarding**:
새 GuildMember에게 질문하고, 답에 따라 역할을 주는 절차다.

## Relationships

- **Guild** 하나는 **Owner** 한 명, **GuildMember** 여러 명, **Role** 여러 개, **Channel** 여러 개를 가진다.
- **GuildMember**는 **Role**을 0개 이상 가진다.
- **Channel**은 **PermissionOverwrite**를 0개 이상 가진다.
- **Invite**는 **Guild** 하나에 속하고, 받아들이면 **GuildMember**를 만든다. 수락은 하나의 트랜잭션이다.
- **Thread**는 **Channel** 하나에 속한다.
- **DirectMessageChannel**·**GroupDm**은 Guild에 속하지 않는다.

## Example dialogue

> **Dev:** "초대를 받아들이면 멤버를 추가하고 역할을 하나씩 따로 부여하면 되죠?"
> **Domain expert:** "**Invite** 수락은 한 번에 일어나야 합니다. 사용 횟수 차감, **GuildMember** 생성, 역할 부여가 하나의 트랜잭션이에요. 중간에 실패하면 사용 횟수도 줄면 안 됩니다."
> **Dev:** "그 사람 id는 memberId라고 부르면 되나요?"
> **Domain expert:** "사람은 항상 `UserId`입니다. **GuildMember**는 그 User가 Guild에 속한다는 사실이지, 다른 id가 아니에요."

## Flagged ambiguities

- `server`, `workspace`를 Guild와 같은 뜻으로 써 왔다. 코드에서는 **Guild**만 쓴다. `ServerEvent`는 **GuildEvent**로, 알림 종류 `SERVER`는 `GUILD_EVENT`로 바꾼다(U3, U5).
- `memberId`가 실제로는 `UserId`다. 인자 이름을 `userId`로 바꾼다.
- `Invite.acceptedMemberIds`는 이미 멤버가 된 사람이 아니라 초대를 받아들인 User 목록이다. `acceptedUserIds`로 바꾼다.
