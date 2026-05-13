# T02 Guild/Channel/Permission Design

작성일: 2026-05-13  
PDCA Phase: Design  
Slice: T02-A foundation

## Architecture

Add two backend modules:

- `backend/modules/guild`: guild, member, role, channel models and in-memory application service for this slice
- `backend/modules/channel`: channel type and channel permission overwrite value objects

Extend existing `permission` module:

- add more Discord-like permissions
- add `PermissionOverwrite`
- add `EffectivePermissionCalculator`

Boot module adds a thin REST controller:

- `POST /api/guilds`
- `POST /api/guilds/{guildId}/channels`
- `GET /api/guilds/{guildId}/channels/visible?memberId=...`

## Domain Model

```text
Guild
GuildMember
Role
Channel
ChannelType
PermissionOverwrite
EffectivePermissionCalculator
```

## Permission Rules

1. Start from `@everyone` role permissions.
2. OR all member role permissions.
3. If `ADMINISTRATOR` is present, allow every permission.
4. Apply channel overwrite deny bits.
5. Apply channel overwrite allow bits.
6. `VIEW_CHANNEL` controls channel visibility.

For this slice, overwrites are role-level only. Member-specific overwrites are later.

## API Design

### Create guild

```http
POST /api/guilds
{
  "name": "Discord Clone",
  "ownerId": "uuid"
}
```

Returns:

```json
{
  "id": "uuid",
  "name": "Discord Clone",
  "ownerId": "uuid"
}
```

### Create channel

```http
POST /api/guilds/{guildId}/channels
{
  "name": "general",
  "type": "GUILD_TEXT",
  "parentId": null
}
```

### Visible channels

```http
GET /api/guilds/{guildId}/channels/visible?memberId=uuid
```

Returns channels where effective permissions include `VIEW_CHANNEL`.

## Frontend Design

Keep frontend API-free for T02-A. Update shell seed to carry:

- guild id/name
- channel groups with type
- active channel id
- visible channel rendering

Tests continue to validate shell landmarks and channel names.

## Review Gates

- Backend review: permission correctness, API behavior, in-memory consistency
- Frontend review: routing stability, shell rendering, test isolation

