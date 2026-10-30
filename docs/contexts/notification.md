# Notification

User마다 모아 보여 주는 알림함을 소유하는 context다. 다른 context에서 일어난 일을 이벤트나 기록 port로 받아 알림 항목을 만든다.

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
- 알림을 만드는 쪽(Message, Community)은 Notification의 기록 port를 부른다. `notification_items` table을 직접 쓰지 않는다.

## Example dialogue

> **Dev:** "DM을 보내면 social 쪽에서 notification_items에 바로 넣고 있어요."
> **Domain expert:** "알림 항목은 Notification이 만듭니다. DM 발행은 이벤트나 기록 port로 알리고, 차단, 설정, 중복 처리는 Notification 한 곳에서 판단해야 합니다."

## Flagged ambiguities

- 지금은 4곳이 `notification_items`를 직접 쓴다: social, event, presence, notification. 종류 문자열(`'DM'`, `'SERVER'`, `'MENTION'`)도 여러 곳에 반복된다. U5에서 기록 port 하나로 모은다.
