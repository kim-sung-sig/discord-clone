# T02C Guild Authorization Boundary Design

작성일: 2026-05-13  
PDCA Phase: Design  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-C authorization boundary

## Backend Design

### Auth Resolver

- Add `AuthenticatedUserResolver` in `backend/boot/src/main/java/com/example/discord/auth`.
- It accepts `Authorization` header and returns authenticated `UUID`.
- It reuses `AccessTokenService` and `InMemoryAuthStore` to reject missing, revoked, invalid, or unknown tokens.

### Guild Authorization

- Extend `Permission` with `MANAGE_ROLES`.
- Add `InMemoryGuildService.canManageRoles(guildId, requesterId)`.
- Add `InMemoryGuildService.canManageChannels(guildId, requesterId)`.
- Owner always passes.
- Non-owner passes when aggregate guild role permissions allow the required permission or `ADMINISTRATOR`.

### Controller Rules

| Endpoint | Required Authz |
| --- | --- |
| `POST /api/guilds` | authenticated user; owner is authenticated user |
| `POST /api/guilds/{guildId}/channels` | owner or `MANAGE_CHANNELS` |
| `POST /api/guilds/{guildId}/roles` | owner or `MANAGE_ROLES` |
| `PUT /api/guilds/{guildId}/roles/{roleId}/permissions` | owner or `MANAGE_ROLES` |
| `PUT /api/guilds/{guildId}/members/{memberId}` | owner or `MANAGE_ROLES` |
| `PUT /api/guilds/{guildId}/members/{memberId}/roles/{roleId}` | owner or `MANAGE_ROLES` |
| `PUT /api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}` | owner or `MANAGE_CHANNELS` |

## Test Design

- MockMvc signs up users and uses returned access tokens.
- Tests verify owner success, missing token unauthorized, non-owner forbidden, permission-granted delegate success.
- Existing visible channel and invalid payload tests are updated to pass auth where mutation is required.
