# Presence

User가 지금 접속해 있는지, 무엇을 하고 있는지처럼 곧 사라지는 상태를 소유하는 context다. 기록으로 남기지 않고 TTL로 관리한다.

## Language

**UserPresence**:
User 한 명의 현재 상태다. **PresenceStatus** 하나와 마지막으로 활동한 시각을 가진다.
_Avoid_: status(단독), online flag

**PresenceStatus**:
접속 상태의 종류다: `ONLINE`, `IDLE`, `DO_NOT_DISTURB`, `OFFLINE`.
_Avoid_: `dnd` 같은 줄임말을 코드 값으로 쓰는 것

**TypingIndicator**:
User가 채널 하나에서 입력 중이라는 짧은 신호다. 몇 초 뒤 저절로 사라진다.
_Avoid_: typing event를 기록으로 저장하는 것

## Relationships

- **UserPresence**는 User마다 하나다.
- **TypingIndicator**는 (User, Channel)마다 최대 하나이며 TTL로 사라진다.
- 변화는 Gateway 이벤트로 전달된다.

## Example dialogue

> **Dev:** "읽음 위치도 Presence에 두면 되나요? 지금 presence 패키지에 있던데요."
> **Domain expert:** "읽음 위치는 메시지 번호에 묶인 지속 상태라 Message의 **ReadMarker**입니다. Presence는 TTL로 사라지는 상태만 다뤄요."

## Flagged ambiguities

- 읽음 위치 관련 코드가 presence에 있다: `ReadMarker` record(`backend/modules/presence`)와 `JdbcChannelReadService`(boot `presence` 패키지). 용어상 Message의 **ReadMarker**이므로 Message context로 옮긴다(U4).
