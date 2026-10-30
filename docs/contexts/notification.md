# Notification

User마다 모아 보여 주는 알림함을 소유하는 context다. ADR(T171-C0 §1.3, §3.1)에 따라 Notification Worker는 다른 context가 Kafka로 내보낸 이벤트를 소비해 알림 항목을 만든다. 같은 이벤트가 두 번 와도 inbox 중복 제거로 한 번만 반영한다.

## Language

**NotificationItem**:
User 한 명의 알림함에 들어가는 항목 하나다. 종류(**NotificationKind**), 요약, 원본 위치, 읽음 여부를 가진다.
_Avoid_: alert(보안 경보 SecurityAlert와 헷갈림), inbox row

**NotificationKind**:
알림의 종류다: `MENTION`(멘션), `DIRECT_MESSAGE`(DM), `GUILD_EVENT`(Guild 행사).
_Avoid_: `DM`, `SERVER`(지금 코드의 값이다. U5에서 바꾼다)

**NotificationPreference**:
User가 종류별로, 또는 Guild·채널별로 알림을 받을지 정한 설정이다.

## Relationships

- **NotificationItem**은 User 한 명에게 속한다.
- 알림을 일으키는 쪽(Message, Community)은 자기 outbox로 이벤트를 내보낸다. Notification Worker가 그것을 소비해 **NotificationItem**을 만든다. 다른 context가 `notification_items` table을 직접 쓰지 않는다.
- 채널을 읽어 **ReadMarker**가 앞으로 가면 관련 알림을 읽음으로 바꾼다. 이것도 Message가 내보낸 읽음 위치 변경 이벤트를 소비해 처리한다.

## Example dialogue

> **Dev:** "DM을 보내면 social 쪽에서 notification_items에 바로 넣고 있어요."
> **Domain expert:** "알림 항목은 Notification이 만듭니다. DM 발행은 이벤트로 알리고, 차단, 설정, 중복 처리는 Notification 한 곳에서 판단해야 합니다."

## Flagged ambiguities

- 지금은 4곳이 `notification_items`를 직접 다룬다. 종류 문자열(`'DM'`, `'SERVER'`, `'MENTION'`)도 여러 곳에 반복된다(U5에서 이벤트 소비로 바꾼다).
  - social: DM 알림 INSERT(`JdbcDirectMessageService`)
  - event: Guild 행사 알림 INSERT(`JdbcServerEventService`)
  - presence: 읽음 처리 UPDATE(`JdbcChannelReadService`, ReadMarker와 같은 트랜잭션)
  - notification: 본래 저장소(`JdbcNotificationInboxService`)
