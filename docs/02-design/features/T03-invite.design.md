# T03 Invite Design

작성일: 2026-05-13  
PDCA Phase: Design  
Slice: T03 invite

## Backend Design

### Module

- Add `backend/modules/invite`.
- `Invite` stores code, guild id, channel id, creator id, max age, max uses, temporary flag, role grant ids, createdAt, deletedAt, and accepted member ids.
- `InMemoryInviteService` owns invite lifecycle and uses synchronized methods for max-use race safety.

### Boot API

| Method | Path | Auth |
| --- | --- | --- |
| `POST` | `/api/guilds/{guildId}/invites` | owner or `MANAGE_CHANNELS` |
| `GET` | `/api/invites/{code}` | public preview |
| `POST` | `/api/invites/{code}/accept` | bearer required |
| `DELETE` | `/api/invites/{code}` | owner or `MANAGE_CHANNELS` |

### Accept Flow

- Reject deleted invite.
- Reject expired invite.
- Reject max uses for distinct member accepts.
- Same member accept is idempotent and does not consume an additional use.
- Add member to guild and assign configured role grants.

## Frontend Design

- Add `InviteModal.vue`.
- Static seed state is acceptable for T03; API wiring follows later.
- Modal renders guild preview, expiry/max-use metadata, role grant skeleton, and accept CTA.
- Mount modal in shell workspace without route changes.

