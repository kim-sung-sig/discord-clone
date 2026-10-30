# Presence

User가 지금 접속해 있는지, 무엇을 하고 있는지 같은, 곧 사라지는 상태를 소유하는 context다. 기록으로 남기지 않고 TTL로 관리한다.

## Language

**Presence**:
User 한 명의 현재 상태다: online, idle, dnd, offline 중 하나와, 마지막으로 활동한 시각을 가진다.
_Avoid_: status(단독), online flag

**TypingIndicator**:
User가 채널 하나에서 입력 중이라는 짧은 신호다. 몇 초 뒤 저절로 사라진다.
_Avoid_: typing event를 기록으로 저장하는 것

## Relationships

- **Presence**는 User마다 하나다.
- **TypingIndicator**는 (User, Channel)마다 최대 하나이며 TTL로 사라진다.
- 변화는 Gateway 이벤트로 전달된다.

## Example dialogue

> **Dev:** "읽음 위치도 Presence에 두면 되나요? 지금 presence 패키지에 있던데요."
> **Domain expert:** "읽음 위치는 메시지 번호에 묶인 지속 상태라 Message의 **ReadMarker**입니다. Presence는 TTL로 사라지는 상태만 다뤄요."

## Flagged ambiguities

- `JdbcChannelReadService`(읽음 위치)가 presence 패키지에 있다. 용어상 Message의 **ReadMarker**이므로, Message context로 옮긴다(U4).
