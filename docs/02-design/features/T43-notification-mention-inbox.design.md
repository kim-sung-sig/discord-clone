# T43 Notification & Mention Inbox Design

작성일: 2026-05-18  
PDCA Phase: Design  
Slice: T43 Notification & Mention Inbox

## Architecture Decision

Start with a backend domain module, `backend:modules:notification`, that owns notification item creation and read-state mutation. Message/gateway/frontend integrations can call this boundary later without duplicating unread logic.

The first implementation is in-memory and deterministic. Persistence and REST endpoints are follow-up work.

## Component Boundaries

| Component | Responsibility |
| --- | --- |
| `NotificationInboxService` | create inbox items, list inbox, count unread, mark read |
| `NotificationItem` | immutable inbox item |
| `NotificationKind` | `MENTION`, `DM`, `SERVER` category |
| `NotificationPreferences` | category enable/disable skeleton |
| `NotificationCommand` | sanitized event input from message/DM/server domains |

## Ingestion Contract

Mention ingestion receives:

- guild id,
- channel id,
- message id,
- message sequence,
- author id,
- mentioned user ids,
- visible recipient ids,
- summary.

Rules:

- Author does not receive their own mention notification.
- Mentioned user must also be in `visibleRecipientIds`.
- Mention preference must be enabled.
- Duplicate command for the same user/message/kind is idempotent.

DM/server events use the same item model but different `NotificationKind`.

## Read-state Integration

`markChannelRead(userId, channelId, sequence)` marks unread items read when:

- item belongs to `userId`,
- item is in `channelId`,
- item sequence is less than or equal to the read sequence.

This matches the existing presence read marker sequence model while avoiding a direct dependency from notification to presence in the first slice.

## Payload Safety

Notification items store safe summary text and opaque ids only:

- no access tokens,
- no LiveKit/media tokens,
- no signed URLs,
- no raw object keys,
- no full message payload if a short summary is enough.

## QA Strategy

- Unit test mention inbox creation for visible recipient.
- Unit test hidden-channel mentioned user filtering.
- Unit test category preferences suppress new notifications.
- Unit test DM/server unread count.
- Unit test read marker mutation clears unread count up to sequence.
- Unit test deterministic ordering: newest first by sequence/created time.

## Risks

- In-memory items are lost on restart until persistence is added.
- REST/frontend integration is not in this first slice.
- Mention extraction remains owned by message domain; notification receives already parsed mentioned ids.
- Per-guild/channel notification preference depth is deferred.
