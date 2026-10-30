# T225 유비쿼터스 언어 이름 결정 ADR

- 상태: 승인 (2026-10-30, 사용자 결정)
- 관련: `CONTEXT-MAP.md`, `docs/contexts/*.md`, `docs/02-design/features/T171-C0-msa-target-state.adr.md`

## 배경

2026-10-30 아키텍처 조사에서 같은 개념을 여러 이름으로 부르는 문제가 드러났다. MSA로 나누면 이름이 곧 서비스 사이의 계약이 된다. 그래서 서비스를 나누기 전에 이름을 고정한다.

| 개념 | 섞여 쓰인 이름 |
| --- | --- |
| 서버 | guild, server, workspace, community |
| 작성자 | author, sender, creator, owner, reporter, actor, requester |
| 사람 | member, user |
| session | 4가지 뜻 |
| sequence | 5가지 뜻 |

## 결정

1. **서버의 공식 이름은 `Guild`다.**
   - 이미 API(`/api/guilds`), 타입, DB(`guild_id`)가 이 이름이라 바꿀 범위가 가장 작다.
   - 코드, API, DB, 이벤트에서는 `server`와 `workspace`를 쓰지 않는다. `community`는 서비스 이름으로만 쓴다.
   - 사용자에게 보이는 문구만 "서버"로 번역한다.
2. **계정은 `User`, Guild 소속은 `GuildMember`다.**
   - 사람은 언제나 `UserId`로 식별한다. 메서드 인자에서는 `memberId`를 쓰지 않는다.
3. **Guild 밖의 대화는 `DirectMessageChannel`(1:1)과 `GroupDm`(여럿)이다.**
   - 둘 다 Message의 수신처(`MessageTarget`)이며, 채널과 같은 방식으로 메시지를 받는다.
4. **행위자는 역할별 이름을 쓰고, 뜻은 용어집에 고정한다.**

   | 이름 | 뜻 |
   | --- | --- |
   | `Author` | 메시지 작성 |
   | `Owner` | Guild, 스레드, 그룹 DM 소유 |
   | `Creator` | 초대, 웹훅, Guild 이벤트를 만든 사람 |
   | `Reporter` | 신고한 사람 |
   | `Actor` | 요청이나 감사 대상 작업을 수행한 사람 |

   같은 뜻의 다른 이름(`sender`, `requester`, `memberId`)은 버린다.
5. **`Session`과 `Sequence`는 단독으로 쓰지 않는다.**
   - Session: `RefreshSession`(Identity), `GatewaySession`(Gateway), `StageSession`(Media)
   - Sequence: `ChannelSequence`(Message), `UserEventSequence`(Gateway), `ReadMarker`(Message)
   - 이 항목은 사용자가 따로 고르지 않았다. 앞의 결정 원칙을 따라 코디네이터가 정했다.
6. **용어집은 context별로 둔다.**
   - 진입점은 루트 `CONTEXT-MAP.md`이고, 내용은 `docs/contexts/<context>.md`에 있다.
   - 기존 루트 `CONTEXT.md`의 Message 용어는 `docs/contexts/message.md`로 옮긴다.

## 고려한 다른 안

- **`Server`**: 사용자에게 보이는 말과 같다는 장점이 있다. 하지만 API, DB 컬럼, 모든 타입 이름을 바꿔야 한다.
- **`Community`**: ADR의 서비스 이름과 같아서, "서비스"와 "그 안의 한 서버"를 구분할 수 없다.
- **모든 행위자를 `Author`로**: "신고한 사람"과 "신고된 메시지의 작성자"가 같은 이름이 된다.

## 결과

- 각 용어집의 "Flagged ambiguities"에 적은 이름 변경은 해당 context를 분리하는 작업에서 함께 처리한다(T225 계획 U2~U5). 이 ADR은 이름만 정하고 코드는 바꾸지 않는다.
- 이후 작업 검토에서는 "용어집에 없는 새 이름"을 명세 위반으로 본다.
