# T04 Message Core Design

작성일: 2026-05-13  
PDCA Phase: Design  
Slice: T04 message core

## Backend Design

### Module

- Add `backend/modules/message`.
- `Message` stores id, guild id, channel id, author id, content, mentions, pinned flag, deleted flag, edit history, createdAt, updatedAt.
- `InMemoryMessageService` owns ordering, cursor pagination, mutation lifecycle, mention extraction, and search.
- Cursor is a createdAt/id tuple encoded as an opaque string by the service.

### Guild Permission Boundary

- Extend `InMemoryGuildService` with channel-level checks:
  - `canViewChannel(guildId, channelId, memberId)`
  - `canSendMessages(guildId, channelId, memberId)`
  - `canManageMessages(guildId, channelId, memberId)`
- `ADMINISTRATOR` and guild owner pass every check.
- Read/list/search requires `VIEW_CHANNEL`.
- Create requires `VIEW_CHANNEL` and `SEND_MESSAGES`.
- Delete/pin others requires `MANAGE_MESSAGES`; authors can edit/delete their own non-deleted messages.

### Boot API

| Method | Path | Auth |
| --- | --- | --- |
| `POST` | `/api/channels/{channelId}/messages` | bearer, `SEND_MESSAGES` |
| `GET` | `/api/channels/{channelId}/messages?before=&limit=` | bearer, `VIEW_CHANNEL` |
| `PATCH` | `/api/channels/{channelId}/messages/{messageId}` | author |
| `DELETE` | `/api/channels/{channelId}/messages/{messageId}` | author or `MANAGE_MESSAGES` |
| `PUT` | `/api/channels/{channelId}/messages/{messageId}/pin` | `MANAGE_MESSAGES` |
| `DELETE` | `/api/channels/{channelId}/messages/{messageId}/pin` | `MANAGE_MESSAGES` |
| `GET` | `/api/channels/{channelId}/messages/search?q=` | bearer, `VIEW_CHANNEL` |

## Frontend Design

- Extend `ShellMessage` with id, createdAt, edited, pinned, deleted, mentions.
- Add composer state/action to `shell.ts` with empty-content guard.
- `ChatViewport.vue` renders message metadata, tombstone, pinned label, mention chips, and composer.
- Static seed remains acceptable for T04; API wiring follows after gateway/API client slice.

## QA Design

- Backend service tests cover pagination, mention extraction, edit history, tombstone delete, pin/search.
- MockMvc tests cover authz read/write, author mutation, moderator mutation.
- Component tests cover chat metadata and composer action.
- Playwright covers channel select and send-message happy path.
